package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketSlaRuleRepository extends JpaRepository<TicketSlaRule, UUID> {

    List<TicketSlaRule> findAllByCompanyIdOrderByPriorityAsc(UUID companyId);

    Optional<TicketSlaRule> findByCompanyIdAndPriorityAndActiveTrue(UUID companyId, TicketPriority priority);

    Optional<TicketSlaRule> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndPriority(UUID companyId, TicketPriority priority);
}
