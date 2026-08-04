-- =============================================================================
-- V17 — Shipment Booking
--
-- The core transaction of the platform. A shipment is booked only after Customer
-- (senderCustomerId/receiverCustomerId/senderAddressId/receiverAddressId, V14), Route +
-- Master Data (bookingBranchId/deliveryBranchId/serviceTypeId/packageTypeId/paymentModeId,
-- V11/V15) and the Pricing Engine (com.courier.modules.pricing, no table of its own) have
-- all agreed the booking is valid; a PAID/PREPAID booking additionally debits the booking
-- branch's wallet (com.courier.modules.finance, V10) once the booking itself has committed.
--
-- Five tables, all owned by this module from day one:
--   shipments               the aggregate root
--   shipment_items          one-to-many, FK to shipments.id (this module owns both sides)
--   shipment_charges        one-to-one, the Pricing Engine's own charge breakup, persisted
--   shipment_status_history append-only; this module only ever writes BOOKED and CANCELLED
--                           — the richer status vocabulary below exists for Manifest
--                           Management and later modules, not started here
--   shipment_documents      a document is a name + type + URL; there is no file-storage
--                           backend yet, the same "URL is source of truth" honesty note
--                           features/company's CompanyLogo already carries
--
-- No physical FK to booking_branch_id / delivery_branch_id / sender_customer_id /
-- receiver_customer_id / sender_address_id / receiver_address_id / service_type_id /
-- package_type_id / payment_mode_id: every one of them belongs to a different module
-- (company, customer, master) and is validated in the service layer through that module's
-- own application service — the same cross-feature treatment rate_master.route_id and
-- customer_addresses' geography ids already get.
--
-- One permission row is new: SHIPMENT_UPLOAD (SHIPMENT already had CREATE/READ/UPDATE/
-- DELETE/SEARCH/IMPORT/EXPORT/PRINT/ASSIGN from V6 — the "responsibility list is ahead of
-- the code" pattern this project has flagged before — but no UPLOAD, needed for
-- POST /shipments/{id}/documents). Generated from DefaultPermissionCatalog exactly as
-- V6/V11/V12/V13/V16 were; catalogue total moves 222 -> 223,
-- DefaultPermissionCatalogTest asserts it. COMPANY_ADMIN needs no row here: its permission
-- set is derived from the whole catalogue (DefaultRoleCatalog.ALL_PERMISSION_CODES).
-- BRANCH_MANAGER and BOOKING_OPERATOR — the two roles that already hold SHIPMENT_CREATE —
-- are extended explicitly, the same as V16 extended them with RATE_MASTER_CALCULATE.
-- =============================================================================

