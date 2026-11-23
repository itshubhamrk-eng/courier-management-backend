package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.BranchReportLogRepository;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.modules.communication.domain.DailyReportLogRepository;
import com.courier.modules.communication.domain.WhatsAppTestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Meta reports each message's journey after our send: sent -> delivered -> read, or failed. The
 * three states are ordered, and Meta does not promise to deliver them in order (a "read" can beat
 * its own "delivered"), so a receipt only ever moves a row forward:
 * <ul>
 *   <li>{@code sent} — we already know; ignored.</li>
 *   <li>{@code delivered} — SENT becomes DELIVERED; {@code deliveredAt} set once.</li>
 *   <li>{@code read} — implies delivered, so the row becomes DELIVERED too (if it was not yet) and
 *       {@code readAt} is set. There is no separate READ status; the dashboard counts {@code readAt}.</li>
 *   <li>{@code failed} — only for a message not yet delivered: becomes FAILED with Meta's reason, and
 *       {@code nextRetryAt} is cleared so the retry sweep does not blindly re-send it (an admin can
 *       still press Retry). A failure after delivery is nonsense and is ignored.</li>
 * </ul>
 * Replaying the same receipt is harmless — Meta retries until it gets a 200.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppReceiptServiceImpl implements WhatsAppReceiptService {

    private final CommunicationLogRepository logRepository;
    private final DailyReportLogRepository dailyReportLogRepository;
    private final WhatsAppTestLogRepository testLogRepository;
    private final BranchReportLogRepository branchReportLogRepository;

    /**
     * One ledger row seen through the few fields a receipt touches, so the state machine below is
     * written once for all four tables. {@code onFailed} is the one per-table extra (the shipment
     * log also clears its retry time).
     */
    private record Target(Supplier<CommunicationStatus> status, Consumer<CommunicationStatus> setStatus,
                          Supplier<Instant> deliveredAt, Consumer<Instant> setDeliveredAt,
                          Supplier<Instant> readAt, Consumer<Instant> setReadAt,
                          Consumer<String> setError, Runnable onFailed, Runnable save) {
    }

    private Optional<Target> findShipmentLog(String messageId) {
        return logRepository.findFirstByProviderMessageId(messageId).map(r -> new Target(
                r::getStatus, r::setStatus, r::getDeliveredAt, r::setDeliveredAt, r::getReadAt, r::setReadAt,
                r::setErrorMessage, () -> r.setNextRetryAt(null), () -> logRepository.save(r)));
    }

    private Optional<Target> findDaily(String messageId) {
        return dailyReportLogRepository.findFirstByProviderMessageId(messageId).map(r -> new Target(
                r::getStatus, r::setStatus, r::getDeliveredAt, r::setDeliveredAt, r::getReadAt, r::setReadAt,
                r::setErrorMessage, () -> { }, () -> dailyReportLogRepository.save(r)));
    }

    private Optional<Target> findTest(String messageId) {
        return testLogRepository.findFirstByProviderMessageId(messageId).map(r -> new Target(
                r::getStatus, r::setStatus, r::getDeliveredAt, r::setDeliveredAt, r::getReadAt, r::setReadAt,
                r::setErrorMessage, () -> { }, () -> testLogRepository.save(r)));
    }

    private Optional<Target> findBranchReport(String messageId) {
        return branchReportLogRepository.findFirstByProviderMessageId(messageId).map(r -> new Target(
                r::getStatus, r::setStatus, r::getDeliveredAt, r::setDeliveredAt, r::getReadAt, r::setReadAt,
                r::setErrorMessage, () -> { }, () -> branchReportLogRepository.save(r)));
    }

    @Override
    @Transactional
    public int apply(List<Receipt> receipts) {
        List<Function<String, Optional<Target>>> sources = List.of(
                this::findShipmentLog, this::findDaily, this::findTest, this::findBranchReport);
        int matched = 0;
        for (Receipt receipt : receipts) {
            if (receipt == null || receipt.messageId() == null || receipt.messageId().isBlank()) {
                continue;
            }
            String status = receipt.status() == null ? "" : receipt.status().toLowerCase(Locale.ROOT);
            Instant at = receipt.at() == null ? Instant.now() : receipt.at();

            for (var source : sources) {
                Optional<Target> target = source.apply(receipt.messageId());
                if (target.isPresent()) {
                    matched++;
                    applyReceipt(target.get(), status, at, receipt.error());
                    break;
                }
            }
        }
        return matched;
    }

    private void applyReceipt(Target row, String status, Instant at, String error) {
        switch (status) {
            case "delivered" -> {
                if (row.deliveredAt().get() == null) {
                    row.setDeliveredAt().accept(at);
                }
                if (row.status().get() == CommunicationStatus.SENT) {
                    row.setStatus().accept(CommunicationStatus.DELIVERED);
                }
            }
            case "read" -> {
                if (row.deliveredAt().get() == null) {
                    row.setDeliveredAt().accept(at);
                }
                if (row.readAt().get() == null) {
                    row.setReadAt().accept(at);
                }
                if (row.status().get() == CommunicationStatus.SENT) {
                    row.setStatus().accept(CommunicationStatus.DELIVERED);
                }
            }
            case "failed" -> {
                if (row.status().get() == CommunicationStatus.DELIVERED || row.deliveredAt().get() != null) {
                    return;
                }
                row.setStatus().accept(CommunicationStatus.FAILED);
                row.setError().accept(truncate("Meta could not deliver it: " + (error == null ? "no reason given" : error)));
                row.onFailed().run();
            }
            default -> {
                return; // "sent" and anything new Meta invents: nothing to record
            }
        }
        row.save().run();
    }

    private static String truncate(String value) {
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
