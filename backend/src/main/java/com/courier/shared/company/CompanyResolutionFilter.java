package com.courier.shared.company;

import com.courier.shared.api.RequestIdHolder;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Binds the company for the duration of the request.
 *
 * <p>Runs after {@code JwtAuthenticationFilter}, because the company is taken from the
 * <b>verified token</b>, never from anything the client can choose. A client-supplied
 * {@code X-Company-ID} would be a one-header path to every other company's data.
 *
 * <p>The single exception is impersonation by a {@code PLATFORM_ADMIN}, whose token
 * legitimately carries no company. That path is logged at INFO on every use; once the
 * audit module has a request-scoped writer this should also emit a
 * {@code COMPANY_IMPERSONATED} audit event (tracked in {@code MEMORY/BACKLOG.md}).
 *
 * <p>{@link CompanyContext#clear()} runs in a {@code finally} block. Servlet threads are
 * pooled and reused; a leaked binding would silently hand one request's company to the
 * next.
 */
@Slf4j
@Component
public class CompanyResolutionFilter extends OncePerRequestFilter {

    public static final String COMPANY_HEADER = "X-Company-ID";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            resolveCompany(request).ifPresent(companyId -> {
                CompanyContext.setCompanyId(companyId);
                MDC.put(RequestIdHolder.COMPANY_ID_KEY, companyId.toString());
            });
            filterChain.doFilter(request, response);
        } finally {
            CompanyContext.clear();
            MDC.remove(RequestIdHolder.COMPANY_ID_KEY);
        }
    }

    private java.util.Optional<UUID> resolveCompany(HttpServletRequest request) {
        AuthenticatedUser user = SecurityUtils.getCurrentUser().orElse(null);

        if (user == null) {
            // Public endpoint (login, tracking, docs). No company, and the Hibernate
            // filter stays off — such handlers must not touch company-owned entities
            // except through explicitly named cross-company queries.
            return java.util.Optional.empty();
        }

        if (user.isPlatformAdmin()) {
            return resolveForPlatformAdmin(request, user);
        }

        if (user.isSuperAdmin()) {
            // Platform tier, and normally company-less: a super admin manages the plan
            // catalogue, which is not company-owned. X-Company-ID is deliberately NOT
            // honoured here — impersonating a company is PLATFORM_ADMIN's job, and
            // widening it to this role would grant company data access nobody asked for.
            return java.util.Optional.ofNullable(user.companyId());
        }

        if (user.companyId() == null) {
            // A non-admin token without a company claim is malformed. Refuse to guess.
            log.warn("Token for user {} carries no company claim; request left unbound", user.userId());
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(user.companyId());
    }

    private java.util.Optional<UUID> resolveForPlatformAdmin(HttpServletRequest request,
                                                             AuthenticatedUser admin) {
        String header = request.getHeader(COMPANY_HEADER);
        if (header == null || header.isBlank()) {
            // Operating at platform level: company-owned queries stay unfiltered, which
            // is why platform endpoints must only touch platform-level entities.
            return java.util.Optional.ofNullable(admin.companyId());
        }

        try {
            UUID impersonated = UUID.fromString(header.trim());
            log.info("Platform admin {} is acting as company {} on {} {}",
                    admin.userId(), impersonated, request.getMethod(), request.getRequestURI());
            return java.util.Optional.of(impersonated);
        } catch (IllegalArgumentException e) {
            log.warn("Platform admin {} sent a malformed {} header", admin.userId(), COMPANY_HEADER);
            return java.util.Optional.empty();
        }
    }
}
