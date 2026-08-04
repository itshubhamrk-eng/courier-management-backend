-- =============================================================================
-- V11 — Master Data (Phase 6)
--
-- Twelve reference tables a courier company configures before it can book
-- anything: the geography hierarchy (country -> state -> district -> city ->
-- area -> pincode), the operational catalogues (vehicle / package / service
-- type, payment mode, weight slab) and the route master.
--
-- All company-owned. company_id NOT NULL and it leads every unique key and every
-- composite index, per MEMORY/ARCHITECTURE.md §4. Two couriers may each define
-- a "BIKE" vehicle type; neither can see the other's.
--
-- Every table carries the same head — code / name / description / status /
-- display_order — plus the BaseEntity audit, soft-delete and version columns.
-- The shared shape is what lets one AbstractMasterDataService serve all twelve.
--
-- Forward-only. V9 branches, V10 branch wallet, V11 this.
--
-- FK policy: within this module the hierarchy FKs are real and RESTRICT, because
-- every row here is created by this module and nothing predates it. FKs that
-- would reach *out* of the module are deliberately left off, following the
-- project's existing pattern:
--   * master_routes.booking_branch_id / delivery_branch_id -> branches.id — the
--     service validates both against the branch directory. The FK is omitted for
--     the same reason branches.manager_id -> users.id is: cross-module
--     references are still settling, and a constraint added now would fail on the
--     dev database's test rows.
-- =============================================================================


-- --- geography: country ------------------------------------------------------

