package com.courier.shared.activity.infrastructure;

import com.courier.shared.activity.application.ActivityContext;
import com.courier.shared.activity.application.ActivityLogService;
import com.courier.shared.activity.domain.ActivityStatus;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.useragent.UserAgentParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes one {@link com.courier.shared.activity.domain.ActivityLog} row per meaningful
 * authenticated API call, with <b>no controller or service code required anywhere</b> —
 * this is requirement 4's "automatic logging" in full. A handful of call sites additionally
 * call {@link ActivityContext#recordChange} right before returning, to attach an old/new
 * value this filter has no way to know on its own (see that class's own doc); everything
 * else — module, submodule, action, entity id, request method, endpoint, status, ip,
 * device/browser/os — is inferred here from the request/response alone.
 *
 * <p>Runs after {@code JwtAuthenticationFilter} and {@code CompanyResolutionFilter}, so the
 * principal and company are both already resolved when this filter's post-processing runs.
 *
 * <p><b>Deliberately does not log every GET.</b> A paginated list endpoint is polled far
 * more often than it is meaningful, and requirement 9 asks this module not to slow the
 * application down or store data nobody will read. A GET is logged as {@code VIEW} only
 * when its last path segment looks like one record's id (a UUID, or a short alphanumeric
 * code) — the same distinction {@code PermissionAction.READ} vs {@code SEARCH} already
 * draws for permission codes. Every mutating verb is always logged.
 *
 * <p><b>Never stores a raw request body larger than {@link #MAX_BODY_CAPTURE_BYTES}</b>, and
 * always runs it through {@code SensitiveDataMasker} first — requirements 8 and 9 in one
 * guard.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLoggingFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private static final int MAX_BODY_CAPTURE_BYTES = 4_096;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern SIMPLE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{2,40}$");

    /** Never worth an activity row: nothing authenticatable, or pure infrastructure. */
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password", "/api/v1/auth/verify-email",
            "/api/v1/companies/register", "/api/v1/activity-logs");

    /** Trailing path segments that name an action more specific than the HTTP verb alone. */
    private static final Map<String, String> ACTION_KEYWORDS = Map.ofEntries(
            Map.entry("dispatch", "DISPATCH"), Map.entry("receive", "RECEIVE"),
            Map.entry("approve", "APPROVE"), Map.entry("reject", "REJECT"),
            Map.entry("cancel", "CANCEL"), Map.entry("assign", "ASSIGN"),
            Map.entry("print", "PRINT"), Map.entry("export", "EXPORT"),
            Map.entry("download", "DOWNLOAD"), Map.entry("upload", "UPLOAD"),
            Map.entry("status", "STATUS_CHANGE"), Map.entry("deliver", "DELIVER"),
            Map.entry("activate", "ACTIVATE"), Map.entry("deactivate", "DEACTIVATE"),
            Map.entry("suspend", "SUSPEND"), Map.entry("renew", "RENEW"),
            Map.entry("recharge", "RECHARGE"), Map.entry("out-scan", "OUT_SCAN"),
            Map.entry("in-scan", "IN_SCAN"), Map.entry("verify", "VERIFY"),
            Map.entry("logout", "LOGOUT"), Map.entry("permissions", "PERMISSION_CHANGE"),
            Map.entry("assign-role", "ROLE_ASSIGNED"), Map.entry("password", "PASSWORD_CHANGE"));

    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/")) {
            return true;
        }
        return EXCLUDED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        boolean mutating = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
        boolean cacheBody = mutating && isJsonBody(request);

        ContentCachingRequestWrapper wrappedRequest =
                cacheBody ? new ContentCachingRequestWrapper(request, MAX_BODY_CAPTURE_BYTES) : null;
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, wrappedResponse);
        } finally {
            try {
                recordIfApplicable(wrappedRequest != null ? wrappedRequest : request, wrappedResponse,
                        method, mutating);
            } catch (Exception e) {
                // Logging the activity must never break the response it is describing.
                log.error("Activity logging failed for {} {}", method, request.getRequestURI(), e);
            } finally {
                ActivityContext.clear();
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    private void recordIfApplicable(HttpServletRequest request, ContentCachingResponseWrapper response,
                                    String method, boolean mutating) {
        Optional<AuthenticatedUser> maybeUser = SecurityUtils.getCurrentUser();
        if (maybeUser.isEmpty()) {
            // Anonymous traffic under /api/v1 is either a public endpoint or a 401 the
            // security layer already logged; neither has a "who" worth a row here.
            return;
        }
        AuthenticatedUser user = maybeUser.get();

        String uri = request.getRequestURI();
        String[] segments = uri.replaceFirst("^/api/v1/", "").split("/");
        if (segments.length == 0 || segments[0].isBlank()) {
            return;
        }

        String lastSegment = segments[segments.length - 1];
        boolean lastLooksLikeId = UUID_PATTERN.matcher(lastSegment).matches();
        boolean viewableGet = "GET".equals(method)
                && (lastLooksLikeId || (segments.length > 1 && SIMPLE_ID_PATTERN.matcher(lastSegment).matches()
                        && !lastSegment.equalsIgnoreCase(segments[0])));

        if (!mutating && !viewableGet) {
            return;
        }

        ActivityContext.Entry enrichment = ActivityContext.get();

        String module = enrichment != null && enrichment.module() != null
                ? enrichment.module() : titleCase(segments[0]);
        String submodule = enrichment != null ? enrichment.submodule() : inferSubmodule(segments);
        // The action always comes from the verb/route, even when a service enriched the
        // request with an old/new value — the URL is still the ground truth for "what verb
        // was invoked", enrichment only adds detail about its effect.
        String action = inferAction(method, segments, lastLooksLikeId);
        String entityId = enrichment != null && enrichment.entityId() != null
                ? enrichment.entityId() : firstIdSegment(segments);
        String entityType = enrichment != null && enrichment.entityType() != null
                ? enrichment.entityType() : singularize(module);
        String description = enrichment != null && enrichment.description() != null
                ? enrichment.description() : "%s %s".formatted(action, module);

        int status = response.getStatus();
        ActivityStatus outcome = status < 400 ? ActivityStatus.SUCCESS : ActivityStatus.FAILURE;
        String errorMessage = outcome == ActivityStatus.FAILURE ? extractErrorMessage(response) : null;

        UserAgentParser.Parsed agent = UserAgentParser.parse(request.getHeader("User-Agent"));
        String requestTemplate = Optional.ofNullable(
                        (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .orElse(uri);

        Map<String, Object> newValue = enrichment != null ? enrichment.newValue() : captureBody(request);
        Map<String, Object> oldValue = enrichment != null ? enrichment.oldValue() : null;

        activityLogService.log(module, submodule, action, entityType, entityId, description,
                oldValue, newValue, user.companyId(), user.userId(), user.email(), clientIp(request),
                agent.device(), agent.browser(), agent.os(), null, method, requestTemplate,
                outcome, errorMessage);
    }

    private boolean isJsonBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBody(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        try {
            Object tree = objectMapper.readValue(content, Object.class);
            if (tree instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("value", tree);
            return wrapped;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractErrorMessage(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0 || content.length > MAX_BODY_CAPTURE_BYTES) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            JsonNode message = node.get("message");
            return message != null ? message.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String inferAction(String method, String[] segments, boolean lastLooksLikeId) {
        String candidateKeyword = lastLooksLikeId && segments.length > 1 ? segments[segments.length - 2]
                : segments[segments.length - 1];
        String mapped = ACTION_KEYWORDS.get(candidateKeyword.toLowerCase());
        if (mapped != null) {
            return mapped;
        }
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            case "GET" -> "VIEW";
            default -> method;
        };
    }

    private String inferSubmodule(String[] segments) {
        if (segments.length < 3) {
            return null;
        }
        // segments[1] is usually an id when present; the true submodule is whichever of
        // segments[1]/segments[2] is not shaped like an id.
        for (int i = 1; i < segments.length - 1; i++) {
            String seg = segments[i];
            if (!UUID_PATTERN.matcher(seg).matches() && !seg.matches("\\d+")) {
                return titleCase(seg);
            }
        }
        return null;
    }

    private String firstIdSegment(String[] segments) {
        for (String seg : segments) {
            if (UUID_PATTERN.matcher(seg).matches()) {
                return seg;
            }
        }
        return null;
    }

    private String titleCase(String segment) {
        String[] words = segment.replace('-', ' ').replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.isEmpty() ? segment : sb.toString();
    }

    private String singularize(String titleCasedModule) {
        String lower = titleCasedModule.toLowerCase();
        if (lower.endsWith("ies")) {
            return titleCasedModule.substring(0, titleCasedModule.length() - 3) + "y";
        }
        if (lower.endsWith("ches") || lower.endsWith("shes") || lower.endsWith("xes") || lower.endsWith("sses")) {
            return titleCasedModule.substring(0, titleCasedModule.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss")) {
            return titleCasedModule.substring(0, titleCasedModule.length() - 1);
        }
        return titleCasedModule;
    }

    /** Same reasoning as {@code AuditService.clientIp}: a proxy-visible header, treated as
     *  a hint for investigation, never as an authentication signal. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() <= 45 ? first : first.substring(0, 45);
        }
        String remote = request.getRemoteAddr();
        return remote != null && remote.length() > 45 ? remote.substring(0, 45) : remote;
    }
}
