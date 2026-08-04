package com.courier.modules.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Users, always within the bound company.
 *
 * <p>Every method here relies on the Hibernate {@code companyFilter} being active,
 * which {@code CompanyFilterAspect} guarantees whenever {@code CompanyContext} is set.
 * Callers must bind the company <em>before</em> invoking anything on this interface;
 * the login use case does so from the validated {@code companyId}.
 *
 * <p>Note the absence of {@code findById}: for a company-owned entity Hibernate does
 * not apply filters to a primary-key load, so {@code findById} would happily return
 * another company's row. {@link #findByIdWithinCompany} makes the company predicate
 * explicit instead.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Explicit company predicate rather than {@code findById}, because a PK load
     * bypasses the Hibernate filter. Belt and braces: the filter also applies, so
     * the company is checked twice.
     */
    @Query("select u from User u where u.id = :id and u.companyId = :companyId")
    Optional<User> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /**
     * Bulk lock clear, used when a password reset unlocks an account. Written as a
     * modifying query so it cannot be defeated by a stale in-memory copy.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedAttempts = 0, u.lockedUntil = null where u.id = :id")
    void clearLock(@Param("id") UUID id);

    /**
     * Cross-company lookup for platform-level sign-in (SUPER_ADMIN / PLATFORM_ADMIN),
     * which is not scoped to any one company and therefore carries no company slug.
     *
     * <p><b>Deliberately crosses companies.</b> The Hibernate {@code companyFilter} is
     * inactive here because it must be invoked with <em>no company bound</em> — the
     * whole point is to discover the platform admin's home company. It is safe only
     * because the {@code role in :roles} predicate restricts the result to accounts
     * that are already company-unbound platform operators; a normal company user's
     * email never matches, so this cannot be used to enumerate ordinary accounts.
     * Callers must still verify the password through the normal flow.
     */
    @Query("select distinct u from User u join u.roles r where u.email = :email and r in :roles")
    List<User> findPlatformUsersByEmail(@Param("email") String email,
                                        @Param("roles") Collection<Role> roles);

    /**
     * Every account holding one of the platform-tier roles, across all companies.
     *
     * <p><b>Deliberately crosses companies</b>, for the same reason as the lookup above
     * and with the same safeguard: the {@code r in :roles} predicate confines the result
     * to accounts that are company-unbound by definition. It exists so a super admin can
     * see who else holds their own level of access — the one list nobody else may read,
     * which is why the calling service requires {@code SUPER_ADMIN}.
     */
    @Query("select distinct u from User u join u.roles r where r in :roles order by u.email asc")
    List<User> findAllByRoleIn(@Param("roles") Collection<Role> roles);

    /** Whether an address is taken anywhere on the platform, not merely in one company. */
    @Query("select count(u) > 0 from User u where u.email = :email")
    boolean existsByEmailAcrossCompanies(@Param("email") String email);
}
