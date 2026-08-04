package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * The one settings row per company.
 *
 * <p>Company-owned: {@code findByCompanyId} relies on the caller's company being bound, and
 * the Hibernate filter narrows it anyway. {@code JpaSpecificationExecutor} backs the
 * platform-level reporting query in {@link CompanySettingsSpecifications} — a super admin
 * asking "which companies have COD enabled?" — which is the only search this single-row
 * table has a use for.
 */
public interface CompanySettingsRepository
        extends JpaRepository<CompanySettings, UUID>, JpaSpecificationExecutor<CompanySettings> {

    Optional<CompanySettings> findByCompanyId(UUID companyId);

    boolean existsByCompanyId(UUID companyId);
}
