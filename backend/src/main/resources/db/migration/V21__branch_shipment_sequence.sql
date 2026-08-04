-- =============================================================================
-- V21 — Branch-wise serial shipment numbers
--
-- Shipment.shipmentNumber used to be a random 6-digit number (ShipmentNumberGenerator,
-- existence-check-and-retry, same contract as every other generator in the project). On
-- direct request the shipment number becomes a per-branch, gapless-under-concurrency
-- serial instead: "<BRANCH_CODE>-<6-digit serial>", e.g. PUNE-000001, PUNE-000002,
-- LATUR-000001. trackingNumber (the AWB) is untouched — still the dated random code from
-- AwbNumberGenerator; only the internal shipment number moves to a counter.
--
-- One row per branch, incremented with MySQL's LAST_INSERT_ID(expr) upsert idiom: the
-- UPDATE branch takes the row's lock so two concurrent bookings at the same branch can
-- never observe or hand out the same value, no application-level locking required. No
-- FK to branches(id) — the project's cross-module references are plain UUID columns
-- throughout (see e.g. shipments.booking_branch_id in V17), not physical FKs.
--
-- Column is sequence_value, not last_value: MySQL 8.0.14+ reserves LAST_VALUE as a
-- window function name, and an unquoted column of that name fails with a syntax error.
-- =============================================================================

CREATE TABLE branch_shipment_sequences (
    branch_id      BINARY(16) NOT NULL,
    sequence_value BIGINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (branch_id)
);
