package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.UserEndpointCount;
import com.courier.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One user repeatedly hitting things their role may not do. Flag-only.
 *
 * <p>What it measures: {@code activity_logs} rows with {@code status = FAILURE} whose
 * {@code error_message} is exactly {@link ErrorCode#ACCESS_DENIED}'s default message
 * ("You do not have permission to perform this action"), per user, in the scan window;
 * fires when the total is MORE than {@code accessDenied.threshold}. The table stores no
 * HTTP status, only SUCCESS/FAILURE plus the response envelope's {@code message}, and that
 * exact message is written by both generic 403 paths:
 * <ul>
 *   <li>URL rules in {@code SecurityConfig}: the access-denied handler writes it, and
 *       {@code ActivityLoggingFilter} sits before Spring Security's authorization filter
 *       in the chain, so it sees the 403 with the principal still bound;</li>
 *   <li>{@code @PreAuthorize} denials: {@code GlobalExceptionHandler.handleAccessDenied}.</li>
 * </ul>
 * 404s carry "Resource not found" / "Endpoint not found" / "X not found" and never match.
 *
 * <p>Evidence groups by (method, {@code api_endpoint}). That column holds the handler's
 * route template when the request reached a controller ({@code @PreAuthorize} denials), but
 * the raw request URI for URL-rule 403s, which are rejected before handler mapping; so one
 * route can appear under several raw URIs. Counts are unaffected. The incident's company is
 * the user's home company ({@code users.company_id}), falling back to the rows' company.
 *
 * <p>Not counted (no honest way to tell them apart from other failures by message):
 * {@code ForbiddenException}s thrown with a custom message (branch-scope checks such as
 * "You may only manage users of your own branch."), cross-company isolation denials, and
 * denied list GETs, which the filter never logs at all (it logs mutations and GETs of one
 * record only). Anonymous 401s have no user and are not logged either.
 */
@Component
@RequiredArgsConstructor
public class AccessDeniedBurstRule implements ScanRule {

    static final String ACCESS_DENIED_MESSAGE = ErrorCode.ACCESS_DENIED.getDefaultMessage();

    /** Evidence lists at most this many endpoints, most denied first. */
    private static final int MAX_ENDPOINTS = 5;

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final SecurityProperties properties;

    @Override
    public IncidentRule code() {
        return IncidentRule.ACCESS_DENIED_BURST;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        int threshold = properties.getAccessDenied().getThreshold();
        Map<UUID, List<UserEndpointCount>> byUser = new LinkedHashMap<>();
        for (UserEndpointCount row
                : activityLogRepository.findFailureCountsByErrorMessage(from, to, ACCESS_DENIED_MESSAGE)) {
            byUser.computeIfAbsent(row.userId(), k -> new ArrayList<>()).add(row);
        }

        Map<UUID, Long> flagged = new LinkedHashMap<>();
        byUser.forEach((userId, rows) -> {
            long denials = rows.stream().mapToLong(UserEndpointCount::count).sum();
            if (denials > threshold) {
                flagged.put(userId, denials);
            }
        });
        Map<UUID, UUID> homeCompany = HomeCompany.of(userRepository, flagged.keySet());

        List<IncidentRequest> out = new ArrayList<>();
        flagged.forEach((userId, denials) -> {
            List<UserEndpointCount> rows = byUser.get(userId);
            Map<String, Long> top = new LinkedHashMap<>();
            rows.stream()
                    .sorted(Comparator.comparingLong(UserEndpointCount::count).reversed())
                    .limit(MAX_ENDPOINTS)
                    .forEach(r -> top.merge(r.requestMethod() + " " + r.apiEndpoint(), r.count(), Long::sum));
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("denials", denials);
            evidence.put("threshold", threshold);
            evidence.put("distinctEndpoints", rows.size());
            evidence.put("topEndpoints", top);
            evidence.put("windowFrom", from.toString());
            evidence.put("windowTo", to.toString());
            UUID companyId = homeCompany.getOrDefault(userId, rows.get(0).companyId());
            out.add(new IncidentRequest(companyId, userId, IncidentRule.ACCESS_DENIED_BURST, evidence));
        });
        return out;
    }
}
