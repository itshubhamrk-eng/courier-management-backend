package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Platform-level: no company filter applies, the company is an explicit argument. */
public interface CompanyMenuLockRepository extends JpaRepository<CompanyMenuLock, UUID> {

    List<CompanyMenuLock> findAllByCompanyId(UUID companyId);
}
