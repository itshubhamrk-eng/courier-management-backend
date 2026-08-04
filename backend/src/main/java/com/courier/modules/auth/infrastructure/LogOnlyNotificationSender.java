package com.courier.modules.auth.infrastructure;

import com.courier.modules.auth.application.port.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Development notification sender: writes the link to the log instead of mailing it.
 *
 * <p><b>Restricted to non-production profiles.</b> A password-reset link in a log
 * file is a credential sitting in plaintext wherever logs are shipped and retained;
 * that is acceptable on a laptop and not acceptable anywhere else.
 *
 * <p>Production therefore has <em>no</em> default implementation. The context fails
 * to start until a real sender is supplied, which is the correct behaviour: silently
 * swallowing password-reset emails would look exactly like a working system while
 * locking users out.
 */
@Slf4j
@Configuration
@Profile("!prod")
public class LogOnlyNotificationSender {

    @Bean
    @ConditionalOnMissingBean(NotificationPort.class)
    public NotificationPort logOnlyNotificationPort() {
        log.warn("No real NotificationPort configured — password reset and verification "
                + "links will be written to the application log. Development only.");

        return new NotificationPort() {
            @Override
            public void sendPasswordResetLink(String email, String displayName, String link) {
                log.info("[DEV NOTIFICATION] Password reset for {} ({}): {}", email, displayName, link);
            }

            @Override
            public void sendEmailVerificationLink(String email, String displayName, String link) {
                log.info("[DEV NOTIFICATION] Email verification for {} ({}): {}", email, displayName, link);
            }

            @Override
            public void sendCompanyActivation(String email, String displayName,
                                              String companyName, String link) {
                // No temporary password here — the port is not given one, precisely so
                // that no implementation can put a credential in a log or a mailbox.
                log.info("[DEV NOTIFICATION] Activation for {} ({}) of company {}: {}",
                        email, displayName, companyName, link);
            }
        };
    }
}
