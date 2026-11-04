package com.courier.modules.security.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.security.domain.AutoAction;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityIncidentService {

    /** A dismissed (user, rule) stays quiet this long before it may be raised again. */
    static final Duration DISMISSAL_QUIET_PERIOD = Duration.ofHours(24);

    private final SecurityIncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final UserBanService userBanService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Runs in its own transaction so an auto-ban survives the caller rolling back
     * (a detector firing inside a request that then fails must not undo the ban).
     *
     * @return the new incident, or empty when deduplicated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SecurityIncident> raise(IncidentRequest request) {
        if (request.userId() != null) {
            if (incidentRepository.existsByUserIdAndRuleAndStatus(
                    request.userId(), request.rule(), IncidentStatus.OPEN)) {
                return Optional.empty();
            }
            if (incidentRepository.existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
                    request.userId(), request.rule(), IncidentStatus.DISMISSED,
                    Instant.now().minus(DISMISSAL_QUIET_PERIOD))) {
                return Optional.empty();
            }
        }

        boolean autoBan = request.rule().autoBan();
        SecurityIncident incident = SecurityIncident.builder()
                .companyId(request.companyId())
                .userId(request.userId())
                .rule(request.rule())
                .severity(autoBan ? IncidentSeverity.HIGH : IncidentSeverity.MEDIUM)
                .status(IncidentStatus.OPEN)
                .evidence(toJson(request.evidence()))
                .autoAction(AutoAction.NONE)
                .detectedAt(Instant.now())
                .build();

        if (autoBan && request.userId() != null && isBannable(request.userId())) {
            userBanService.ban(request.userId(), "Auto-ban: " + request.rule());
            incident.setAutoAction(AutoAction.USER_DISABLED);
            incident.setStatus(IncidentStatus.BANNED);
        }

        SecurityIncident saved = incidentRepository.save(incident);
        auditService.record(AuditAction.SECURITY_INCIDENT_RAISED, "SecurityIncident", saved.getId(),
                Map.of("rule", request.rule().name(), "autoAction", saved.getAutoAction().name()));
        return Optional.of(saved);
    }

    @Transactional
    public SecurityIncident ban(UUID incidentId, UUID actorId, String note) {
        SecurityIncident incident = getVisible(incidentId);
        if (actorId != null && actorId.equals(incident.getUserId())) {
            throw new ForbiddenException("You cannot ban yourself");
        }
        if (incident.getUserId() != null) {
            userBanService.ban(incident.getUserId(), "Incident " + incidentId + ": " + note);
        }
        return resolve(incident, IncidentStatus.BANNED, actorId, note);
    }

    @Transactional
    public SecurityIncident dismiss(UUID incidentId, UUID actorId, String note) {
        return resolve(getVisible(incidentId), IncidentStatus.DISMISSED, actorId, note);
    }

    @Transactional
    public SecurityIncident unban(UUID incidentId, UUID actorId, String note) {
        SecurityIncident incident = getVisible(incidentId);
        if (incident.getUserId() != null) {
            userBanService.unban(incident.getUserId());
        }
        return resolve(incident, IncidentStatus.UNBANNED, actorId, note);
    }

    @Transactional(readOnly = true)
    public Page<SecurityIncident> list(IncidentFilter filter, Pageable pageable) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        Specification<SecurityIncident> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!caller.isPlatformAdmin()) {
                p.add(cb.equal(root.get("companyId"), caller.companyId()));
            }
            if (filter != null) {
                if (filter.status() != null) p.add(cb.equal(root.get("status"), filter.status()));
                if (filter.severity() != null) p.add(cb.equal(root.get("severity"), filter.severity()));
                if (filter.rule() != null) p.add(cb.equal(root.get("rule"), filter.rule()));
                if (filter.userId() != null) p.add(cb.equal(root.get("userId"), filter.userId()));
                if (filter.from() != null) p.add(cb.greaterThanOrEqualTo(root.<Instant>get("detectedAt"), filter.from()));
                if (filter.to() != null) p.add(cb.lessThan(root.<Instant>get("detectedAt"), filter.to()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return incidentRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public SecurityIncident get(UUID id) {
        return getVisible(id);
    }

    /** Company isolation: another company's incident is indistinguishable from a missing one. */
    private SecurityIncident getVisible(UUID id) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        SecurityIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityIncident", id));
        if (!caller.isPlatformAdmin() && !Objects.equals(caller.companyId(), incident.getCompanyId())) {
            throw new ResourceNotFoundException("SecurityIncident", id);
        }
        return incident;
    }

    private SecurityIncident resolve(SecurityIncident incident, IncidentStatus status, UUID actorId, String note) {
        incident.setStatus(status);
        incident.setResolvedBy(actorId);
        incident.setResolvedAt(Instant.now());
        incident.setResolutionNote(note);
        return incidentRepository.save(incident);
    }

    /** Platform-tier accounts are never auto-banned: locking them out could lock out the operators. */
    private boolean isBannable(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && !user.hasRole(Role.SUPER_ADMIN) && !user.hasRole(Role.PLATFORM_ADMIN);
    }

    private String toJson(Map<String, Object> evidence) {
        if (evidence == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise incident evidence: {}", e.toString());
            return "{}";
        }
    }
}
