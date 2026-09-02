-- =============================================================================
-- V53 — Pincode Branch Mapping
--
-- Which branch owns delivery/service for a pincode. A branch serves many pincodes;
-- a pincode is served by exactly one branch per company (routing would be ambiguous
-- otherwise) — enforced by the unique key on (company_id, pincode_id) alone, not on
-- the (branch_id, pincode_id) pair.
--
-- Company-owned for real (unlike master_pincode_areas, V52): a Branch is a genuine
-- per-company entity, so this table binds to the caller's own company_id, not the
-- platform reserved id. Pincode itself is still the global master
-- (GlobalMasters.PLATFORM_COMPANY_ID) — the service layer crosses into that binding
-- only to validate/display the pincode, never to store this row under it.
-- =============================================================================

CREATE TABLE branch_pincode_mapping (
    id         BINARY(16) NOT NULL,
    company_id BINARY(16) NOT NULL,

    branch_id  BINARY(16) NOT NULL,
    pincode_id BINARY(16) NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_branch_pincode_pincode (company_id, pincode_id),
    KEY idx_branch_pincode_branch (company_id, branch_id),

    CONSTRAINT fk_branch_pincode_branch FOREIGN KEY (branch_id) REFERENCES branches (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_branch_pincode_pincode FOREIGN KEY (pincode_id) REFERENCES master_pincodes (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Branch <-> pincode service-area mapping — one branch per pincode per company';
