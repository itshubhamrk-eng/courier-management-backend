-- =============================================================================
-- V100 — Reusable shipment numbers
--
-- A cancelled shipment gives its serial back: the cancelled row is renamed
-- "<number>-CXL" (shipments.uk_shipments_company_number stays satisfied, history and the
-- cancelled row itself are kept) and the freed serial lands here. The branch's next
-- booking takes the lowest freed serial before advancing branch_shipment_sequences.
-- =============================================================================

CREATE TABLE released_shipment_numbers (
    branch_id BINARY(16) NOT NULL,
    serial    BIGINT     NOT NULL,
    PRIMARY KEY (branch_id, serial)
);