CREATE TABLE shipments (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_number  VARCHAR(30) NOT NULL,
    tracking_number  VARCHAR(30) NOT NULL,

    booking_date DATE NOT NULL,

    booking_branch_id  BINARY(16) NOT NULL,
    delivery_branch_id BINARY(16) NOT NULL,

    sender_customer_id   BINARY(16) NOT NULL,
    receiver_customer_id BINARY(16) NOT NULL,
    sender_address_id    BINARY(16) NOT NULL,
    receiver_address_id  BINARY(16) NOT NULL,

    service_type_id BINARY(16) NOT NULL,
    package_type_id BINARY(16) NOT NULL,
    payment_mode_id BINARY(16) NOT NULL,

    -- DOCUMENT | NON_DOCUMENT | CARGO — the content category, independent of package_type
    -- (the container: envelope/box/pallet) and of service_type (the speed sold).
    shipment_type VARCHAR(20) NOT NULL DEFAULT 'NON_DOCUMENT',

    expected_delivery_date DATE NULL,

    actual_weight     DECIMAL(12, 3) NOT NULL,
    volumetric_weight DECIMAL(12, 3) NOT NULL DEFAULT 0,
    chargeable_weight DECIMAL(12, 3) NOT NULL,

    declared_value     DECIMAL(19, 4) NULL,
    number_of_packages INT            NOT NULL DEFAULT 1,

    -- BOOKED | READY_FOR_MANIFEST | MANIFESTED | DISPATCHED | RECEIVED |
    -- OUT_FOR_DELIVERY | DELIVERED | RETURN_INITIATED | RETURNED | CANCELLED.
    -- This module only ever writes BOOKED (on create) and CANCELLED (on cancel); every
    -- other transition belongs to Manifest Management and later modules, not started here.
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',

    remarks VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Never scoped by `deleted` — the same reasoning every other module's own code/number
    -- column follows: a cancelled or otherwise removed shipment's number stays reserved.
    UNIQUE KEY uk_shipments_company_number (company_id, shipment_number),
    UNIQUE KEY uk_shipments_company_tracking (company_id, tracking_number),

    KEY idx_shipments_tracking (tracking_number),
    KEY idx_shipments_status (company_id, status, created_at),
    KEY idx_shipments_booking_branch (company_id, booking_branch_id, status),
    KEY idx_shipments_delivery_branch (company_id, delivery_branch_id, status),
    KEY idx_shipments_sender (company_id, sender_customer_id),
    KEY idx_shipments_receiver (company_id, receiver_customer_id),

    CONSTRAINT ck_shipments_actual_weight CHECK (actual_weight > 0),
    CONSTRAINT ck_shipments_volumetric_weight CHECK (volumetric_weight >= 0),
    CONSTRAINT ck_shipments_chargeable_weight CHECK (chargeable_weight > 0),
    CONSTRAINT ck_shipments_declared_value CHECK (declared_value IS NULL OR declared_value >= 0),
    CONSTRAINT ck_shipments_number_of_packages CHECK (number_of_packages > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Booking: the aggregate root, one row per booked shipment';

CREATE TABLE shipment_items (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id BINARY(16) NOT NULL,

    item_name VARCHAR(150) NOT NULL,
    quantity  INT            NOT NULL DEFAULT 1,
    weight    DECIMAL(12, 3) NOT NULL,

    length_cm DECIMAL(10, 2) NULL,
    width_cm  DECIMAL(10, 2) NULL,
    height_cm DECIMAL(10, 2) NULL,

    declared_value   DECIMAL(19, 4) NULL,
    fragile          BOOLEAN        NOT NULL DEFAULT FALSE,
    dangerous_goods  BOOLEAN        NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_shipment_items_shipment (company_id, shipment_id),

    CONSTRAINT fk_shipment_items_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id)
        ON DELETE RESTRICT,

    CONSTRAINT ck_shipment_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_shipment_items_weight CHECK (weight > 0),
    CONSTRAINT ck_shipment_items_length CHECK (length_cm IS NULL OR length_cm > 0),
    CONSTRAINT ck_shipment_items_width CHECK (width_cm IS NULL OR width_cm > 0),
    CONSTRAINT ck_shipment_items_height CHECK (height_cm IS NULL OR height_cm > 0),
    CONSTRAINT ck_shipment_items_declared_value CHECK (declared_value IS NULL OR declared_value >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Booking: one row per packed item of a shipment';

CREATE TABLE shipment_charges (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id BINARY(16) NOT NULL,

    -- The Pricing Engine's own ChargeType lines (com.courier.modules.pricing), persisted
    -- verbatim at booking time so a later rate or config change never reprices history.
    freight          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    fuel_charge      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    handling_charge  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    oda_charge       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    insurance_charge DECIMAL(19, 4) NOT NULL DEFAULT 0,
    gst_amount       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    discount_amount  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    round_off        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    net_amount       DECIMAL(19, 4) NOT NULL DEFAULT 0,

    -- Informational only — which Route/Rate the Pricing Engine matched, for a support
    -- desk reading the charge breakup later. No FK, same cross-module treatment as above.
    matched_route_id BINARY(16) NULL,
    matched_rate_id  BINARY(16) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- One row per shipment: a re-priced update replaces this row's figures, it never
    -- appends a second one.
    UNIQUE KEY uk_shipment_charges_shipment (company_id, shipment_id),

    CONSTRAINT fk_shipment_charges_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Booking: the Pricing Engine charge breakup, one row per shipment';

CREATE TABLE shipment_status_history (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id     BINARY(16) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20) NULL,
    remarks         VARCHAR(500) NULL,
    changed_by      BINARY(16) NULL,
    changed_at      TIMESTAMP(6) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_shipment_status_history_shipment (company_id, shipment_id, changed_at),

    CONSTRAINT fk_shipment_status_history_shipment FOREIGN KEY (shipment_id)
        REFERENCES shipments (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Booking: append-only status timeline, one row per transition';

CREATE TABLE shipment_documents (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    shipment_id    BINARY(16) NOT NULL,
    -- INVOICE | EWAY_BILL | PACKING_LIST | LR_COPY | POD
    document_type  VARCHAR(20) NOT NULL,
    document_name  VARCHAR(255) NOT NULL,
    -- No file-storage backend exists yet — the URL is the source of truth, the same
    -- honesty note features/company's CompanyLogo already carries.
    document_url   VARCHAR(1000) NOT NULL,
    remarks        VARCHAR(500) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_shipment_documents_shipment (company_id, shipment_id, document_type),

    CONSTRAINT fk_shipment_documents_shipment FOREIGN KEY (shipment_id)
        REFERENCES shipments (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Shipment Booking: uploaded document references (Invoice, E-Way Bill, Packing List, LR Copy, POD)';

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'SHIPMENT_UPLOAD' AS code, 'Upload Shipment Documents' AS name, 'SHIPMENT' AS module,
         'shipment' AS resource, 'UPLOAD' AS action, 141 AS display_order, NULL AS feature_flag
) AS d;

-- BRANCH_MANAGER and BOOKING_OPERATOR already hold SHIPMENT_CREATE/UPDATE (V6) — extend
-- both with the document-upload right, the same booking-desk reasoning V16 used to extend
-- them with RATE_MASTER_CALCULATE. COMPANY_ADMIN needs no row: ALL_PERMISSION_CODES
-- already covers it.
INSERT INTO role_permissions (id, company_id, role_id, permission_id, permission_code,
                              created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')),
       r.company_id,
       r.id,
       p.id,
       p.permission_code,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM company_roles r
         JOIN (
             SELECT 'BRANCH_MANAGER' AS role_code, 'SHIPMENT_UPLOAD' AS permission_code
             UNION ALL SELECT 'BOOKING_OPERATOR', 'SHIPMENT_UPLOAD'
         ) grant_map ON grant_map.role_code = r.role_code
         JOIN permissions p ON p.permission_code = grant_map.permission_code
WHERE r.deleted = FALSE
  AND EXISTS (SELECT 1 FROM role_permissions existing
              WHERE existing.role_id = r.id AND existing.permission_code = 'SHIPMENT_CREATE'
                AND existing.deleted = FALSE)
  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                  WHERE x.role_id = r.id AND x.permission_code = grant_map.permission_code
                    AND x.deleted = FALSE);
