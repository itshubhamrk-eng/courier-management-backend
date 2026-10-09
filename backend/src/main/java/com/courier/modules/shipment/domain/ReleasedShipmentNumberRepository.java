package com.courier.modules.shipment.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Per-branch pool of shipment serials freed by cancellation. Plain JDBC — the table has no
 * entity; {@link #take} locks the row it hands out so two concurrent bookings never get
 * the same serial.
 */
@Repository
public class ReleasedShipmentNumberRepository {

    private final JdbcTemplate jdbc;

    public ReleasedShipmentNumberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void release(byte[] branchId, long serial) {
        jdbc.update("INSERT IGNORE INTO released_shipment_numbers (branch_id, serial) VALUES (?, ?)",
                branchId, serial);
    }

    /** Lowest freed serial for the branch, removed from the pool; empty when none. */
    public java.util.Optional<Long> take(byte[] branchId) {
        List<Long> rows = jdbc.queryForList(
                "SELECT serial FROM released_shipment_numbers WHERE branch_id = ? ORDER BY serial LIMIT 1 FOR UPDATE",
                Long.class, branchId);
        if (rows.isEmpty()) {
            return java.util.Optional.empty();
        }
        jdbc.update("DELETE FROM released_shipment_numbers WHERE branch_id = ? AND serial = ?", branchId, rows.get(0));
        return java.util.Optional.of(rows.get(0));
    }
}
