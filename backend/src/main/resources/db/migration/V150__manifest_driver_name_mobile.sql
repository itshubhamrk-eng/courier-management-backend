ALTER TABLE manifests
    ADD COLUMN driver_name VARCHAR(120) NULL AFTER driver_user_id,
    ADD COLUMN driver_mobile VARCHAR(20) NULL AFTER driver_name;
