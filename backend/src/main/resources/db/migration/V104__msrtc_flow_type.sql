-- =============================================================================
-- V104 — MSRTC flow marker on shipments and manifests
--
-- MSRTC reuses the shipment and manifest tables; flow_type tells the two flows apart.
-- Every existing row is STANDARD (the only flow that ever existed), so the current
-- booking / Load Sheet / THC behaviour is unchanged.
-- =============================================================================
ALTER TABLE shipments
    ADD COLUMN flow_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE manifests
    ADD COLUMN flow_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

CREATE INDEX idx_shipments_flow_type ON shipments (company_id, flow_type);
CREATE INDEX idx_manifests_flow_type ON manifests (company_id, flow_type);
