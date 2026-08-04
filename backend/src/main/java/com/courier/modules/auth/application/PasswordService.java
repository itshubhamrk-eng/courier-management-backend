package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.NotificationPort;
import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.PasswordResetToken;
import com.courier.modules.auth.domain.PasswordResetTokenRepository;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Password lifecycle: forgotten, reset, and changed.
 *
 * <p>Two properties drive the design of this class:
 * <ul>
 *   <li><b>No enumeration.</b> {@code forgotPassword} returns success whether or not
 *       the account exists, and does the same amount of visible work either way.</li>
 *   <li><b>A password change invalidates sessions.</b> If the password was changed
 *       because of a suspected compromise, leaving the attacker's tokens alive would
 *       defeat the point.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final CompanyDirectoryPort companyDirectory;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final NotificationPort notificationPort;
    private final TokenRevocationService tokenRevocationService;
    private final AuthProperties properties;
    private final AuditService auditService;

    // -------------------------------------------------------------- forgot

    /**
     * Starts a password reset. <b>Always completes normally</b>, even for an unknown
     * address — the caller must not be able to tell whether an account exists.
     */
    @Transactional
    public void forgotPassword(UUID companyId, String rawEmail, String ipAddress) {
        String email = User.normaliseEmail(rawEmail);

        Optional<CompanyDirectoryPort.CompanyRef> company = companyDirectory.findById(companyId)
                .filter(CompanyDirectoryPort.CompanyRef::active);

        if (company.isEmpty()) {
            // Same silent success as an unknown email: an inactive or bogus company id
            // must not be distinguishable either.
            log.debug("Password reset requested for unknown or inactive company {}", companyId);
            return;
        }

        CompanyContext.setCompanyId(companyId);

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            log.debug("Password reset requested for unknown email in company {}", companyId);
            return;
        }

        User user = maybeUser.get();
        if (user.isDisabled()) {
            log.debug("Password reset suppressed for disabled user {}", user.getId());
            return;
        }

        // Only the newest link stays valid.
        resetTokenRepository.consumeAllForUser(user.getId(), Instant.now());

        String rawToken = TokenHasher.generateRawToken();
        resetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.hash(rawToken))
                .expiresAt(Instant.now().plus(properties.getResetTokenTtl()))
                .ipAddress(ipAddress)
                .build());

        String link = UriComponentsBuilder.fromUriString(properties.getAppBaseUrl())
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .toUriString();

        notificationPort.sendPasswordResetLink(user.getEmail(), user.displayName(), link);

        auditService.record(AuditAction.PASSWORD_RESET_REQUESTED, "User", user.getId(),
                Map.of("ipAddress", String.valueOf(ipAddress)));
    }

    // --------------------------------------------------------------- reset

    /**
     * Completes a reset.
     *
     * <p>Also the <b>account unlock</b> path: someone who proves control of the
     * mailbox has demonstrated more than a password guess, so the failed-attempt
     * lock is cleared. Without this, a locked-out user would have to wait even after
     * successfully resetting.
     *
     * <p>Runs unauthenticated, so no company is bound on entry; it is bound from the
     * token row. See {@code PasswordResetTokenRepository#findByTokenHash}.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String ipAddress) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(this::invalidResetToken);

        if (!token.isUsable()) {
            throw invalidResetToken();
        }

        CompanyContext.setCompanyId(token.getCompanyId());

        User user = userRepository.findByIdWithinCompany(token.getUserId(), token.getCompanyId())
                .orElseThrow(this::invalidResetToken);

        passwordPolicy.validate(newPassword, user.getEmail(), user.getPasswordHash(),
                passwordEncoder::matches);

        token.consume();
        resetTokenRepository.save(token);

        user.changePassword(passwordEncoder.encode(newPassword));
        user.clearLock();
        userRepository.save(user);

        // A reset is the standard response to a compromise: every existing session
        // must die, including the attacker's.
        sessionService.revokeAllSessions(user.getId(), RefreshToken.RevokeReason.PASSWORD_RESET);

        log.info("Password reset completed for user {} (lock cleared, all sessions revoked)", user.getId());
        auditService.record(AuditAction.PASSWORD_RESET_COMPLETED, "User", user.getId(),
                Map.of("ipAddress", String.valueOf(ipAddress)));
        auditService.record(AuditAction.ACCOUNT_UNLOCKED, "User", user.getId(),
                Map.of("trigger", "PASSWORD_RESET"));
    }

    // -------------------------------------------------------------- change

    /**
     * Changes the password of the signed-in user.
     *
     * <p>Requires the current password: a stolen access token alone must not be
     * enough to take over an account permanently.
     *
     * <p><b>Every session is revoked, including the caller's</b>, and the access
     * token in use is denylisted immediately. The alternative — keeping the current
     * device signed in — needs a session id inside the access token, which it does
     * not carry. Rather than guess at which session to spare, the honest behaviour
     * is a clean re-authentication: the client receives 200 and must sign in again.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        AuthenticatedUser principal = SecurityUtils.requireCurrentUser();

        User user = userRepository.findByIdWithinCompany(principal.userId(), principal.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.userId()));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("Password change rejected for user {}: current password did not match",
                    user.getId());
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS,
                    "Current password is incorrect");
        }

        passwordPolicy.validate(newPassword, user.getEmail(), user.getPasswordHash(),
                passwordEncoder::matches);

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        int revoked = sessionService.revokeAllSessions(
                user.getId(), RefreshToken.RevokeReason.PASSWORD_CHANGED);

        // Kill the access token that made this call too, so the change takes effect
        // now rather than at the end of its remaining TTL.
        tokenRevocationService.revokeCurrentAccessToken(principal);

        log.info("Password changed for user {}; {} session(s) revoked, re-authentication required",
                user.getId(), revoked);
        auditService.record(AuditAction.PASSWORD_CHANGED, "User", user.getId(),
                Map.of("sessionsRevoked", revoked));
    }

    private BusinessRuleException invalidResetToken() {
        return new BusinessRuleException(ErrorCode.TOKEN_INVALID,
                "This reset link is invalid or has expired. Request a new one.");
    }
}
