package com.courier.modules.legacyimport.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegacyOriginBranchRepository extends JpaRepository<LegacyOriginBranch, UUID> {

    List<LegacyOriginBranch> findAllByCompanyId(UUID companyId);

    Optional<LegacyOriginBranch> findByCompanyIdAndOriginKey(UUID companyId, String originKey);
}
