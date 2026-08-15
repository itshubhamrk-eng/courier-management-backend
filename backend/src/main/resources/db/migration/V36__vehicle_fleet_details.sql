-- Vehicle Management: the minimal V19 fleet record (registration, optional
-- vehicle_type_id, capacity, ACTIVE/INACTIVE status) grows into a full fleet entity —
-- ownership dates, statutory document expiries, base branch, an operational status and
-- a separate active/inactive record toggle. See MEMORY/modules/shipment-movement.md.

ALTER TABLE vehicles
    ADD COLUMN vehicle_type       VARCHAR(20)   NOT NULL DEFAULT 'OTHER' AFTER vehicle_number,
    ADD COLUMN make               VARCHAR(50)   NULL AFTER vehicle_type,
    ADD COLUMN model              VARCHAR(50)   NULL AFTER make,
    -- PETROL | DIESEL | CNG | EV | OTHER
    ADD COLUMN fuel_type          VARCHAR(20)   NULL AFTER model,
    ADD COLUMN current_odometer   DECIMAL(12,2) NULL AFTER capacity_kg,
    ADD COLUMN purchase_date      DATE          NULL AFTER current_odometer,
    ADD COLUMN registration_date  DATE          NULL AFTER purchase_date,
    ADD COLUMN insurance_expiry   DATE          NULL AFTER registration_date,
    ADD COLUMN puc_expiry         DATE          NULL AFTER insurance_expiry,
    ADD COLUMN fitness_expiry     DATE          NULL AFTER puc_expiry,
    ADD COLUMN permit_expiry      DATE          NULL AFTER fitness_expiry,
    ADD COLUMN branch_id          BINARY(16)    NULL AFTER permit_expiry,
    -- record enable/disable toggle, independent of the operational `status` column
    ADD COLUMN active             BOOLEAN       NOT NULL DEFAULT TRUE AFTER remarks;

-- Carry the old ACTIVE/INACTIVE dichotomy into the new (status, active) pair before the
-- status values themselves change shape: an old ACTIVE vehicle becomes AVAILABLE + active,
-- an old INACTIVE vehicle stays INACTIVE + inactive.
UPDATE vehicles
   SET active = (status = 'ACTIVE'),
       status = CASE WHEN status = 'ACTIVE' THEN 'AVAILABLE' ELSE 'INACTIVE' END;

-- AVAILABLE | IN_USE | MAINTENANCE | INACTIVE
ALTER TABLE vehicles
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE vehicles
    DROP COLUMN vehicle_type_id;

ALTER TABLE vehicles
    ADD KEY idx_vehicles_company_branch (company_id, branch_id),
    ADD KEY idx_vehicles_company_active (company_id, active);