CREATE TABLE master_countries (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    iso_code2     VARCHAR(2)   NULL,
    iso_code3     VARCHAR(3)   NULL,
    dial_code     VARCHAR(8)   NULL,
    currency_code VARCHAR(3)   NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- Not scoped by `deleted`: a soft-deleted country keeps its code reserved, so
    -- historical shipments that name it can never be re-pointed at a new row.
    UNIQUE KEY uk_master_countries_code (company_id, code),
    UNIQUE KEY uk_master_countries_name (company_id, name),
    KEY idx_master_countries_status (company_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: countries';


-- --- geography: state --------------------------------------------------------

CREATE TABLE master_states (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    country_id    BINARY(16)   NOT NULL,
    -- India's two-digit GST state code, kept as text so a leading zero survives.
    gst_state_code VARCHAR(4)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_states_code (company_id, code),
    -- Name is unique within its parent, not within the company: two countries may
    -- both have a "Western Province".
    UNIQUE KEY uk_master_states_name (company_id, country_id, name),
    KEY idx_master_states_country (company_id, country_id),
    KEY idx_master_states_status (company_id, status),

    CONSTRAINT fk_master_states_country FOREIGN KEY (country_id)
        REFERENCES master_countries (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: states, within a country';


-- --- geography: district -----------------------------------------------------

CREATE TABLE master_districts (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    state_id      BINARY(16)   NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_districts_code (company_id, code),
    UNIQUE KEY uk_master_districts_name (company_id, state_id, name),
    KEY idx_master_districts_state (company_id, state_id),
    KEY idx_master_districts_status (company_id, status),

    CONSTRAINT fk_master_districts_state FOREIGN KEY (state_id)
        REFERENCES master_states (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: districts, within a state';


-- --- geography: city ---------------------------------------------------------

CREATE TABLE master_cities (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    district_id   BINARY(16)   NOT NULL,
    is_metro      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Optional commercial tiering used by rate cards later: TIER_1..TIER_4.
    city_tier     VARCHAR(10)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_cities_code (company_id, code),
    UNIQUE KEY uk_master_cities_name (company_id, district_id, name),
    KEY idx_master_cities_district (company_id, district_id),
    KEY idx_master_cities_status (company_id, status),

    CONSTRAINT fk_master_cities_district FOREIGN KEY (district_id)
        REFERENCES master_districts (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: cities, within a district';


-- --- geography: area ---------------------------------------------------------

CREATE TABLE master_areas (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    -- "One Area belongs to one City" — a single column, not a join table.
    city_id       BINARY(16)   NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_areas_code (company_id, code),
    UNIQUE KEY uk_master_areas_name (company_id, city_id, name),
    KEY idx_master_areas_city (company_id, city_id),
    KEY idx_master_areas_status (company_id, status),

    CONSTRAINT fk_master_areas_city FOREIGN KEY (city_id)
        REFERENCES master_cities (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: areas / localities, within a city';


-- --- geography: pincode ------------------------------------------------------

CREATE TABLE master_pincodes (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    -- `code` is the pincode itself; `name` is the post office / locality label.
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    -- "One Pincode belongs to one Area."
    area_id       BINARY(16)   NOT NULL,

    -- Serviceability, read on every booking.
    serviceable        BOOLEAN NOT NULL DEFAULT TRUE,
    cod_available      BOOLEAN NOT NULL DEFAULT TRUE,
    prepaid_available  BOOLEAN NOT NULL DEFAULT TRUE,
    pickup_available   BOOLEAN NOT NULL DEFAULT TRUE,
    -- Delivery zone used by rate cards: A..E, or LOCAL / ZONAL / NATIONAL.
    zone          VARCHAR(20)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    -- The pincode value is the key. One pincode exists once per company, and it
    -- resolves to exactly one area.
    UNIQUE KEY uk_master_pincodes_code (company_id, code),
    KEY idx_master_pincodes_area (company_id, area_id),
    KEY idx_master_pincodes_status (company_id, status),
    KEY idx_master_pincodes_serviceable (company_id, serviceable),

    CONSTRAINT fk_master_pincodes_area FOREIGN KEY (area_id)
        REFERENCES master_areas (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: pincodes, one per area';


-- --- catalogue: vehicle type -------------------------------------------------

CREATE TABLE master_vehicle_types (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    capacity_kg          DECIMAL(12, 3) NULL,
    capacity_cft         DECIMAL(12, 3) NULL,
    wheel_count          INT            NULL,
    requires_permit      BOOLEAN NOT NULL DEFAULT FALSE,
    -- Weight is DECIMAL, never double: MEMORY/ARCHITECTURE.md §4.

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_vehicle_types_code (company_id, code),
    UNIQUE KEY uk_master_vehicle_types_name (company_id, name),
    KEY idx_master_vehicle_types_status (company_id, status),

    CONSTRAINT ck_master_vehicle_types_capacity
        CHECK (capacity_kg IS NULL OR capacity_kg > 0),
    CONSTRAINT ck_master_vehicle_types_volume
        CHECK (capacity_cft IS NULL OR capacity_cft > 0),
    CONSTRAINT ck_master_vehicle_types_wheels
        CHECK (wheel_count IS NULL OR wheel_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: vehicle types (BIKE, AUTO, PICKUP, TRUCK, CONTAINER)';


-- --- catalogue: package type -------------------------------------------------

CREATE TABLE master_package_types (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    is_document       BOOLEAN NOT NULL DEFAULT FALSE,
    fragile_by_default BOOLEAN NOT NULL DEFAULT FALSE,
    max_weight_kg     DECIMAL(12, 3) NULL,
    default_length_cm DECIMAL(10, 2) NULL,
    default_width_cm  DECIMAL(10, 2) NULL,
    default_height_cm DECIMAL(10, 2) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_package_types_code (company_id, code),
    UNIQUE KEY uk_master_package_types_name (company_id, name),
    KEY idx_master_package_types_status (company_id, status),

    CONSTRAINT ck_master_package_types_weight
        CHECK (max_weight_kg IS NULL OR max_weight_kg > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: package types (DOCUMENT, PARCEL, BOX, BAG, PALLET)';


-- --- catalogue: service type -------------------------------------------------

CREATE TABLE master_service_types (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    -- Promised transit, in days. 0 means same day.
    delivery_days INT          NULL,
    is_express    BOOLEAN NOT NULL DEFAULT FALSE,
    -- Last time of day a booking still makes today's promise.
    cutoff_time   TIME         NULL,
    priority      INT     NOT NULL DEFAULT 0,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_service_types_code (company_id, code),
    UNIQUE KEY uk_master_service_types_name (company_id, name),
    KEY idx_master_service_types_status (company_id, status),

    CONSTRAINT ck_master_service_types_days
        CHECK (delivery_days IS NULL OR delivery_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: service types (STANDARD, EXPRESS, SAME_DAY, ECONOMY)';


-- --- catalogue: payment mode -------------------------------------------------

CREATE TABLE master_payment_modes (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    -- Who pays, and when. PAID collects at booking; TO_PAY and COD at delivery;
    -- TBB (to be billed) settles against a credit account on an invoice.
    collect_at_booking      BOOLEAN NOT NULL DEFAULT FALSE,
    collect_at_delivery     BOOLEAN NOT NULL DEFAULT FALSE,
    requires_credit_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_cash_on_delivery     BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_payment_modes_code (company_id, code),
    UNIQUE KEY uk_master_payment_modes_name (company_id, name),
    KEY idx_master_payment_modes_status (company_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: payment modes (PAID, TO_PAY, TBB, COD)';


-- --- catalogue: weight slab --------------------------------------------------

CREATE TABLE master_weight_slabs (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    -- Half-open interval [min, max): a 1.000 kg parcel falls in 1.000-5.000, not
    -- in 0.000-1.000. The service enforces that active slabs of one unit never
    -- overlap, which the database cannot express.
    min_weight    DECIMAL(12, 3) NOT NULL,
    max_weight    DECIMAL(12, 3) NOT NULL,
    weight_unit   VARCHAR(10)    NOT NULL DEFAULT 'KG',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_weight_slabs_code (company_id, code),
    UNIQUE KEY uk_master_weight_slabs_name (company_id, name),
    KEY idx_master_weight_slabs_status (company_id, status),
    KEY idx_master_weight_slabs_range (company_id, weight_unit, min_weight, max_weight),

    CONSTRAINT ck_master_weight_slabs_min CHECK (min_weight >= 0),
    CONSTRAINT ck_master_weight_slabs_range CHECK (max_weight > min_weight)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: weight slabs, [min, max) per unit';


-- --- route master ------------------------------------------------------------

CREATE TABLE master_routes (
    id            BINARY(16)   NOT NULL,
    company_id     BINARY(16)   NOT NULL,

    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order INT          NOT NULL DEFAULT 0,

    booking_branch_id  BINARY(16) NOT NULL,
    delivery_branch_id BINARY(16) NOT NULL,
    distance_km        DECIMAL(10, 2) NULL,
    transit_days       INT            NOT NULL DEFAULT 1,
    -- Free-text list of intermediate points, e.g. "Nashik, Dhule".
    via                VARCHAR(255)   NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_master_routes_code (company_id, code),
    -- One route per ordered branch pair. A -> B and B -> A are different routes:
    -- the distance may be equal but the transit rarely is.
    UNIQUE KEY uk_master_routes_pair (company_id, booking_branch_id, delivery_branch_id),
    KEY idx_master_routes_booking (company_id, booking_branch_id),
    KEY idx_master_routes_delivery (company_id, delivery_branch_id),
    KEY idx_master_routes_status (company_id, status),

    CONSTRAINT ck_master_routes_distinct_branches
        CHECK (booking_branch_id <> delivery_branch_id),
    CONSTRAINT ck_master_routes_distance
        CHECK (distance_km IS NULL OR distance_km >= 0),
    CONSTRAINT ck_master_routes_transit CHECK (transit_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Master data: routes, booking branch -> delivery branch';


-- --- seed: 13 further system permissions -------------------------------------
-- Nine for the new MASTER_DATA module, and the ACTIVATE/DEACTIVATE pair that
-- PINCODE and ROUTE_MASTER were missing — both now have activate/deactivate
-- endpoints, and a right that cannot be granted is a right that does not exist.
--
-- Generated from DefaultPermissionCatalog exactly as V6's 174 were; the
-- catalogue's total moves 174 -> 187 and DefaultPermissionCatalogTest asserts it.
--
-- Existing companies keep the roles they already have: grants are rows in
-- role_permissions, and back-filling every company's COMPANY_ADMIN here would
-- silently widen roles an administrator may have deliberately trimmed. New
-- companies pick these up automatically, because DefaultRoleCatalog derives
-- COMPANY_ADMIN's set from the catalogue rather than listing it.

INSERT INTO permissions (id, permission_code, permission_name, module, resource, action,
                         display_order, required_feature_flag, is_system_permission, status,
                         created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.code, d.name, d.module, d.resource, d.action,
       d.display_order, d.feature_flag, TRUE, 'ACTIVE',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM (
  SELECT 'MASTER_DATA_CREATE' AS code, 'Create Master Data' AS name, 'MASTER_DATA' AS module, 'master-data' AS resource, 'CREATE' AS action, 106 AS display_order, NULL AS feature_flag
  UNION ALL SELECT 'MASTER_DATA_READ', 'Read Master Data', 'MASTER_DATA', 'master-data', 'READ', 107, NULL
  UNION ALL SELECT 'MASTER_DATA_UPDATE', 'Update Master Data', 'MASTER_DATA', 'master-data', 'UPDATE', 108, NULL
  UNION ALL SELECT 'MASTER_DATA_DELETE', 'Delete Master Data', 'MASTER_DATA', 'master-data', 'DELETE', 109, NULL
  UNION ALL SELECT 'MASTER_DATA_SEARCH', 'Search Master Data', 'MASTER_DATA', 'master-data', 'SEARCH', 110, NULL
  UNION ALL SELECT 'MASTER_DATA_EXPORT', 'Export Master Data', 'MASTER_DATA', 'master-data', 'EXPORT', 111, NULL
  UNION ALL SELECT 'MASTER_DATA_IMPORT', 'Import Master Data', 'MASTER_DATA', 'master-data', 'IMPORT', 112, NULL
  UNION ALL SELECT 'MASTER_DATA_ACTIVATE', 'Activate Master Data', 'MASTER_DATA', 'master-data', 'ACTIVATE', 119, NULL
  UNION ALL SELECT 'MASTER_DATA_DEACTIVATE', 'Deactivate Master Data', 'MASTER_DATA', 'master-data', 'DEACTIVATE', 120, NULL
  UNION ALL SELECT 'PINCODE_ACTIVATE', 'Activate Pincodes', 'PINCODE', 'pincode', 'ACTIVATE', 114, NULL
  UNION ALL SELECT 'PINCODE_DEACTIVATE', 'Deactivate Pincodes', 'PINCODE', 'pincode', 'DEACTIVATE', 115, NULL
  UNION ALL SELECT 'ROUTE_MASTER_ACTIVATE', 'Activate Route Master', 'ROUTE_MASTER', 'route-master', 'ACTIVATE', 134, NULL
  UNION ALL SELECT 'ROUTE_MASTER_DEACTIVATE', 'Deactivate Route Master', 'ROUTE_MASTER', 'route-master', 'DEACTIVATE', 135, NULL
) AS d;
