package com.courier.shared.config;

import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Populates {@code created_by} / {@code updated_by} / {@code created_at} /
 * {@code updated_at} on every {@code BaseEntity}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * The acting user, or empty for system work (migrations, scheduled jobs,
     * anonymous requests) — those rows legitimately carry a null actor.
     */
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> SecurityUtils.getCurrentUser().map(AuthenticatedUser::userId);
    }

    /**
     * Timestamps come from {@link Instant#now()} — UTC, unambiguous, and independent
     * of the JVM's default zone, which differs between a developer laptop and a
     * container.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
