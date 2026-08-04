-- =============================================================================
-- V3 — Subscription Plans (Phase 2, Super Admin)
--
-- NOTE ON ORDERING: modules take migration versions in the order they are built,
-- not in the order they were planned. Auth took V2, this takes V3, and the company
-- module takes V4 — which is also where the FK companies.subscription_plan_id and
-- the FK users.company_id are added. Flyway here is forward-only with
-- out-of-order disabled, so a "reserved" gap could never be filled later.
--
-- This table is PLATFORM-LEVEL, not company-owned: the catalogue is shared by
-- every company and written only by SUPER_ADMIN. It therefore has no company_id,
-- and its unique keys are global rather than prefixed with company_id — the
-- prefix rule in MEMORY/ARCHITECTURE.md §4 applies to company-owned tables.
--
-- NULL means UNLIMITED on every quota column. There is no -1 sentinel: a
-- forgotten guard around a sentinel evaluates `current < -1` as "over quota" and
-- silently blocks everything, whereas a forgotten NULL check fails loudly.
-- =============================================================================

CREATE TABLE subscription_plans (
    id                   BINARY(16)     NOT NULL,

    -- Stable machine key, uppercased by the application. Immutable after creation:
    -- companies and invoices reference it, so re-pointing a code at different terms
    -- would rewrite commercial history.
    plan_code            VARCHAR(50)    NOT NULL,
    plan_name            VARCHAR(100)   NOT NULL,
    description          VARCHAR(500)   NULL,

    -- TRIAL | BASIC | STANDARD | PREMIUM | ENTERPRISE. Stored as a string, never an
    -- ordinal, so the enum can be reordered without rewriting rows.
    plan_type            VARCHAR(20)    NOT NULL,

    -- Money is DECIMAL, never a float. Both prices are >= 0, enforced in the domain
    -- and by the CHECK below.
    monthly_price        DECIMAL(19, 4) NOT NULL,
    yearly_price         DECIMAL(19, 4) NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'INR',

    -- Free days granted on signup. A TRIAL plan must have at least 1.
    trial_days           INT            NOT NULL DEFAULT 0,

    -- ---------------------------------------------------------------- quotas
    -- NULL = unlimited on every column below. ENTERPRISE plans are stored with all
    -- of them NULL; the application nulls them on write.
    max_users            INT            NULL,
    max_branches         INT            NULL,
    max_hubs             INT            NULL,
    max_customers        INT            NULL,
    max_drivers          INT            NULL,
    max_vehicles         INT            NULL,
    max_daily_bookings   INT            NULL,
    max_monthly_bookings INT            NULL,
    storage_limit_gb     INT            NULL,
    -- Requests per minute, per company.
    api_rate_limit       INT            NULL,

    -- Feature toggles, e.g. {"bulkBooking": true}. Schemaless on purpose: features
    -- are added far more often than plans, and a BOOLEAN column per feature would
    -- mean a migration for every one of them.
    feature_flags        JSON           NULL,

    -- Whether the plan may be assigned to a NEW company. Deactivating grandfathers
    -- existing subscribers rather than cancelling them.
    is_active            BOOLEAN        NOT NULL DEFAULT TRUE,
    display_order        INT            NOT NULL DEFAULT 0,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    -- Global, and deliberately NOT scoped by `deleted`: a soft-deleted plan keeps
    -- its code and name reserved. Reusing the code of a retired plan would attach a
    -- new price to the identifier that old companies and invoices already point at.
    -- The application checks both keys against soft-deleted rows too, so the caller
    -- gets a 409 naming the field instead of an opaque constraint violation.
    UNIQUE KEY uk_subscription_plans_code (plan_code),
    -- utf8mb4_unicode_ci is case-insensitive, so this also rejects "Standard" when
    -- "STANDARD" exists — matching the LOWER() comparison the application performs.
    UNIQUE KEY uk_subscription_plans_name (plan_name),

    -- Serves the catalogue query: WHERE is_active = TRUE ORDER BY display_order.
    KEY idx_subscription_plans_active_order (is_active, display_order),
    KEY idx_subscription_plans_type (plan_type),
    -- Soft delete is filtered on every read, so it leads the general list index.
    KEY idx_subscription_plans_deleted (deleted, display_order),

    -- Belt and braces behind the domain rules. The application raises a readable
    -- 422 first; these stop a bad row arriving by any other route.
    CONSTRAINT ck_subscription_plans_monthly_price CHECK (monthly_price >= 0),
    CONSTRAINT ck_subscription_plans_yearly_price CHECK (yearly_price >= 0),
    CONSTRAINT ck_subscription_plans_trial_days CHECK (trial_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Platform-wide subscription plan catalogue (SUPER_ADMIN)';
