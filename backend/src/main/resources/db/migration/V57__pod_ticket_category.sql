-- =============================================================================
-- V57 — POD Auto Verification: auto-raise a support ticket when a POD lands in
-- REVIEW or FAIL. One shared category covers both outcomes (priority carries
-- the distinction: FAIL -> HIGH, REVIEW -> MEDIUM). Idempotency is enforced in
-- code (TicketService.raiseSystemTicketIfNoneOpen — one open ticket per
-- shipment per category), not a new table, unlike V41's shipment_sla_breaches:
-- POD retries aren't a recurring-sweep problem that needs a permanent ledger
-- of every raise-worthy condition ever seen, so the dedup query reads the
-- tickets table itself.
-- =============================================================================

INSERT INTO ticket_categories (id, name, active, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'POD Verification Issue', TRUE, NOW(6), NOW(6), FALSE, 0
WHERE NOT EXISTS (SELECT 1 FROM ticket_categories WHERE name = 'POD Verification Issue');

-- Supports the new open-ticket-per-shipment-per-category dedup query
-- (TicketRepository.existsOpenByCompanyIdAndRelatedShipmentIdAndCategoryId) —
-- related_shipment_id had no index at all before this.
CREATE INDEX idx_tickets_company_shipment ON tickets (company_id, related_shipment_id);
