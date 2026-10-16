-- =============================================================================
-- V126 — MSRTC bus crossing routes
--
-- A reusable "A to B goes via C (and D ...)" route between bus depots / stations, any
-- type to any type, independent of branches. The first booking that needs a crossing
-- saves the route; later bookings between the same two points pick it up automatically.
-- shipment_bus_crossings is the per-shipment snapshot (so editing a route later does not
-- rewrite history). Stops are ordered, 0-based. Uniqueness of (from, to) among live rows
-- is checked in the service — soft delete must not reserve it.
-- =============================================================================
CREATE TABLE bus_crossing_routes (
    id            BINARY(16)  NOT NULL,
    company_id    BINARY(16)  NOT NULL,
    from_depot_id BINARY(16)  NOT NULL,
    to_depot_id   BINARY(16)  NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_bus_crossing_routes_pair (company_id, from_depot_id, to_depot_id),
    CONSTRAINT fk_bus_crossing_routes_from FOREIGN KEY (from_depot_id) REFERENCES bus_depots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bus_crossing_routes_to   FOREIGN KEY (to_depot_id)   REFERENCES bus_depots (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Reusable MSRTC crossing routes between depots / stations';

CREATE TABLE bus_crossing_route_stops (
    route_id BINARY(16) NOT NULL,
    seq      INT        NOT NULL,
    depot_id BINARY(16) NOT NULL,
    PRIMARY KEY (route_id, seq),
    CONSTRAINT fk_bus_crossing_route_stops_route FOREIGN KEY (route_id) REFERENCES bus_crossing_routes (id) ON DELETE CASCADE,
    CONSTRAINT fk_bus_crossing_route_stops_depot FOREIGN KEY (depot_id) REFERENCES bus_depots (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE shipment_bus_crossings (
    id            BINARY(16)  NOT NULL,
    company_id    BINARY(16)  NOT NULL,
    shipment_id   BINARY(16)  NOT NULL,
    route_id      BINARY(16)  NULL,
    from_depot_id BINARY(16)  NOT NULL,
    to_depot_id   BINARY(16)  NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_shipment_bus_crossings_shipment (company_id, shipment_id),
    CONSTRAINT fk_shipment_bus_crossings_from FOREIGN KEY (from_depot_id) REFERENCES bus_depots (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shipment_bus_crossings_to   FOREIGN KEY (to_depot_id)   REFERENCES bus_depots (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-shipment snapshot of the MSRTC crossing route used';

CREATE TABLE shipment_bus_crossing_stops (
    crossing_id BINARY(16) NOT NULL,
    seq         INT        NOT NULL,
    depot_id    BINARY(16) NOT NULL,
    PRIMARY KEY (crossing_id, seq),
    CONSTRAINT fk_shipment_bus_crossing_stops_crossing FOREIGN KEY (crossing_id) REFERENCES shipment_bus_crossings (id) ON DELETE CASCADE,
    CONSTRAINT fk_shipment_bus_crossing_stops_depot FOREIGN KEY (depot_id) REFERENCES bus_depots (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
