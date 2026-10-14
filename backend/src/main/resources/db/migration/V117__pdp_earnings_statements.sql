-- =============================================================================
-- V117 — Pickup-Delivery Partner earnings and billing statements (phase 3).
--
-- pdp_earnings: one row per job a partner is paid for — a PICKUP (when the pickup is booked)
-- or a DELIVERY (when the delivered shipment's POD is approved). Accrued only: nothing here
-- ever touches the partner's wallet. The amount and every input are SNAPSHOTTED so a later
-- rate-card edit never changes a past earning. UNRATED = no live rate card at the time
-- (amount 0, re-priced by an admin once a card exists). Unique (type, source) = a job is
-- never paid twice.
--
-- pdp_statements: a billing statement per partner per period, built from ACCRUED earnings
-- (which become BILLED and point at it). A DRAFT statement can be deleted, releasing them.
-- =============================================================================

CREATE TABLE pdp_statements (
    id           BINARY(16)  NOT NULL,
    company_id   BINARY(16)  NOT NULL,
    statement_no VARCHAR(30) NOT NULL,
    branch_id    BINARY(16)  NOT NULL,
    period_from  DATE NOT NULL,
    period_to    DATE NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    pickup_count     INT NOT NULL DEFAULT 0,
    delivery_count   INT NOT NULL DEFAULT 0,
    pickup_total     DECIMAL(14, 2) NOT NULL DEFAULT 0,
    delivery_total   DECIMAL(14, 2) NOT NULL DEFAULT 0,
    net_payable      DECIMAL(14, 2) NOT NULL DEFAULT 0,

    approved_at  TIMESTAMP(6) NULL,
    paid_on      DATE NULL,
    payment_ref  VARCHAR(100) NULL,
    remarks      VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pdp_statement_no (company_id, statement_no),
    KEY idx_pdp_statement_branch (company_id, branch_id, status),
    CONSTRAINT fk_pdp_statement_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Billing statement of a Pickup-Delivery Partner for a period';

CREATE TABLE pdp_earnings (
    id            BINARY(16)  NOT NULL,
    company_id    BINARY(16)  NOT NULL,
    branch_id     BINARY(16)  NOT NULL,
    earning_type  VARCHAR(20) NOT NULL,
    -- pickup_requests.id for PICKUP, shipments.id for DELIVERY (plain ids, no FK: other modules own them)
    source_id     BINARY(16)  NOT NULL,
    reference_no  VARCHAR(50) NOT NULL,
    earned_on     DATE NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACCRUED',

    pincode            VARCHAR(10) NULL,
    district_id        BINARY(16)  NULL,
    actual_weight_kg   DECIMAL(12, 3) NULL,
    chargeable_weight_kg DECIMAL(12, 3) NULL,
    rate_id            BINARY(16)  NULL,
    rate_per_kg        DECIMAL(12, 2) NULL,
    weight_charge      DECIMAL(14, 2) NOT NULL DEFAULT 0,
    mathadi_charge     DECIMAL(14, 2) NOT NULL DEFAULT 0,
    oda_charge         DECIMAL(14, 2) NOT NULL DEFAULT 0,
    total_amount       DECIMAL(14, 2) NOT NULL DEFAULT 0,
    remark             VARCHAR(255) NULL,

    statement_id  BINARY(16) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pdp_earning_source (company_id, earning_type, source_id),
    KEY idx_pdp_earning_branch (company_id, branch_id, status, earned_on),
    KEY idx_pdp_earning_statement (company_id, statement_id),
    CONSTRAINT fk_pdp_earning_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Accrued (never wallet-credited) earnings of a Pickup-Delivery Partner';

-- --- menu: Earnings + Billing under the PDP group (PDP_BILLING permissions, V115) -------
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), p.id, d.code, d.title, d.icon, d.route, 'PDP_BILLING',
       d.display_order, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM menu_items p
JOIN (SELECT 'pdp-earnings' AS code, 'Partner Earnings' AS title, 'payments' AS icon, '/pdp/earnings' AS route, 4 AS display_order
      UNION ALL SELECT 'pdp-billing', 'Partner Billing', 'receipt_long', '/pdp/billing', 5) d
WHERE p.code = 'pdp-management' AND p.parent_id IS NULL AND p.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = d.code);
