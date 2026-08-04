package com.courier.modules.auth.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * The auth module's view of companies.
 *
 * <p>This seam is why authentication could be built before company management. Auth
 * needs exactly two facts — does this company exist, and may it authenticate — and
 * nothing about plans, billing or contact details. Depending on an interface rather
 * than the {@code companies} table kept the two modules independent.
 *
 * <p>The only implementation is {@code CompanyDirectory} in {@code modules/company}.
 * The former {@code StandaloneCompanyDirectory} placeholder — which treated any id
 * owning at least one user as active, and so could not enforce company status — has
 * been deleted. A fallback that silently ignores suspension is worse than a startup
 * failure if the real bean ever goes missing.
 */
public interface CompanyDirectoryPort {

    Optional<CompanyRef> findById(UUID companyId);

    /** Resolves a company by its {@code companyCode}, the key typed at login. */
    Optional<CompanyRef> findByCode(String companyCode);

    /**
     * Minimal projection of a company.
     *
     * @param id     the company's ownership key — what the JWT carries, and what every
     *               row the company owns is stamped with
     * @param code   {@code companyCode}, the key typed at login
     * @param active whether the company may authenticate right now
     * @param name   display name, carried into the session so the signed-in UI can
     *               render the company's own brand instead of a generic app name
     * @param logo   logo URL, same reason as {@code name}; null when not set
     */
    record CompanyRef(UUID id, String code, boolean active, String name, String logo) {
    }
}
