package com.courier.modules.followup.application;

import com.courier.modules.followup.domain.FollowUp;
import com.courier.modules.followup.domain.FollowUpRepository;
import com.courier.modules.support.application.NotificationService;
import com.courier.modules.support.domain.NotificationType;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Overdue/due-today detection has no single triggering user action — it fires from here
 * on a plain hourly poll instead, the same shape {@code TicketSlaSweepJob} uses for its
 * own two "nobody did anything, time just passed" notifications.
 *
 * <p>Runs with no {@code CompanyContext} bound (a scheduler thread starts clean —
 * {@code CompanyContext} is a plain, not inheritable, {@code ThreadLocal}), so
 * {@link FollowUpRepository#findAllOpenPendingSweep} runs genuinely cross-company; each
 * follow-up's own notify+save is wrapped in {@code CompanyContext.runAs} so the write
 * path sees the same binding a real request would.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpSweepJob {

    private final FollowUpRepository followUpRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "3600000", initialDelayString = "120000")
    public void sweep() {
        List<FollowUp> candidates = followUpRepository.findAllOpenPendingSweep();
        if (candidates.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int overdue = 0;
        int dueToday = 0;
        for (FollowUp followUp : candidates) {
            boolean isOverdue = followUp.getDueDate().isBefore(now);
            boolean isDueToday = !isOverdue
                    && followUp.getDueDate().atZone(ZoneOffset.UTC).toLocalDate().isEqual(today);

            boolean changed = false;
            if (isOverdue && !followUp.isOverdueNotified()) {
                fire(followUp, NotificationType.FOLLOWUP_OVERDUE, "This follow-up is now overdue.");
                followUp.setOverdueNotified(true);
                overdue++;
                changed = true;
            } else if (isDueToday && !followUp.isDueTodayNotified()) {
                fire(followUp, NotificationType.FOLLOWUP_DUE_TODAY, "This follow-up is due today.");
                followUp.setDueTodayNotified(true);
                dueToday++;
                changed = true;
            }
            if (changed) {
                CompanyContext.runAs(followUp.getCompanyId(), () -> followUpRepository.save(followUp));
            }
        }
        if (overdue > 0 || dueToday > 0) {
            log.info("Follow-up sweep: {} overdue, {} due-today notification(s) sent", overdue, dueToday);
        }
    }

    private void fire(FollowUp followUp, NotificationType type, String message) {
        CompanyContext.runAs(followUp.getCompanyId(), () -> notificationService
                .notifyFollowUp(followUp.getAssignedUserId(), type, followUp.getTitle(), message, followUp.getId()));
    }
}
