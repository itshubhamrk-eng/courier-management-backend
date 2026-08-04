package com.courier.shared.audit.application;

import com.courier.shared.api.RequestIdHolder;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.audit.domain.AuditLog;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail.
 *
 * <p>Three design points worth keeping:
 * <ul>
 *   <li><b>Asynchronous</b> — auditing must never add latency to, or fail, the
 *       business transaction it describes.</li>
 *   <li><b>{@code REQUIRES_NEW}</b> — the audit record is committed independently.
 *       If the business transaction rolls back, the attempt is still recorded, which
 *       is precisely what you want when investigating a failed or malicious action.</li>
 *   <li><b>Never throws</b> — a broken audit write is logged loudly but does not
 *       surface to the caller. The alternative is an outage caused by the logging of
 *       an outage.</li>
 * </ul>
 *
 * <p>The request context is captured <i>before</i> handing off to the async executor,
 * because {@code CompanyContext}, {@code SecurityContextHolder} and {@code MDC} are all
 * thread-bound and are empty on the executor thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogWriter writer;
    private final ObjectMapper objectMapper;

    public void record(AuditAction action) {
        record(action, null, null, null, true);
    }

    public void record(AuditAction action, String entityType, UUID entityId) {
        record(action, entityType, entityId, null, true);
    }

    public void record(AuditAction action, String entityType, UUID entityId, Map<String, Object> details) {
        record(action, entityType, entityId, details, true);
    }

    public void recordFailure(AuditAction action, Map<String, Object> details) {
        record(action, null, null, details, false);
    }

    /**
     * Captures everything thread-bound, then hands the finished entity to the
     * async writer.
     */
    public void record(AuditAction action,
                       String entityType,
                       UUID entityId,
                       Map<String, Object> details,
                       boolean success) {

        AuthenticatedUser actor = SecurityUtils.getCurrentUser().orElse(null);
        HttpServletRequest request = currentRequest();

        AuditLog entry = AuditLog.builder()
                .action(action)
                .companyId(CompanyContext.getCompanyId().orElse(actor != null ? actor.companyId() : null))
                .actorId(actor != null ? actor.userId() : null)
                .actorEmail(actor != null ? actor.email() : null)
                .entityType(entityType)
                .entityId(entityId)
                .details(serialise(details))
                .ipAddress(request != null ? clientIp(request) : null)
                .userAgent(request != null ? truncate(request.getHeader("User-Agent"), 512) : null)
                .requestId(MDC.get(RequestIdHolder.REQUEST_ID_KEY))
                .occurredAt(Instant.now())
                .success(success)
                .build();

        writer.write(entry);
    }

    private String serialise(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            log.warn("Could not serialise audit details; storing marker instead", e);
            return "{\"_error\":\"serialisation_failed\"}";
        }
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }

    /**
     * Behind a load balancer the socket address is the proxy, so the first entry of
     * {@code X-Forwarded-For} is used when present.
     *
     * <p>That header is client-controllable and must be treated as a hint for
     * investigation, not as an authentication signal.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
