package com.courier.modules.branchreport.application;

import com.courier.modules.branchreport.domain.BranchStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds branch statements for the scheduler. No authorization: callers run on a scheduler
 * thread, pass {@code companyId} explicitly and must already have the company bound in
 * {@code CompanyContext}. Dates are inclusive IST dates.
 */
public interface BranchStatementService {

    /** One statement per active branch of the company (one shipment-audit query in total). */
    List<BranchStatement> buildAll(UUID companyId, LocalDate from, LocalDate to);

    /** The branch's statement; empty if the branch does not exist in this company. */
    Optional<BranchStatement> build(UUID companyId, UUID branchId, LocalDate from, LocalDate to);
}
