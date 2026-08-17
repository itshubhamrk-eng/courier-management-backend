package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Advances a company's ticket-number counter. {@link #advance} and {@link #nextValue}
 * must run back to back on the same connection within the caller's transaction — same
 * contract as {@code CompanyDrsSequenceRepository}.
 */
public interface CompanyTicketSequenceRepository extends JpaRepository<CompanyTicketSequence, UUID> {

    @Modifying
    @Query(value = "INSERT INTO company_ticket_sequences (company_id, sequence_value) VALUES (:companyId, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE sequence_value = LAST_INSERT_ID(sequence_value + 1)", nativeQuery = true)
    void advance(@Param("companyId") byte[] companyId);

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    long nextValue();
}
