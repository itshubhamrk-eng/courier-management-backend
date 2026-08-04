-- =============================================================================
-- V23 — Wallet Top-up Requests
--
-- Distinct from a recharge (branch pays the platform directly via Razorpay,
-- wallets/wallet_transactions, V10): a branch asks its own company admin to fund the
-- wallet, and nothing moves until the admin approves. On approve, the existing
-- WalletService.credit(...) path writes the wallet_transactions row (sub_transaction_type
-- MCR) and raises the balance — this table only tracks the ask and the decision, never a
-- balance itself. No FK to wallet_transactions is enforced here: transaction_id is set only
-- on approval, after the credit has already committed in the same request.
-- =============================================================================

CREATE TABLE wallet_topup_requests (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    wallet_id BINARY(16) NOT NULL,
    branch_id BINARY(16) NOT NULL,

    requested_amount DECIMAL(19, 4) NOT NULL,
    remarks          VARCHAR(500) NULL,

    -- PENDING | APPROVED | REJECTED. Terminal once decided.
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    requested_by BINARY(16) NOT NULL,

    decided_by       BINARY(16) NULL,
    decided_at       TIMESTAMP(6) NULL,
    decision_remarks VARCHAR(500) NULL,
    -- The wallet_transactions row approval created. Set only when status = APPROVED.
    transaction_id   BINARY(16) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    KEY idx_topup_req_branch_status (company_id, branch_id, status),
    KEY idx_topup_req_company_status (company_id, status, created_at),

    CONSTRAINT fk_topup_req_wallet FOREIGN KEY (wallet_id)
        REFERENCES wallets (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_topup_req_branch FOREIGN KEY (branch_id)
        REFERENCES branches (id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT ck_topup_req_amount_positive CHECK (requested_amount > 0),
    CONSTRAINT ck_topup_req_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- A decided request always carries who/when; a pending one carries neither.
    CONSTRAINT ck_topup_req_decision CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'A branch''s ask to fund its own wallet, decided by a company admin';
