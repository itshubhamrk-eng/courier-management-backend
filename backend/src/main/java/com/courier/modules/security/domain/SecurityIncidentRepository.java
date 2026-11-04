package com.courier.modules.security.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface SecurityIncidentRepository
        extends JpaRepository<SecurityIncident, UUID>, JpaSpecificationExecutor<SecurityIncident> {

    /** Dedup: is there already an open incident of this kind for the user? */
    boolean existsByUserIdAndRuleAndStatus(UUID userId, IncidentRule rule, IncidentStatus status);

    /** Dedup: is there already a live (e.g. OPEN or BANNED) incident of this kind for the user? */
    boolean existsByUserIdAndRuleAndStatusIn(
            UUID userId, IncidentRule rule, java.util.Collection<IncidentStatus> statuses);

    /** Is another incident (not {@code excludeId}) for this user still in the given status? */
    boolean existsByUserIdAndStatusAndIdNot(UUID userId, IncidentStatus status, UUID excludeId);

    /** Dedup: was one of this kind dismissed recently enough to stay quiet? */
    boolean existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
            UUID userId, IncidentRule rule, IncidentStatus status, Instant resolvedAfter);
}
