-- =============================================================================
-- V56: indexes for a handful of hot query paths that were missing one.
--
-- Audited against the real schema (every existing CREATE TABLE/CREATE INDEX in
-- this migration history), not guessed — most tables already carry a
-- company_id-leading composite matching their real filter columns. These four
-- are the genuine gaps found: real repository methods / Specification
-- predicates filtering or sorting on a column with no matching index.
-- =============================================================================

-- ShipmentRepository.countByCompanyIdAndCurrentLocationIdAnd... (Branch Overview
-- dashboard's pipeline/action-required tiles) and ShipmentSpecifications
-- .currentLocationId filter current_location_id — next_location_id already has
-- idx_shipments_next_location (V37), current_location_id never got the same.
CREATE INDEX idx_shipments_current_location ON shipments (company_id, current_location_id);

-- DRS Report's main query (findByCompanyAndAssignedAtBetween) filters + sorts
-- delivery_assignment by assigned_at within a company; only the branch/user
-- composites (both trailing status, not assigned_at) exist.
CREATE INDEX idx_delivery_assignment_assigned ON delivery_assignment (company_id, assigned_at);

-- Delivery Report's date filter (findShipmentIdsDeliveredBetween) ranges on
-- delivered_at, same gap.
CREATE INDEX idx_delivery_assignment_delivered ON delivery_assignment (company_id, delivered_at);

-- ManifestSpecifications.deliveryBranchId (arrival/in-scan-adjacent manifest
-- search) filters delivery_branch_id; only booking_branch_id has a composite
-- (idx_manifests_booking_branch, V19).
CREATE INDEX idx_manifests_delivery_branch ON manifests (company_id, delivery_branch_id, status);
