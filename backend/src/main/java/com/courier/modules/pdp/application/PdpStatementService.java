package com.courier.modules.pdp.application;

import com.courier.modules.pdp.domain.PdpEarning;
import com.courier.modules.pdp.domain.PdpStatement;
import com.courier.modules.pdp.domain.PdpStatementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Billing statements of Pickup-Delivery Partners — the report the company pays against. Built
 * from ACCRUED earnings; approving and paying are manual marks and never post to a wallet.
 * {@code COMPANY_ADMIN} only.
 */
public interface PdpStatementService {

    /** Bills every ACCRUED, unbilled earning of the partner dated in {@code [from, to]}. */
    StatementView generate(UUID branchId, LocalDate from, LocalDate to, String remarks);

    /**
     * Same as {@link #generate} but for the scheduled cycle job, which runs with no logged-in user.
     * Not exposed over HTTP; the bound company is the caller's responsibility.
     */
    StatementView generateScheduled(UUID branchId, LocalDate from, LocalDate to, String remarks);

    Page<PdpStatement> search(UUID branchId, PdpStatementStatus status, Pageable pageable);

    StatementView get(UUID id);

    StatementView approve(UUID id);

    StatementView markPaid(UUID id, LocalDate paidOn, String reference);

    /** Draft only: removes the statement and releases its earnings back to ACCRUED. */
    void delete(UUID id);

    record StatementView(PdpStatement statement, List<PdpEarning> lines, long unratedInPeriod) {
    }
}
