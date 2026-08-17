-- =============================================================================
-- V43 — Index for TicketSlaSweepJob's cross-company sweep query
--
-- TicketRepository.findAllOpenWithPendingSla() runs every 5 minutes
-- (TicketSlaSweepJob, fixedDelay) with NO company_id predicate — every existing
-- tickets index is (company_id, ...)-leading and useless here, so this was a full
-- table scan on every tick, forever, growing with the table.
--
-- Most tickets have sla_resolution_due_at = NULL (SLA is opt-in per company,
-- see V40) so leading on it lets InnoDB skip straight past the NULL prefix to
-- only the SLA-tracked rows before filtering status/notified flags.
-- =============================================================================

CREATE INDEX idx_tickets_sla_sweep ON tickets (sla_resolution_due_at, status);
