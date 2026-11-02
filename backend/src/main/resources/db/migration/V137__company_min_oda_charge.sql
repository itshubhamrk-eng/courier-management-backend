-- Lowest ODA Charge the booking desk may enter in place of the calculated figure
-- (calculated 250 -> editable down to 150). 0 removes the floor.
ALTER TABLE company_settings_config
    ADD COLUMN min_oda_charge DECIMAL(19,4) NOT NULL DEFAULT 150.0000
        AFTER to_pay_charge;
