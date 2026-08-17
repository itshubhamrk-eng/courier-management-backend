package com.courier.modules.support.application;

import com.courier.modules.support.domain.NotificationType;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketRepository;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The two SLA notification types with no single triggering user action —
 * {@code SLA_APPROACHING}/{@code SLA_BREACHED} — fire from here instead, on a plain
 * poll rather than a per-request check, so they land even if nobody happens to be
 * looking at the ticket when the threshold crosses.
 *
 * <p>Runs with no {@code CompanyContext} bound (a scheduler thread starts clean, never
 * inherited from a request — {@code CompanyContext} is deliberately a plain, not
 * inheritable, {@code ThreadLocal}), so {@link TicketRepository#findAllOpenWithPendingSla}
 * runs genuinely cross-company with no rebind needed. Each ticket's own notify+save is
 * still wrapped in {@code CompanyContext.runAs} so the write path sees the same binding
 * a real request would.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketSlaSweepJob {

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "300000", initialDelayString = "60000")
    public void sweep() {
        List<Ticket> candidates = ticketRepository.findAllOpenWithPendingSla();
        if (candidates.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int warned = 0;
        int breached = 0;
        for (Ticket ticket : candidates) {
            String bucket = TicketServiceImpl.slaBucket(ticket, now);
            if ("BREACHED".equals(bucket) && !ticket.isSlaBreachNotified()) {
                fire(ticket, NotificationType.SLA_BREACHED, "SLA breached — past the resolution target.");
                ticket.setSlaBreachNotified(true);
                breached++;
            } else if ("WARNING".equals(bucket) && !ticket.isSlaWarningNotified()) {
                fire(ticket, NotificationType.SLA_APPROACHING, "SLA approaching — resolution target closing in.");
                ticket.setSlaWarningNotified(true);
                warned++;
            } else {
                continue;
            }
            CompanyContext.runAs(ticket.getCompanyId(), () -> ticketRepository.save(ticket));
        }
        if (warned > 0 || breached > 0) {
            log.info("SLA sweep: {} warning(s), {} breach notification(s) sent", warned, breached);
        }
    }

    @Transactional
    void fire(Ticket ticket, NotificationType type, String message) {
        UUID recipient = ticket.getAssigneeUserId() != null ? ticket.getAssigneeUserId() : ticket.getCreatedByUserId();
        CompanyContext.runAs(ticket.getCompanyId(), () ->
                notificationService.notify(recipient, type, ticket.getTicketNumber(), message, ticket.getId()));
    }
}
