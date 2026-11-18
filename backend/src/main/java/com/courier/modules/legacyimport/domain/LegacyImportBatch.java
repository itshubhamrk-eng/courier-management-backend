package com.courier.modules.legacyimport.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/** One committed legacy consignment import: the file's identity and what it produced. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "legacy_import_batches")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class LegacyImportBatch extends CompanyOwnedEntity {

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "file_hash", nullable = false, updatable = false, length = 64)
    private String fileHash;

    @Column(name = "new_count", nullable = false)
    private int newCount;

    @Column(name = "existing_count", nullable = false)
    private int existingCount;

    @Column(name = "invalid_count", nullable = false)
    private int invalidCount;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMMITTED";
}
