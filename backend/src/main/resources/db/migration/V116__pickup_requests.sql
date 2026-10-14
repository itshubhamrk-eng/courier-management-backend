-- =============================================================================
-- V116 — Pickup requests (Pickup-Delivery Partner module, phase 2).
--
-- A pickup request is "collect a shipment from this address". Staff create it, assign it to
-- a Pickup-Delivery Partner (a Branch of type PICKUP_DELIVERY_PARTNER), the partner accepts,
-- goes out, collects (recording actual weight/parcels) and the shipment is then booked and
-- linked back. Lifecycle is enforced in the service (PickupStatus). pdp_branch_id and
-- shipment_id are plain ids (no physical FK) — the shipment is booked by another module.
-- =============================================================================

CREATE TABLE pickup_requests (
    id          BINARY(16)  NOT NULL,
    company_id  BINARY(16)  NOT NULL,
    pickup_no   VARCHAR(30) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',

    customer_id    BINARY(16)   NULL,
    contact_name   VARCHAR(150) NOT NULL,
    contact_mobile VARCHAR(20)  NOT NULL,
    address_line   VARCHAR(255) NOT NULL,
    city           VARCHAR(100) NULL,
    district_id    BINARY(16)   NULL,
    pincode        VARCHAR(10)  NOT NULL,

    expected_parcels   INT            NOT NULL DEFAULT 1,
    expected_weight_kg DECIMAL(12, 3) NULL,
    preferred_date     DATE           NULL,
    preferred_slot     VARCHAR(40)    NULL,
    remarks            VARCHAR(500)   NULL,

    pdp_branch_id  BINARY(16) NULL,
    assigned_at    TIMESTAMP(6) NULL,
    accepted_at    TIMESTAMP(6) NULL,
    started_at     TIMESTAMP(6) NULL,
    picked_up_at   TIMESTAMP(6) NULL,
    actual_parcels INT            NULL,
    actual_weight_kg DECIMAL(12, 3) NULL,

    reject_reason  VARCHAR(255) NULL,
    fail_reason    VARCHAR(255) NULL,
    cancel_reason  VARCHAR(255) NULL,

    shipment_id    BINARY(16)  NULL,
    shipment_no    VARCHAR(50) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pickup_no (company_id, pickup_no),
    KEY idx_pickup_company_status (company_id, status),
    KEY idx_pickup_company_pdp (company_id, pdp_branch_id, status),
    KEY idx_pickup_company_shipment (company_id, shipment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Requests to collect a shipment, assigned to a Pickup-Delivery Partner';

-- --- menu: Pickup Requests under the PDP management group (existing PICKUP_* permissions) ---
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), p.id, 'pdp-pickups', 'Pickup Requests', 'inventory_2', '/pdp/pickups', 'PICKUP',
       3, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM menu_items p
WHERE p.code = 'pdp-management' AND p.parent_id IS NULL AND p.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = 'pdp-pickups');
