-- =============================================================================
-- V10 — Branch Wallet (Phase 5, Finance)
--
-- Every branch (vendor) owns exactly one prepaid wallet. The wallet holds only a
-- balance; every change to that balance is an append-only row in
-- wallet_transactions. There is no API that writes a balance directly.
--
-- Company-owned: company_id NOT NULL, leading the composite keys.
--
-- Forward-only. V2 auth, V3 subscription, V4 company, V5 roles, V6 permissions,
-- V7 users, V8 settings, V9 branches, V10 this.
--
-- Money is DECIMAL(19,4) everywhere — never a float. The CHECK constraints are a
-- backstop under the service rules, not a substitute for them: a balance that has
-- gone negative is a bug we want the database to refuse rather than persist.
--
-- No backfill of wallets for branches that already exist. Wallet numbers are
-- generated in Java (time-ordered prefix + random suffix) and SQL cannot produce
-- them; instead WalletService.getOrCreateForBranch creates a missing wallet on
-- first access, so pre-V10 branches acquire one lazily and post-V10 branches get
-- one from the BranchCreated event.
-- =============================================================================

CREATE TABLE wallets (
    id        BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    -- Human-facing wallet identifier, printed on statements. Globally unique like
    -- users.username, not per-company: it is quoted in support tickets across
    -- companies and must resolve to exactly one wallet.
    wallet_number VARCHAR(30) NOT NULL,
    branch_id     BINARY(16)  NOT NULL,

    -- ACTIVE | INACTIVE | SUSPENDED | CLOSED
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Spendable now. hold_balance is money reserved against in-flight work
    -- (a booked-but-not-manifested shipment); it is not spendable and not lost.
    available_balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    hold_balance      DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'INR',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    -- "Every branch owns exactly one wallet", enforced by the database and not
    -- only by the service. Not scoped by `deleted`: a wallet is never deleted.
    UNIQUE KEY uk_wallets_company_branch (company_id, branch_id),
    UNIQUE KEY uk_wallets_number (wallet_number),

    KEY idx_wallets_branch (branch_id),
    KEY idx_wallets_company_status (company_id, status),

    CONSTRAINT fk_wallets_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_wallets_available_non_negative CHECK (available_balance >= 0),
    CONSTRAINT ck_wallets_hold_non_negative CHECK (hold_balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One prepaid wallet per branch';


CREATE TABLE wallet_transactions (
    id        BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,
    wallet_id BINARY(16) NOT NULL,

    transaction_no VARCHAR(40) NOT NULL,

    -- CR (credit, balance up) | DR (debit, balance down)
    transaction_type VARCHAR(2) NOT NULL,
    -- WRC wallet recharge | SBK shipment booking | SRF shipment refund |
    -- COD COD settlement  | COM commission      | BST branch settlement |
    -- MCR manual credit   | MDB manual debit    | TRI transfer in |
    -- TRO transfer out    | ADJ adjustment      | PNL penalty
    sub_transaction_type VARCHAR(10) NOT NULL,

    amount         DECIMAL(19, 4) NOT NULL,
    -- The available balance before and after this entry. Denormalised on purpose:
    -- a statement must be reproducible without replaying the whole ledger, and a
    -- later correction must never silently rewrite history.
    balance_before DECIMAL(19, 4) NOT NULL,
    balance_after  DECIMAL(19, 4) NOT NULL,

    -- PAYMENT | SHIPMENT | SETTLEMENT | SYSTEM | MANUAL
    reference_type VARCHAR(20) NULL,
    reference_id   VARCHAR(100) NULL,

    remarks VARCHAR(500) NULL,

    payment_gateway   VARCHAR(30) NULL,
    payment_reference VARCHAR(100) NULL,
    -- PENDING | SUCCESS | FAILED | REFUNDED. NULL for entries that are not a payment.
    payment_status    VARCHAR(20) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    UNIQUE KEY uk_wallet_txn_no (transaction_no),
    -- Idempotency for gateway settlement. Deliberately NOT scoped by company_id,
    -- unlike every other unique key here: the payment gateway is one merchant
    -- account shared by the whole platform, so a payment id is globally unique at
    -- the gateway. Scoping this per company would let company B present company A's
    -- payment id and be credited for it. MySQL allows repeated NULLs in a unique
    -- index, so non-payment entries are unaffected.
    UNIQUE KEY uk_wallet_txn_payment_ref (payment_reference),

    -- The statement query: one wallet, newest first.
    KEY idx_wallet_txn_wallet_created (wallet_id, created_at),
    KEY idx_wallet_txn_company_created (company_id, created_at),
    KEY idx_wallet_txn_type (company_id, transaction_type, sub_transaction_type),
    -- "What did shipment X cost this branch?"
    KEY idx_wallet_txn_reference (company_id, reference_type, reference_id),

    CONSTRAINT fk_wallet_txn_wallet FOREIGN KEY (wallet_id)
        REFERENCES wallets (id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_wallet_txn_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_wallet_txn_type CHECK (transaction_type IN ('CR', 'DR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Append-only wallet ledger; the only way a wallet balance changes';
