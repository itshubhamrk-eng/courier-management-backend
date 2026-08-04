package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Settings of one company. Company-owned; see {@link CompanyRoleRepository} for how the
 * company is bound during provisioning.
 */
public interface CompanySettingRepository extends JpaRepository<CompanySetting, UUID> {

    Optional<CompanySetting> findBySettingKey(String settingKey);

    List<CompanySetting> findAllByCategoryOrderBySettingKeyAsc(String category);

    List<CompanySetting> findAllByOrderByCategoryAscSettingKeyAsc();

    long countByCompanyId(UUID companyId);
}
