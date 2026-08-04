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
 * One row per branch — the counter behind its shipment numbers ({@code
 * "<BRANCH_CODE>-<serial>"}). Never read or written through JPA directly: every access
 * goes through {@link BranchShipmentSequenceRepository}'s native upsert, so the row's own
 * lock (not application code) is what makes concurrent bookings at the same branch hand
 * out distinct values. This entity exists only so Spring Data has a table to attach the
 * repository to.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branch_shipment_sequences")
public class BranchShipmentSequence {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID branchId;

    @Column(name = "sequence_value", nullable = false)
    private long sequenceValue;
}
