package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Advances a company's tracking-number counter. Same contract as
 * {@link BranchShipmentSequenceRepository}: {@link #advance} and {@link #nextValue} must
 * run back to back on the same connection within the caller's transaction, and the insert
 * branch wraps its seed value in {@code LAST_INSERT_ID(1)} — without it, MySQL only
 * populates {@code LAST_INSERT_ID()} from the {@code ON DUPLICATE KEY UPDATE} branch, so a
 * company's very first tracking number would silently read back as 0.
 */
public interface CompanyShipmentSequenceRepository extends JpaRepository<CompanyShipmentSequence, UUID> {

    @Modifying
    @Query(value = "INSERT INTO company_shipment_sequences (company_id, sequence_value) VALUES (:companyId, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE sequence_value = LAST_INSERT_ID(sequence_value + 1)", nativeQuery = true)
    void advance(@Param("companyId") byte[] companyId);

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    long nextValue();
}
