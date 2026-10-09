-- =============================================================================
-- V102 — wallet_recharge_orders
--
-- One row per online (Razorpay) recharge attempt, written BEFORE the gateway order is
-- created so every attempt has our own transaction number (it is also the gateway receipt).
--   PENDING  order opened / being opened, payment not confirmed yet
--   SUCCESS  payment captured; the wallet was credited (ledger_transaction_id)
--   FAILED   the gateway reported failure, the order was never created, or nothing was
--            paid in time. A later captured payment still settles it (SUCCESS overrides).
-- Deliberately NOT a wallet_transactions row: the ledger only records money that moved.
-- A scheduler re-checks PENDING rows against the gateway (RechargeReconciliationJob).
-- =============================================================================
CREATE TABLE wallet_recharge_orders (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    wallet_id  BINARY(16) NOT NULL,
    branch_id  BINARY(16) NOT NULL,

    transaction_no VARCHAR(40) NOT NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    gateway        VARCHAR(30) NOT NULL,

    gateway_order_id   VARCHAR(100) NULL,
    gateway_payment_id VARCHAR(100) NULL,

    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(255) NULL,

    ledger_transaction_id BINARY(16) NULL,
    last_checked_at       TIMESTAMP(6) NULL,
    settled_at            TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_recharge_orders_txn_no (transaction_no),
    KEY idx_recharge_orders_gateway_order (gateway_order_id),
    KEY idx_recharge_orders_status_created (status, created_at),
    KEY idx_recharge_orders_branch (company_id, branch_id, created_at),

    CONSTRAINT ck_recharge_orders_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_recharge_orders_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Online recharge attempts: created before the gateway order, settled by webhook/browser/scheduler';
