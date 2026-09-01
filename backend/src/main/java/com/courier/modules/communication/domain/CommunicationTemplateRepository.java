package com.courier.modules.communication.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunicationTemplateRepository extends JpaRepository<CommunicationTemplate, UUID> {

    @Query("select t from CommunicationTemplate t where t.id = :id and t.companyId = :companyId")
    Optional<CommunicationTemplate> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<CommunicationTemplate> findByCompanyIdAndEventTypeAndChannel(
            UUID companyId, CommunicationEventType eventType, CommunicationChannel channel);

    List<CommunicationTemplate> findAllByCompanyIdOrderByEventTypeAscChannelAsc(UUID companyId);

    long countByCompanyId(UUID companyId);
}
