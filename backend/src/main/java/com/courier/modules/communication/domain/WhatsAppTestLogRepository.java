package com.courier.modules.communication.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppTestLogRepository extends JpaRepository<WhatsAppTestLog, UUID> {

    /** Cross-tenant on purpose, for the same reason as {@code CommunicationLogRepository.findFirstByProviderMessageId}. */
    Optional<WhatsAppTestLog> findFirstByProviderMessageId(String providerMessageId);

    List<WhatsAppTestLog> findAllByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID companyId, Instant from, Instant to);
}
