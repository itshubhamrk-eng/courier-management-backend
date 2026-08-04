package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Advances a branch's shipment-number counter. {@link #advance} and {@link #nextValue}
 * must run back to back on the same connection within the caller's transaction —
 * {@code LAST_INSERT_ID()} is connection-scoped, not row- or session-global, the same
 * MySQL upsert idiom used to hand out a value without a {@code SELECT ... FOR UPDATE}.
 */
public interface BranchShipmentSequenceRepository extends JpaRepository<BranchShipmentSequence, UUID> {

    @Modifying
    @Query(value = "INSERT INTO branch_shipment_sequences (branch_id, sequence_value) VALUES (:branchId, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE sequence_value = LAST_INSERT_ID(sequence_value + 1)", nativeQuery = true)
    void advance(@Param("branchId") byte[] branchId);

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    long nextValue();
}
