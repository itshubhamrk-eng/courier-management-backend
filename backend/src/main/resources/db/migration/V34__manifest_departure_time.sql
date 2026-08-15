ALTER TABLE manifests
    ADD COLUMN departure_time TIMESTAMP NULL AFTER driver_user_id;
