-- =============================================================================
-- V52 — Pincode-Area links, with per-area ODA
--
-- A single 6-digit pincode routes to several real post offices/localities (India Post's
-- own directory routinely lists 3-20 for one code) — master_pincodes.area_id (unchanged)
-- still names the one Area a pincode primarily routes to, but that discarded the rest.
-- This table keeps every Area a pincode's postal record actually names, so an operator
-- can see them and set ODA per area rather than per pincode: whether a location is
-- Out-of-Delivery-Area, and the surcharge, genuinely varies by locality within one
-- pincode, not by the pincode number itself.
--
-- Same owner as master_pincodes/master_areas (global, GlobalMasters.PLATFORM_COMPANY_ID)
-- — a link row is exactly as global as the two rows it connects.
-- =============================================================================

CREATE TABLE master_pincode_areas (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    pincode_id BINARY(16) NOT NULL,
    area_id    BINARY(16) NOT NULL,

    -- The one row matching master_pincodes.area_id — kept in sync by the application
    -- layer (PincodeAreaService), not a generated column: "primary" is a statement
    -- about which Area the pincode routes to, not something derivable from this row
    -- alone once more than one row can be primary-eligible.
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    oda_applicable BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULL until oda_applicable is set true; the application layer defaults a fresh
    -- 250.00 the moment it is, same as leaving it blank would suggest to an operator.
    oda_amount DECIMAL(10, 2) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pincode_area (company_id, pincode_id, area_id),
    KEY idx_pincode_area_pincode (company_id, pincode_id),

    CONSTRAINT fk_pincode_area_pincode FOREIGN KEY (pincode_id) REFERENCES master_pincodes (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_pincode_area_area FOREIGN KEY (area_id) REFERENCES master_areas (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Every Area a pincode postal record names, one primary, each with its own ODA/amount';
