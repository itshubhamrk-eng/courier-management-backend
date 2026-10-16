-- =============================================================================
-- V125 — MSRTC structure: Division -> Depot -> Station
--
-- bus_divisions: the 30 MSRTC divisions (company-owned master). Each bus_depots row
-- (depot or station) belongs to one division (division_id, nullable for legacy rows).
-- A station may name the depot that serves it (parent_depot_id, self FK; the service
-- only accepts a BUS_DEPOT of the same division).
-- Backfill: rows imported from the MSRTC site carry the division name in `district`
-- and code MSRTC-*; create a division per distinct name and link them, then link a
-- station to the depot of the same division whose clean name equals the station's.
-- =============================================================================
CREATE TABLE bus_divisions (
    id            BINARY(16)   NOT NULL,
    company_id    BINARY(16)   NOT NULL,
    division_code VARCHAR(30)  NOT NULL,
    division_name VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_bus_divisions_company (company_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MSRTC divisions; each groups depots and bus stations';

ALTER TABLE bus_depots
    ADD COLUMN division_id     BINARY(16) NULL AFTER branch_id,
    ADD COLUMN parent_depot_id BINARY(16) NULL AFTER division_id,
    ADD KEY idx_bus_depots_division (company_id, division_id),
    ADD CONSTRAINT fk_bus_depots_division FOREIGN KEY (division_id) REFERENCES bus_divisions (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_bus_depots_parent FOREIGN KEY (parent_depot_id) REFERENCES bus_depots (id) ON DELETE RESTRICT;

INSERT INTO bus_divisions (id, company_id, division_code, division_name, status,
                           created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), d.company_id,
       CONCAT('DIV-', UPPER(LEFT(REGEXP_REPLACE(d.district, '[^A-Za-z]', ''), 6))),
       d.district, 'ACTIVE', NOW(6), NOW(6), 0, 0
FROM (SELECT DISTINCT company_id, district FROM bus_depots
      WHERE depot_code LIKE 'MSRTC-%' AND district IS NOT NULL AND deleted = 0) d;

UPDATE bus_depots b
JOIN bus_divisions v ON v.company_id = b.company_id AND v.division_name = b.district AND v.deleted = 0
SET b.division_id = v.id
WHERE b.depot_code LIKE 'MSRTC-%';

UPDATE bus_depots s
JOIN bus_depots p ON p.company_id = s.company_id AND p.division_id = s.division_id
                 AND p.depot_type = 'BUS_DEPOT' AND p.deleted = 0 AND p.city = s.city
SET s.parent_depot_id = p.id
WHERE s.depot_type = 'BUS_STATION' AND s.deleted = 0 AND s.depot_code LIKE 'MSRTC-%';
