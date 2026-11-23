package com.courier.modules.communication.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the monthly branch report needs from the data-owning side: who to send to and the PDF to
 * send. Owned by {@code communication}, implemented by {@code branchreport}, same arrangement as
 * {@link DailyBusinessMetricsPort}. Called on a scheduler thread, so implementations take the
 * company id explicitly; the CALLER must already have {@code CompanyContext} bound.
 */
public interface BranchStatementPort {

    /** @param managerPhones digits only, at least 10 digits, de-duplicated; empty when the branch has no usable number */
    record BranchTarget(UUID branchId, String branchName, List<String> managerPhones) {
    }

    /** @param monthLabel what the template's {{month}} shows, e.g. "December 2026" */
    record GeneratedStatement(byte[] pdf, String fileName, String branchName, String monthLabel) {
    }

    /** Every active branch of the company with its manager phones. */
    List<BranchTarget> targets(UUID companyId, LocalDate from, LocalDate to);

    /** The branch's statement PDF; empty if the branch does not exist in this company. */
    Optional<GeneratedStatement> generate(UUID companyId, UUID branchId, LocalDate from, LocalDate to);
}
