package com.courier.modules.communication.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The monthly branch statement over WhatsApp: one PDF per branch to every branch manager's phone,
 * using the company's {@code BRANCH_MONTHLY_REPORT} template. No {@code @PreAuthorize} here: the
 * scheduler has no authenticated caller, and the REST layer does its own role gate before calling
 * the manual methods.
 */
public interface BranchReportService {

    String SENT = "SENT";
    String NO_PHONE = "NO_PHONE";
    String ALREADY_SENT = "ALREADY_SENT";
    String TEMPLATE_NOT_APPROVED = "TEMPLATE_NOT_APPROVED";
    String FAILED = "FAILED";

    /** @param result one of the constants above */
    record SendOutcome(UUID branchId, String branchName, String result) {
    }

    /**
     * @param lastStatus the least-progressed status among the branch's recipients (FAILED, PENDING,
     *                   SENT, DELIVERED in that order), null when nothing was ever attempted
     * @param lastSentAt the latest send time across recipients, null if none went out
     */
    record BranchSendStatus(UUID branchId, String branchName, int managerPhoneCount, String lastStatus,
                            Instant lastSentAt) {
    }

    /** Scheduler path: every company with WhatsApp enabled. Never forces.
     *  @return how many sends were attempted this pass */
    int sendReportsFor(LocalDate from, LocalDate to);

    /** Manual path, current company from {@code CompanyContext}. */
    SendOutcome sendForBranch(UUID branchId, LocalDate from, LocalDate to, boolean force);

    /** Manual path, current company from {@code CompanyContext}. */
    List<SendOutcome> sendForAllBranches(LocalDate from, LocalDate to, boolean force);

    /** Per-branch send state for the period, current company. */
    List<BranchSendStatus> sendStatus(LocalDate from, LocalDate to);
}
