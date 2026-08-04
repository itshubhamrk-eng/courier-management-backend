package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.NotificationPort;
import com.courier.modules.auth.domain.EmailVerificationToken;
import com.courier.modules.auth.domain.EmailVerificationTokenRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Issues and consumes email-verification links.
 *
 * <p>There is no registration endpoint in this module, so tokens are issued from the
 * login path: when an unverified user presents correct credentials, a fresh link is
 * sent. That keeps the feature self-contained and means a user who lost the original
 * email can recover simply by trying to sign in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationPort notificationPort;
    private final AuthProperties properties;
    private final AuditService auditService;

    /**
     * Sends a verification link unless one was sent very recently.
     *
     * <p>The resend window stops a scripted login loop from mailbombing a user, which
     * would otherwise be an easy way to abuse a public endpoint.
     */
    @Transactional
    public void reissueIfDue(User user) {
        Optional<EmailVerificationToken> latest =
                tokenRepository.findLatestPendingForUser(user.getId());

        boolean tooSoon = latest
                .map(token -> token.getCreatedAt() != null
                        && token.getCreatedAt().isAfter(
                                Instant.now().minus(properties.getVerificationResendWindow())))
                .orElse(false);

        if (tooSoon) {
            log.debug("Verification email for user {} suppressed; one was sent within the resend window",
                    user.getId());
            return;
        }

        issue(user);
    }

    /** Invalidates outstanding links and sends a new one. */
    @Transactional
    public void issue(User user) {
        String link = newLink(user);
        notificationPort.sendEmailVerificationLink(user.getEmail(), user.displayName(), link);

        auditService.record(AuditAction.EMAIL_VERIFICATION_SENT, "User", user.getId(),
                Map.of("email", user.getEmail()));
    }

    /**
     * Sends the activation email a newly provisioned company administrator receives.
     *
     * <p>Same token and same {@code /verify-email} link as {@link #issue} — activating
     * an account and proving its address are one act, and two token families would mean
     * two ways to be half-activated. Only the wording differs, which is why this is a
     * distinct {@code NotificationPort} method rather than a flag.
     *
     * <p><b>The temporary password is deliberately not passed in.</b> It goes back to
     * the super admin who created the company, once, in the create response. Putting a
     * plaintext credential in an email puts it in a mailbox, a mail server and every
     * backup of both — and this address has not been proven to belong to anyone yet.
     */
    @Transactional
    public void issueCompanyActivation(User user, String companyName) {
        String link = newLink(user);
        notificationPort.sendCompanyActivation(user.getEmail(), user.displayName(), companyName, link);

        auditService.record(AuditAction.EMAIL_VERIFICATION_SENT, "User", user.getId(),
                Map.of("email", user.getEmail(), "purpose", "COMPANY_ACTIVATION"));
    }

    /** Consumes any outstanding tokens and mints a fresh single-use link. */
    private String newLink(User user) {
        tokenRepository.consumeAllForUser(user.getId(), Instant.now());

        String rawToken = TokenHasher.generateRawToken();
        tokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.hash(rawToken))
                .expiresAt(Instant.now().plus(properties.getVerificationTokenTtl()))
                .build());

        return UriComponentsBuilder.fromUriString(properties.getAppBaseUrl())
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }

    /**
     * Consumes a token and marks the address verified.
     *
     * <p>Runs without a bound company: the link is followed by an unauthenticated
     * browser. The lookup is by hash of 32 random bytes and the company is bound from
     * the row found — see {@code EmailVerificationTokenRepository#findByTokenHash}.
     */
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(() -> invalid());

        if (!token.isUsable()) {
            throw invalid();
        }

        CompanyContext.setCompanyId(token.getCompanyId());

        User user = userRepository.findByIdWithinCompany(token.getUserId(), token.getCompanyId())
                .orElseThrow(this::invalid);

        token.consume();
        tokenRepository.save(token);

        user.markEmailVerified();
        userRepository.save(user);

        log.info("Email verified for user {} in company {}", user.getId(), token.getCompanyId());
        auditService.record(AuditAction.EMAIL_VERIFIED, "User", user.getId(),
                Map.of("email", user.getEmail()));
    }

    private BusinessRuleException invalid() {
        // One message for unknown, expired and already-used, so the endpoint cannot
        // be used to probe which tokens ever existed.
        return new BusinessRuleException(ErrorCode.TOKEN_INVALID,
                "This verification link is invalid or has expired. Request a new one by signing in.");
    }
}
