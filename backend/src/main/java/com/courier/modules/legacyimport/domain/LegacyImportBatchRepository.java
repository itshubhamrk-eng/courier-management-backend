package com.courier.modules.legacyimport.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegacyImportBatchRepository extends JpaRepository<LegacyImportBatch, UUID> {

    @Query("select b from LegacyImportBatch b where b.id = :id and b.companyId = :companyId")
    Optional<LegacyImportBatch> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    List<LegacyImportBatch> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
