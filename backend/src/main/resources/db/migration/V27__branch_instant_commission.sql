-- =============================================================================
-- V27: branches gain instant_commission, an Operations toggle — when true (the
-- default), a PREPAID booking's branch commission (V26) is credited to the branch
-- wallet the moment the booking debit settles; when false the commission is still
-- computed and stored on the shipment charge, just not auto-credited.
-- =============================================================================

ALTER TABLE branches
    ADD COLUMN instant_commission BOOLEAN NOT NULL DEFAULT TRUE AFTER allow_wallet;
