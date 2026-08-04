-- =============================================================================
-- V19 — Shipment Movement, plus the minimal Manifest module it needs underneath it
--
-- The Shipment Movement brief assumes a Manifest already exists (Create Manifest ->
-- Assign Vehicle -> OUT_SCAN -> DISPATCH). Nothing in this codebase builds one yet —
-- Shipment Booking's own memory says so explicitly ("do not start Manifest Management
-- next") and the backlog's next item was Hub/Serviceability, not Manifest. Confirmed
-- with the user before writing this migration: build just enough Manifest underneath
-- Shipment Movement (create, attach shipments, assign vehicle+driver at dispatch) rather
-- than stopping, or faking a bare manifest_id column with nothing behind it.
--
-- shipment_status_history/shipments already exist (V17). No third table for status
-- history — this reuses the one Shipment Booking already built, widened with three
-- columns (branch/manifest/vehicle) the movement steps need to record. No new table for
-- delivery, either: delivery_assignment is genuinely new (nothing before this module
-- tracked "who is delivering this, and did they").
--
-- ShipmentStatus gains a real rename, not just new values: MANIFESTED -> MANIFEST_CREATED,
-- RECEIVED -> IN_SCAN (matching the brief's exact vocabulary), plus a brand-new OUT_SCAN
-- state the original ten-state graph never had (see ShipmentStatus.java). Safe as a bare
-- rename with no data migration: nothing has ever transitioned a shipment past BOOKED
-- (Shipment Booking's own verification notes this explicitly), so no row anywhere holds
-- the old values — the two defensive UPDATEs below are a no-op today and only exist so a
-- future re-run against a database that somehow does have one of the old values doesn't
-- leave it stranded outside the new enum.
--
-- Permissions: no new migration. DefaultPermissionCatalog already seeded MANIFEST
-- (CREATE/READ/UPDATE/DELETE/SEARCH/PRINT/EXPORT/ASSIGN/DISPATCH/RECEIVE), TRACKING
-- (CREATE/READ/SEARCH/EXPORT), DELIVERY (CREATE/READ/UPDATE/SEARCH/ASSIGN/UPLOAD/DISPATCH/
-- DELIVER) and VEHICLE (full CRUD + ASSIGN + ACTIVATE/DEACTIVATE) rows ahead of any
-- service behind them — the "responsibility list is ahead of the code" pattern this
-- project has hit on every prior module, this time already covering the exact shape this
-- module needs: MANIFEST_DISPATCH/MANIFEST_RECEIVE line up with the OUT_SCAN/DISPATCH/
-- IN_SCAN steps, DELIVERY_DISPATCH/DELIVERY_DELIVER with OUT_FOR_DELIVERY/DELIVER. RBAC
-- stays role-based regardless (`hasAnyRole`, not permission codes), matching every module
-- ahead of the authorise-on-permissions capstone, so this reuse changes no runtime
-- behaviour today either way.
-- =============================================================================

-- ---------------------------------------------------------------- status vocabulary

UPDATE shipments SET status = 'MANIFEST_CREATED' WHERE status = 'MANIFESTED';
UPDATE shipments SET status = 'IN_SCAN' WHERE status = 'RECEIVED';
UPDATE shipments SET status = 'RETURNED' WHERE status = 'RETURN_INITIATED';
UPDATE shipment_status_history SET status = 'MANIFEST_CREATED' WHERE status = 'MANIFESTED';
UPDATE shipment_status_history SET status = 'IN_SCAN' WHERE status = 'RECEIVED';
UPDATE shipment_status_history SET status = 'RETURNED' WHERE status = 'RETURN_INITIATED';
UPDATE shipment_status_history SET previous_status = 'MANIFEST_CREATED' WHERE previous_status = 'MANIFESTED';
UPDATE shipment_status_history SET previous_status = 'IN_SCAN' WHERE previous_status = 'RECEIVED';
UPDATE shipment_status_history SET previous_status = 'RETURNED' WHERE previous_status = 'RETURN_INITIATED';

-- ---------------------------------------------------------------- vehicles (fleet)

CREATE TABLE vehicles (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    vehicle_number VARCHAR(30)  NOT NULL,
    vehicle_type_id BINARY(16) NULL,
    capacity_kg    DECIMAL(12,3) NULL,
    -- ACTIVE | INACTIVE
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    remarks        VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicles_company_number (company_id, vehicle_number),
    KEY idx_vehicles_company_status (company_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Movement: the fleet a manifest may be dispatched with';

-- ---------------------------------------------------------------- manifests

CREATE TABLE manifests (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    manifest_number    VARCHAR(30) NOT NULL,
    booking_branch_id  BINARY(16)  NOT NULL,
    delivery_branch_id BINARY(16)  NOT NULL,
    vehicle_id         BINARY(16)  NULL,
    driver_user_id     BINARY(16)  NULL,
    -- CREATED | DISPATCHED | COMPLETED
    status             VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    dispatched_at      TIMESTAMP(6) NULL,
    completed_at       TIMESTAMP(6) NULL,
    remarks            VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_manifests_company_number (company_id, manifest_number),
    KEY idx_manifests_company_status (company_id, status, created_at),
    KEY idx_manifests_booking_branch (company_id, booking_branch_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Movement: a manifest groups shipments travelling booking-branch -> delivery-branch on one run';

-- shipments.manifest_id — no physical FK, same cross-module treatment every other id on
-- this table already gets (booking_branch_id, service_type_id, ...): manifests is a
-- different module, validated in the service layer, not at the database.
ALTER TABLE shipments
    ADD COLUMN manifest_id BINARY(16) NULL AFTER delivery_branch_id;

CREATE INDEX idx_shipments_manifest ON shipments (company_id, manifest_id);

-- shipment_status_history gains the three columns the movement steps need to record:
-- which branch the scan/transition happened at, which manifest it belongs to, and which
-- vehicle carried it — none of which Shipment Booking's own BOOKED/CANCELLED writes ever
-- needed. All three nullable: a BOOKED entry has none of them.
ALTER TABLE shipment_status_history
    ADD COLUMN branch_id   BINARY(16) NULL AFTER shipment_id,
    ADD COLUMN manifest_id BINARY(16) NULL AFTER branch_id,
    ADD COLUMN vehicle_id  BINARY(16) NULL AFTER manifest_id;

-- ---------------------------------------------------------------- delivery_assignment

CREATE TABLE delivery_assignment (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id        BINARY(16)  NOT NULL,
    delivery_branch_id BINARY(16)  NOT NULL,
    delivery_user_id   BINARY(16)  NOT NULL,
    assigned_at        TIMESTAMP(6) NOT NULL,
    -- ASSIGNED | DELIVERED
    status              VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',

    delivered_at     TIMESTAMP(6) NULL,
    receiver_name    VARCHAR(150) NULL,
    delivery_remarks VARCHAR(500) NULL,
    otp              VARCHAR(10)  NULL,
    signature_url    VARCHAR(1000) NULL,
    photo_url        VARCHAR(1000) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- One live assignment per shipment — a re-assign before delivery updates this row
    -- rather than appending a second (the append-only record of *what happened* is
    -- shipment_status_history; this row is *current state*, the same split
    -- finance.domain.Wallet/WalletTransaction already draws between balance and ledger).
    UNIQUE KEY uk_delivery_assignment_company_shipment (company_id, shipment_id),
    KEY idx_delivery_assignment_branch (company_id, delivery_branch_id, status),
    KEY idx_delivery_assignment_user (company_id, delivery_user_id, status),

    CONSTRAINT fk_delivery_assignment_shipment FOREIGN KEY (shipment_id)
        REFERENCES shipments (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Movement: current delivery-desk assignment and proof of delivery, one row per shipment';
