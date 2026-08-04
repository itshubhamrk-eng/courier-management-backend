-- =============================================================================
-- V22 — 11-digit AWB tracking numbers: YYMM + 7-digit company-wide sequence
--
-- On direct request, the tracking number moves off AwbNumberGenerator's dated-random
-- format ("AWB260803K4M9PX2A7B") onto a plain 11-digit number: 4-digit YYMM + a 7-digit,
-- zero-padded, company-wide counter (e.g. 260800000001) — one running series per company,
-- shared across every branch, unlike shipmentNumber's per-branch counter (V21). Same
-- upsert idiom, its own table: a company and a branch are different scopes, and folding
-- this into branch_shipment_sequences would make one branch's booking pace advance a
-- number that has nothing to do with it.
-- =============================================================================

CREATE TABLE company_shipment_sequences (
    company_id     BINARY(16) NOT NULL,
    sequence_value BIGINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id)
);
