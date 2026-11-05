package com.courier.modules.security.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Dedup for incidents with no user (which {@code raise} cannot dedup): is there an incident
     * of this rule for this company, detected at or after {@code since}, whose evidence has a
     * top-level {@code "ip"} equal to {@code ip}? Any status counts, so a recently dismissed one
     * also keeps quiet.
     *
     * <p>Reads the value with JSON_EXTRACT rather than a LIKE on the text: {@code evidence} is a
     * MySQL JSON column, which re-serialises on store ({@code {"ip": "..."}}, keys reordered),
     * so a {@code "ip":"x"} fragment would never match. Volume is tiny; no index is needed.
     */
    @Query("""
           select case when count(i) > 0 then true else false end
             from SecurityIncident i
            where i.rule = :rule
              and i.companyId = :companyId
              and i.detectedAt >= :since
              and function('JSON_UNQUOTE', function('JSON_EXTRACT', i.evidence, '$.ip')) = :ip
           """)
    boolean existsRecentForEvidenceIp(@Param("rule") IncidentRule rule, @Param("companyId") UUID companyId,
                                      @Param("ip") String ip, @Param("since") Instant since);

    /** Dedup: was one of this kind dismissed recently enough to stay quiet? */
    boolean existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
            UUID userId, IncidentRule rule, IncidentStatus status, Instant resolvedAfter);
}
