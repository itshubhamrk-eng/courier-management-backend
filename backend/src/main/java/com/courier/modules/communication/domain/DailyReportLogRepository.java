package com.courier.modules.communication.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyReportLogRepository extends JpaRepository<DailyReportLog, UUID> {

    Optional<DailyReportLog> findByCompanyIdAndReportDateAndRecipient(
            UUID companyId, LocalDate reportDate, String recipient);

    /** Cross-tenant on purpose, for the same reason as {@code CommunicationLogRepository.findFirstByProviderMessageId}. */
    Optional<DailyReportLog> findFirstByProviderMessageId(String providerMessageId);

    Optional<DailyReportLog> findFirstByCompanyIdOrderByReportDateDescCreatedAtDesc(UUID companyId);

    /** Daily summaries that actually went out in the window — they are WhatsApp messages too, and cost the same. */
    long countByCompanyIdAndStatusInAndSentAtGreaterThanEqualAndSentAtLessThan(
            UUID companyId, java.util.Collection<CommunicationStatus> statuses, java.time.Instant from, java.time.Instant to);
}
