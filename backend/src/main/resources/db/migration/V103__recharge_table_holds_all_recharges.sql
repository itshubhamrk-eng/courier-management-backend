-- =============================================================================
-- V103 — one table for every recharge, ledger keeps only money that moved
--
-- wallet_recharge_orders (V102) becomes the recharge table for BOTH online (Razorpay) and
-- manual recharges. A recharge row is written first; only once it is SUCCESS is the matching
-- wallet_transactions entry created, so the ledger needs no status of its own:
-- wallet_transactions.payment_status is dropped.
--
-- Existing credited recharges (WRC / MCR ledger rows with no recharge row yet) are
-- backfilled as SUCCESS so the Recharge Report keeps its history.
-- =============================================================================
ALTER TABLE wallet_recharge_orders
    ADD COLUMN mode    VARCHAR(10) NOT NULL DEFAULT 'ONLINE' AFTER currency,
    ADD COLUMN remarks VARCHAR(500) NULL AFTER failure_reason,
    ADD CONSTRAINT ck_recharge_orders_mode CHECK (mode IN ('ONLINE', 'MANUAL'));

INSERT INTO wallet_recharge_orders
    (id, company_id, wallet_id, branch_id, transaction_no, amount, currency, mode, gateway,
     gateway_order_id, gateway_payment_id, status, remarks, ledger_transaction_id, settled_at,
     created_at, created_by, updated_at, updated_by, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), t.company_id, t.wallet_id, w.branch_id, t.transaction_no,
       t.amount, w.currency,
       IF(t.sub_transaction_type = 'WRC', 'ONLINE', 'MANUAL'),
       COALESCE(t.payment_gateway, IF(t.sub_transaction_type = 'WRC', 'RAZORPAY', 'MANUAL')),
       IF(t.sub_transaction_type = 'WRC', t.reference_id, NULL),
       t.payment_reference,
       COALESCE(t.payment_status, 'SUCCESS'),
       t.remarks, t.id, t.created_at,
       t.created_at, t.created_by, t.updated_at, t.updated_by, FALSE, 0
FROM wallet_transactions t
         JOIN wallets w ON w.id = t.wallet_id
WHERE t.sub_transaction_type IN ('WRC', 'MCR')
  AND t.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM wallet_recharge_orders r WHERE r.ledger_transaction_id = t.id);

ALTER TABLE wallet_transactions DROP COLUMN payment_status;
