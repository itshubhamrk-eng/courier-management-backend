package com.courier.shared.activity.application;

import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogCriteria;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.ActivityLogSpecifications;
import com.courier.shared.activity.domain.ActivityStatus;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read/write API over {@code activity_logs}. Writes come from {@code ActivityLoggingFilter}
 * (the automatic path — one call per request) and from any service that also calls
 * {@code ActivityContext.recordChange} first. Reads back the Admin Activity Log screen and
 * the per-user Activity screen.
 *
 * <p><b>Company isolation is enforced here, not by a Hibernate filter</b> — {@link ActivityLog}
 * extends {@code BaseEntity} on purpose (see its own class doc), so every read path in this
 * class pins {@code companyId} from the caller's own token unless the caller is platform-tier.
 * A company id arriving in a query string is therefore overridden, never honoured — the same
 * rule AI_CONTEXT.md decision 27 states for every other module's search criteria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    /** AUDIT_READ/SEARCH/EXPORT already exist in the permission catalogue (V6) — the
     *  responsibility list was ahead of the code (MEMORY note) until this module gave
     *  them somewhere to point. Reused rather than adding ACTIVITY_LOG_VIEW/EXPORT
     *  codes that would mean exactly the same thing under a different name. */
    private static final String READERS = "hasRole('" + Roles.SUPER_ADMIN + "') or hasAuthority('AUDIT_READ')";
    private static final String SEARCHERS = "hasRole('" + Roles.SUPER_ADMIN + "') or hasAuthority('AUDIT_SEARCH')";
    private static final String EXPORTERS = "hasRole('" + Roles.SUPER_ADMIN + "') or hasAuthority('AUDIT_EXPORT')";

    private final ActivityLogRepository repository;
    private final ActivityLogWriter writer;
    private final SensitiveDataMasker masker;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ write

    public void log(String module, String submodule, String action, String entityType, String entityId,
                    String description, Map<String, Object> oldValue, Map<String, Object> newValue,
                    UUID companyId, UUID userId, String username, String ipAddress, String device,
                    String browser, String os, UUID sessionId, String requestMethod, String apiEndpoint,
                    ActivityStatus status, String errorMessage) {

        ActivityLog entry = ActivityLog.builder()
                .companyId(companyId)
                .userId(userId)
                .username(username)
                .module(module)
                .submodule(submodule)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(truncate(description, 1000))
                .oldValue(serialise(oldValue))
                .newValue(serialise(newValue))
                .ipAddress(ipAddress)
                .device(device)
                .browser(browser)
                .os(os)
                .sessionId(sessionId)
                .requestMethod(requestMethod)
                .apiEndpoint(truncate(apiEndpoint, 255))
                .status(status)
                .errorMessage(truncate(errorMessage, 500))
                .occurredAt(Instant.now())
                .build();

        writer.write(entry);
    }

    private String serialise(Map<String, Object> value) {
        Map<String, Object> masked = masker.mask(value);
        if (masked == null || masked.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(masked);
        } catch (Exception e) {
            log.warn("Could not serialise activity value; storing marker instead", e);
            return "{\"_error\":\"serialisation_failed\"}";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ------------------------------------------------------------------- read

    /** Pins {@code companyId} to the caller's own company unless platform-tier. */
    @PreAuthorize(SEARCHERS)
    public Page<ActivityLog> search(ActivityLogCriteria requested, Pageable pageable) {
        ActivityLogCriteria scoped = scopeToCaller(requested);
        return repository.findAll(ActivityLogSpecifications.matching(scoped), pageable);
    }

    /** Same filters as {@link #search}, unpaged and capped — backs CSV export. */
    @PreAuthorize(EXPORTERS)
    public List<ActivityLog> export(ActivityLogCriteria requested, int maxRows) {
        ActivityLogCriteria scoped = scopeToCaller(requested);
        Pageable capped = org.springframework.data.domain.PageRequest.of(0, maxRows,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "occurredAt"));
        return repository.findAll(ActivityLogSpecifications.matching(scoped), capped).getContent();
    }

    @PreAuthorize(READERS)
    public ActivityLog getById(UUID id) {
        ActivityLog entry = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity log entry not found: " + id));
        var caller = SecurityUtils.requireCurrentUser();
        if (!caller.isPlatformTier() && entry.getCompanyId() != null
                && !entry.getCompanyId().equals(caller.companyId())) {
            throw new ResourceNotFoundException("Activity log entry not found: " + id);
        }
        return entry;
    }

    /** Recent activities for the User Activity screen, newest first. */
    @PreAuthorize(READERS)
    public Page<ActivityLog> recentForUser(UUID userId, Pageable pageable) {
        UUID companyId = requireCallerCompanyOrExplicit(userId);
        return repository.findByCompanyIdAndUserIdOrderByOccurredAtDesc(companyId, userId, pageable);
    }

    /** Modules a user has touched — "modules accessed" on the same screen. */
    @PreAuthorize(READERS)
    public List<String> modulesAccessedBy(UUID userId) {
        UUID companyId = requireCallerCompanyOrExplicit(userId);
        return repository.findDistinctModulesFor(companyId, userId);
    }

    private UUID requireCallerCompanyOrExplicit(UUID userId) {
        // Platform tier has no company of its own to pin to; every other caller is
        // confined to their own company regardless of whose activity they are viewing.
        return SecurityUtils.getCurrentUser()
                .map(u -> u.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("No user activity for " + userId));
    }

    /** Package-private so {@code ActivityLogServiceTest} can assert the isolation rule
     *  directly, without mocking a JPA {@code Specification} just to prove a companyId. */
    ActivityLogCriteria scopeToCaller(ActivityLogCriteria requested) {
        var caller = SecurityUtils.requireCurrentUser();
        UUID companyId = caller.isSuperAdmin() ? requested.companyId() : caller.companyId();
        return new ActivityLogCriteria(companyId, requested.userId(), requested.module(), requested.action(),
                requested.entityType(), requested.entityId(), requested.status(), requested.sessionId(),
                requested.dateFrom(), requested.dateTo(), requested.search());
    }
}
