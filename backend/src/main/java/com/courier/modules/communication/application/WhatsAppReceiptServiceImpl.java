package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationStatus;
import com.courier.modules.communication.domain.DailyReportLog;
import com.courier.modules.communication.domain.DailyReportLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    @Override
    @Transactional
    public int apply(List<Receipt> receipts) {
        int matched = 0;
        for (Receipt receipt : receipts) {
            if (receipt == null || receipt.messageId() == null || receipt.messageId().isBlank()) {
                continue;
            }
            String status = receipt.status() == null ? "" : receipt.status().toLowerCase(Locale.ROOT);
            Instant at = receipt.at() == null ? Instant.now() : receipt.at();

            Optional<CommunicationLog> shipmentMessage = logRepository.findFirstByProviderMessageId(receipt.messageId());
            if (shipmentMessage.isPresent()) {
                matched++;
                applyToLog(shipmentMessage.get(), status, at, receipt.error());
                continue;
            }
            Optional<DailyReportLog> dailyMessage = dailyReportLogRepository.findFirstByProviderMessageId(receipt.messageId());
            if (dailyMessage.isPresent()) {
                matched++;
                applyToDaily(dailyMessage.get(), status, at, receipt.error());
            }
        }
        return matched;
    }

    private void applyToLog(CommunicationLog row, String status, Instant at, String error) {
        switch (status) {
            case "delivered" -> {
                if (row.getDeliveredAt() == null) {
                    row.setDeliveredAt(at);
                }
                if (row.getStatus() == CommunicationStatus.SENT) {
                    row.setStatus(CommunicationStatus.DELIVERED);
                }
            }
            case "read" -> {
                if (row.getDeliveredAt() == null) {
                    row.setDeliveredAt(at);
                }
                if (row.getReadAt() == null) {
                    row.setReadAt(at);
                }
                if (row.getStatus() == CommunicationStatus.SENT) {
                    row.setStatus(CommunicationStatus.DELIVERED);
                }
            }
            case "failed" -> {
                if (row.getStatus() == CommunicationStatus.DELIVERED || row.getDeliveredAt() != null) {
                    return;
                }
                row.setStatus(CommunicationStatus.FAILED);
                row.setErrorMessage(truncate("Meta could not deliver it: " + (error == null ? "no reason given" : error)));
                row.setNextRetryAt(null);
            }
            default -> {
                return; // "sent" and anything new Meta invents: nothing to record
            }
        }
        logRepository.save(row);
    }

    private void applyToDaily(DailyReportLog row, String status, Instant at, String error) {
        switch (status) {
            case "delivered" -> {
                if (row.getDeliveredAt() == null) {
                    row.setDeliveredAt(at);
                }
                if (row.getStatus() == CommunicationStatus.SENT) {
                    row.setStatus(CommunicationStatus.DELIVERED);
                }
            }
            case "read" -> {
                if (row.getDeliveredAt() == null) {
                    row.setDeliveredAt(at);
                }
                if (row.getReadAt() == null) {
                    row.setReadAt(at);
                }
                if (row.getStatus() == CommunicationStatus.SENT) {
                    row.setStatus(CommunicationStatus.DELIVERED);
                }
            }
            case "failed" -> {
                if (row.getStatus() == CommunicationStatus.DELIVERED || row.getDeliveredAt() != null) {
                    return;
                }
                // Not retried automatically: FAILED with attempts left WOULD be re-sent by the daily job,
                // which is exactly right for a message Meta could not deliver — but the attempt budget is
                // what bounds it, so just record the reason.
                row.setStatus(CommunicationStatus.FAILED);
                row.setErrorMessage(truncate("Meta could not deliver it: " + (error == null ? "no reason given" : error)));
            }
            default -> {
                return;
            }
        }
        dailyReportLogRepository.save(row);
    }

    private static String truncate(String value) {
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
