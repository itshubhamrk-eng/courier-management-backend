package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * "Communication is event driven" / "Use existing Kafka/event infrastructure if available.
 * Otherwise create event abstraction ready for Kafka" — this codebase has no message broker
 * (confirmed: no Kafka dependency anywhere in {@code pom.xml}), so the ready-for-Kafka
 * abstraction is a durable DB outbox (see {@code CommunicationLog}'s own doc) plus this
 * sweep, the exact shape {@code TicketSlaSweepJob}/{@code ShipmentSlaSweepJob}/{@code
 * FollowUpSweepJob} already established for "the actual work happens outside the triggering
 * request, on a schedule, cross-tenant". Swapping this for a real Kafka consumer later is a
 * drop-in replacement for this one class — {@code ShipmentCommunicationListener} already
 * only ever touches {@code CommunicationLog}, never a provider, so nothing upstream changes.
 *
 * <p>Cross-tenant on purpose, no {@code CompanyContext} bound at the top level — see
 * {@code CommunicationLogRepository.findDueForDispatch}'s own doc. Each row is then
 * processed inside its own {@code CompanyContext.runAs} by {@code CommunicationSendService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunicationDispatchJob {

    private final CommunicationLogRepository logRepository;
    private final CommunicationSendService sendService;
    private final CommunicationRetryProperties retryProperties;

    @Scheduled(fixedDelayString = "${app.communication.dispatch.fixed-delay-ms:30000}")
    public void run() {
        List<CommunicationLog> due = logRepository.findDueForDispatch(
                retryProperties.getMaxAttempts(), Instant.now());
        if (due.isEmpty()) {
            return;
        }
        log.info("Communication dispatch sweep: {} row(s) due", due.size());
        for (CommunicationLog logRow : due) {
            try {
                sendService.processOne(logRow.getId(), logRow.getCompanyId());
            } catch (RuntimeException e) {
                log.error("Communication dispatch sweep: unexpected failure on log {} (shipment {})",
                        logRow.getId(), logRow.getShipmentId(), e);
            }
        }
    }
}
