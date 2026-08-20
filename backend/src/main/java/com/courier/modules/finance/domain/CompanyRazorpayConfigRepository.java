package com.courier.modules.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * At most one row per company. Company-owned: the Hibernate filter narrows this to the
 * caller's own company on every query.
 */
public interface CompanyRazorpayConfigRepository extends JpaRepository<CompanyRazorpayConfig, UUID> {

    Optional<CompanyRazorpayConfig> findByCompanyId(UUID companyId);
}
