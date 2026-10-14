-- =============================================================================
-- V112 — bank settlement tracking on online recharges
--
-- wallet_recharge_orders.settled_at (V102) is when the WALLET was credited. These columns
-- are a separate, later fact: when Razorpay paid that captured money out to the company's
-- bank account. Filled by RechargeSettlementJob from Razorpay's settlement recon API;
-- NULL until the payment appears in a settlement.
-- =============================================================================
ALTER TABLE wallet_recharge_orders
    ADD COLUMN bank_settlement_id         VARCHAR(100) NULL AFTER settled_at,
    ADD COLUMN bank_settlement_utr        VARCHAR(100) NULL AFTER bank_settlement_id,
    ADD COLUMN bank_settled_at            TIMESTAMP(6) NULL AFTER bank_settlement_utr,
    ADD COLUMN settlement_last_checked_at TIMESTAMP(6) NULL AFTER bank_settled_at,
    ADD KEY idx_recharge_orders_bank_settlement (bank_settlement_id);
