package com.courier.modules.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One row per company — the counter behind its DRS numbers ({@code "DRS" + 6-digit
 * serial}). Same shape and same reason as {@link CompanyShipmentSequence}, its own table
 * since a DRS run's pace has nothing to do with the tracking-number series: never read or
 * written through JPA directly, only through {@link CompanyDrsSequenceRepository}'s native
 * upsert.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_drs_sequences")
public class CompanyDrsSequence {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID companyId;

    @Column(name = "sequence_value", nullable = false)
    private long sequenceValue;
}
