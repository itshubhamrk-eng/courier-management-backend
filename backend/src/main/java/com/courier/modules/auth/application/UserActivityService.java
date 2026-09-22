package com.courier.modules.auth.application;

import com.courier.modules.auth.api.dto.ActiveSessionResponse;
import com.courier.modules.auth.api.dto.LoginHistoryResponse;
import com.courier.modules.auth.api.dto.UserActivitySummaryResponse;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.shared.activity.api.ActivityLogMapper;
import com.courier.shared.activity.application.ActivityLogService;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The User Activity screen: one user's login history, logout history, recent activities,
 * modules accessed, last activity time and any currently active session — composed here,
 * in {@code modules/auth}, rather than in {@code shared/activity}, because it needs
 * {@code LoginHistory}/{@code UserSession} and {@code shared} must never import from
 * {@code modules} (AI_CONTEXT.md decision 12). {@code modules} depending on {@code shared}
 * is the normal, allowed direction.
 *
 * <p>Company isolation for {@code LoginHistory}/{@code UserSession} comes for free from
 * their own {@code CompanyOwnedEntity} Hibernate filter; {@link ActivityLogService}
 * enforces its own separately, since {@code ActivityLog} is not company-filtered at the
 * entity level — see that class's doc.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private static final String READERS = "hasRole('" + Roles.SUPER_ADMIN + "') or hasAuthority('AUDIT_READ')";

    private static final int RECENT_LOGIN_LIMIT = 20;
    private static final int RECENT_ACTIVITY_LIMIT = 50;

    private final LoginHistoryRepository loginHistoryRepository;
    private final UserSessionRepository userSessionRepository;
    private final ActivityLogService activityLogService;
    private final ActivityLogMapper activityLogMapper;

    @PreAuthorize(READERS)
    public UserActivitySummaryResponse summaryFor(UUID userId) {
        List<LoginHistoryResponse> loginHistory = loginHistoryRepository
                .findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, RECENT_LOGIN_LIMIT))
                .map(this::toResponse)
                .getContent();

        var recentActivities = activityLogService
                .recentForUser(userId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .map(activityLogMapper::toResponse)
                .getContent();

        List<String> modules = activityLogService.modulesAccessedBy(userId);

        List<ActiveSessionResponse> activeSessions = userSessionRepository
                .findActiveByUserId(userId, Instant.now()).stream()
                .map(s -> new ActiveSessionResponse(s.getId(), s.getDeviceName(), s.getDeviceType(),
                        s.getBrowser(), s.getOs(), s.getIpAddress(), s.getLastSeenAt(), s.getExpiresAt(),
                        s.isRememberMe()))
                .sorted((a, b) -> b.lastSeenAt().compareTo(a.lastSeenAt()))
                .toList();

        Instant lastActivityAt = !recentActivities.isEmpty() ? recentActivities.get(0).occurredAt()
                : loginHistory.isEmpty() ? null : loginHistory.get(0).occurredAt();

        return new UserActivitySummaryResponse(loginHistory, recentActivities, modules, lastActivityAt,
                activeSessions);
    }

    private LoginHistoryResponse toResponse(com.courier.modules.auth.domain.LoginHistory h) {
        return new LoginHistoryResponse(h.getId(), h.getUserId(), h.getAttemptedEmail(),
                h.getEventType() != null ? h.getEventType().name() : null, h.isSuccess(), h.getFailureReason(),
                h.getSessionId(), h.getIpAddress(), h.getDevice(), h.getBrowser(), h.getOs(), h.getOccurredAt(),
                h.getLogoutAt());
    }
}
