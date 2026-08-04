package com.courier.shared.company;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enables the Hibernate {@code companyFilter} on the session backing each repository
 * call, so that every query against a {@link CompanyOwnedEntity} is automatically
 * narrowed to the current company.
 *
 * <p>Ordered {@link Ordered#LOWEST_PRECEDENCE} so the advice runs <i>innermost</i> —
 * after Spring's transaction interceptor has opened/joined the transaction. The
 * filter must be enabled on the same session the query will use; enabling it on a
 * different session would silently do nothing.
 *
 * <p>Limitations, deliberately accepted (see {@code MEMORY/BACKLOG.md}):
 * <ul>
 *   <li>Native queries bypass Hibernate filters entirely. {@code nativeQuery = true}
 *       is banned in company-owned repositories.</li>
 *   <li>{@code EntityManager.find()} by primary key is <b>not</b> filtered by
 *       Hibernate. {@link CompanyEntityListener} covers the write path; read paths
 *       that load by id must verify ownership, which is why repositories should
 *       prefer derived queries over {@code findById} for company-owned entities.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CompanyFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("within(org.springframework.data.repository.Repository+)")
    public Object applyCompanyFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID companyId = CompanyContext.getCompanyId().orElse(null);

        if (companyId == null) {
            // Unauthenticated (public tracking), platform-admin or startup work.
            // Such paths must query non-company-owned entities or use an explicitly
            // named cross-company repository method.
            return joinPoint.proceed();
        }

        enableFilter(companyId);
        return joinPoint.proceed();
    }

    private void enableFilter(UUID companyId) {
        try {
            Session session = entityManager.unwrap(Session.class);
            Filter filter = session.getEnabledFilter(CompanyOwnedEntity.COMPANY_FILTER);
            if (filter == null) {
                filter = session.enableFilter(CompanyOwnedEntity.COMPANY_FILTER);
            }
            filter.setParameter(CompanyOwnedEntity.COMPANY_PARAM, companyId);
        } catch (Exception e) {
            // Never swallow this quietly: an un-enabled filter means unfiltered reads.
            log.error("Failed to enable company filter for company {}", companyId, e);
            throw e;
        }
    }
}
