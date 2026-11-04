package com.courier.modules.security.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface SecurityIncidentRepository
        extends JpaRepository<SecurityIncident, UUID>, JpaSpecificationExecutor<SecurityIncident> {

    /** Dedup: is there already an open incident of this kind for the user? */
    boolean existsByUserIdAndRuleAndStatus(UUID userId, IncidentRule rule, IncidentStatus status);

    /** Dedup: was one of this kind dismissed recently enough to stay quiet? */
    boolean existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
            UUID userId, IncidentRule rule, IncidentStatus status, Instant resolvedAfter);
}
