-- =============================================================================
-- V127 — MSRTC bus timetable (the "वेळापत्रक" board of a depot / bus station)
--
-- MSRTC publishes one board per depot / bus station: a row per place (village / town)
-- with the times buses LEAVE for it (जाणाऱ्या गाड्या) and the times buses ARRIVE from it
-- (येणाऱ्या गाड्या); major / long-distance places are highlighted. Same shape here:
-- bus_timetable_entries = one row of the board (depot + place), the two collection tables
-- hold its ordered times. The board of a depot is replaced as a whole by the service.
-- =============================================================================
CREATE TABLE bus_timetable_entries (
    id          BINARY(16)   NOT NULL,
    company_id  BINARY(16)   NOT NULL,
    depot_id    BINARY(16)   NOT NULL,
    place_name  VARCHAR(150) NOT NULL,
    is_major    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INT          NOT NULL DEFAULT 0,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_bus_timetable_depot (company_id, depot_id, sort_order),
    CONSTRAINT fk_bus_timetable_depot FOREIGN KEY (depot_id) REFERENCES bus_depots (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One row of a depot / station timetable board: a place and its bus times';

CREATE TABLE bus_timetable_departures (
    entry_id BINARY(16) NOT NULL,
    seq      INT        NOT NULL,
    dep_time TIME       NOT NULL,
    PRIMARY KEY (entry_id, seq),
    CONSTRAINT fk_bus_timetable_dep_entry FOREIGN KEY (entry_id) REFERENCES bus_timetable_entries (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE bus_timetable_arrivals (
    entry_id BINARY(16) NOT NULL,
    seq      INT        NOT NULL,
    arr_time TIME       NOT NULL,
    PRIMARY KEY (entry_id, seq),
    CONSTRAINT fk_bus_timetable_arr_entry FOREIGN KEY (entry_id) REFERENCES bus_timetable_entries (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
