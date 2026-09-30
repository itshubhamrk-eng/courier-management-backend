-- =============================================================================
-- V95 — the marketing site's public "Request a Quote"/"Contact Us" forms land
-- as a Ticket (see PublicLeadServiceImpl), same shape V57's POD-issue auto-raise
-- established. Existing categories are all operational-issue-flavoured
-- (Shipment Issue, Delivery Issue, ...); a sales/new-business enquiry needs
-- its own so it doesn't get lost in — or skew triage of — the support queue.
-- =============================================================================

INSERT INTO ticket_categories (id, name, active, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'New Business / Quote Request', TRUE, NOW(6), NOW(6), FALSE, 0
WHERE NOT EXISTS (SELECT 1 FROM ticket_categories WHERE name = 'New Business / Quote Request');
