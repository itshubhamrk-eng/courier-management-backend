ALTER TABLE vehicles
    ADD COLUMN owner_name     VARCHAR(150) NULL AFTER vehicle_number,
    ADD COLUMN model_variant  VARCHAR(50)  NULL AFTER model,
    ADD COLUMN series         VARCHAR(30)  NULL AFTER model_variant,
    ADD COLUMN chassis_number VARCHAR(50)  NULL AFTER series,
    ADD COLUMN engine_number  VARCHAR(50)  NULL AFTER chassis_number,
    ADD COLUMN dealer_name    VARCHAR(150) NULL AFTER engine_number;
