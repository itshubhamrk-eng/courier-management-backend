-- =============================================================================
-- V76 — Lower company-level default chargeable weight to 15kg
--
-- V45 set the booking item grid's per-row weight prefill to 20kg. Business now wants
-- 15kg as the out-of-the-box default. Still company-configurable/editable via Company
-- Settings — only the shipped default changes. Existing rows are only touched when
-- still at the old 20.000 default, so any company that already edited this setting
-- keeps its own value.
-- =============================================================================

ALTER TABLE company_settings_config
    ALTER COLUMN default_chargeable_weight_kg SET DEFAULT 15.000;

UPDATE company_settings_config
SET default_chargeable_weight_kg = 15.000
WHERE default_chargeable_weight_kg = 20.000;
