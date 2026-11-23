package com.courier.modules.communication.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchReportLogRepository extends JpaRepository<BranchReportLog, UUID> {

    Optional<BranchReportLog> findByCompanyIdAndBranchIdAndPeriodFromAndPeriodToAndRecipient(
            UUID companyId, UUID branchId, LocalDate periodFrom, LocalDate periodTo, String recipient);

    /** Cross-tenant on purpose, for the same reason as {@code CommunicationLogRepository.findFirstByProviderMessageId}. */
    Optional<BranchReportLog> findFirstByProviderMessageId(String providerMessageId);

    List<BranchReportLog> findAllByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID companyId, Instant from, Instant to);

    /** Every send for one period, for the status column on the report page. */
    List<BranchReportLog> findAllByCompanyIdAndPeriodFromAndPeriodTo(
            UUID companyId, LocalDate periodFrom, LocalDate periodTo);

    /** Branch statements that actually went out in the window — WhatsApp messages too, and they cost the same. */
    long countByCompanyIdAndStatusInAndSentAtGreaterThanEqualAndSentAtLessThan(
            UUID companyId, Collection<CommunicationStatus> statuses, Instant from, Instant to);
}
