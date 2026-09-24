-- =============================================================================
-- V92 — Manual shipment status override, opt-in per company.
--
-- Lets a COMPANY_ADMIN/BRANCH_MANAGER push a shipment straight to any status by
-- hand (correcting one stuck in the wrong state, or force-tracking a Direct
-- Company Delivery THC's shipments) instead of only through the normal per-
-- screen flow (THC Dispatch / DRS / Deliver). Off by default — see
-- CompanySettings.manualStatusOverrideEnabled's own doc comment for the
-- money-side-effect trade-off this opts into.
--
-- No new permission module: gated by role (COMPANY_ADMIN, BRANCH_MANAGER), the
-- same convention ShipmentServiceImpl.WRITERS already uses for THC dispatch/
-- DRS/deliver/cancel, not a new fine-grained permission code.
--
-- shipment_status_history.manual_override flags a row written by this path —
-- ShipmentServiceImpl.overrideStatus reuses the real service method (deliver/
-- assignOutForDelivery/cancel) when the target is a legal, precondition-met
-- transition (money/wallet side effects still fire correctly); otherwise it
-- falls back to a raw status write with this flag true and no side effects.
-- =============================================================================

ALTER TABLE company_settings_config
    ADD COLUMN manual_status_override_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shipment_status_history
    ADD COLUMN manual_override BOOLEAN NOT NULL DEFAULT FALSE;
