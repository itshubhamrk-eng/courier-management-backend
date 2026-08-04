-- =============================================================================
-- V14 — Customer Management
--
-- Customer is reusable master data, company-owned, independent of Shipment Order
-- (see MEMORY/modules/shipment.md and MEMORY/modules/customer.md). One customer has
-- many addresses.
--
-- Permission rows already exist: CUSTOMER_* was seeded in V6, ADDRESS_* likewise — this
-- migration adds no permission rows, only the two tables the existing catalogue and
-- DefaultRoleCatalog grants were seeded ahead of (see the "responsibility list is ahead
-- of the code" note in AI_CONTEXT.md).
--
-- customer_addresses.customer_id carries a real FK to customers.id: both tables are
-- written by this module from day one, unlike the cross-module references below, which
-- follow the project's "no cross-entity FK until the data is stable" pattern:
--   * customer_addresses.country_id / state_id / district_id / city_id / area_id /
--     pincode_id -> the V12 global geography masters. A different module's tables;
--     validated in the service layer, the same treatment branches.manager_id gets.
-- =============================================================================

CREATE TABLE customers (
    id            BINARY(16)   NOT NULL,
    company_id    BINARY(16)   NOT NULL,

    customer_code VARCHAR(50)  NOT NULL,
    -- INDIVIDUAL | BUSINESS
    customer_type VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL',
    company_name  VARCHAR(150) NULL,
    first_name    VARCHAR(100) NOT NULL,
    middle_name   VARCHAR(100) NULL,
    last_name     VARCHAR(100) NOT NULL,

    mobile            VARCHAR(20)  NOT NULL,
    alternate_mobile  VARCHAR(20)  NULL,
    email             VARCHAR(255) NULL,

    gst_number    VARCHAR(15) NULL,
    pan_number    VARCHAR(10) NULL,

    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Never scoped by `deleted` — a customer code may be quoted by a shipment, so it
    -- stays reserved even after a soft delete.
    UNIQUE KEY uk_customers_company_code (company_id, customer_code),

    KEY idx_customers_company_status (company_id, status),
    KEY idx_customers_company_mobile (company_id, mobile),
    KEY idx_customers_company_type (company_id, customer_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Customers: reusable master data, independent of Shipment Order';

CREATE TABLE customer_addresses (
    id          BINARY(16)   NOT NULL,
    company_id  BINARY(16)   NOT NULL,
    customer_id BINARY(16)   NOT NULL,

    -- HOME | OFFICE | WAREHOUSE
    address_type VARCHAR(20) NOT NULL DEFAULT 'HOME',

    country_id  BINARY(16) NULL,
    state_id    BINARY(16) NULL,
    district_id BINARY(16) NULL,
    city_id     BINARY(16) NULL,
    area_id     BINARY(16) NULL,
    pincode_id  BINARY(16) NULL,

    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255) NULL,
    landmark      VARCHAR(150) NULL,

    -- WGS84, ~0.1 m precision.
    latitude    DECIMAL(9, 6) NULL,
    longitude   DECIMAL(9, 6) NULL,

    is_default_pickup   BOOLEAN NOT NULL DEFAULT FALSE,
    is_default_delivery BOOLEAN NOT NULL DEFAULT FALSE,

    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    KEY idx_cust_addr_company_customer (company_id, customer_id),
    KEY idx_cust_addr_company_status (company_id, status),
    KEY idx_cust_addr_pincode (company_id, pincode_id),

    CONSTRAINT fk_cust_addr_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_cust_addr_latitude CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    CONSTRAINT ck_cust_addr_longitude CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Customer addresses: pickup / delivery locations for a customer';
