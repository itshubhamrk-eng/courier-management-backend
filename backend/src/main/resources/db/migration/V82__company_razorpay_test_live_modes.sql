-- V82 — Company Razorpay config gains separate Test and Live credential slots.
--
-- Previously one key_id/key_secret_encrypted pair per company with no way to hold a test
-- key alongside a live one — switching required overwriting the live secret to try
-- something in test mode. Now each company stores both slots and a `mode` flag saying
-- which one wallet recharge actually uses; the other slot stays saved so switching back
-- doesn't mean re-entering credentials.
--
-- Existing rows are assumed to hold a live key (the only kind ever configured before this
-- migration) and are backfilled into the live slot with mode = LIVE.

ALTER TABLE company_razorpay_config
    ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'TEST' AFTER enabled,
    ADD COLUMN test_key_id VARCHAR(255) NULL AFTER mode,
    ADD COLUMN test_key_secret_encrypted VARCHAR(1000) NULL AFTER test_key_id,
    ADD COLUMN live_key_id VARCHAR(255) NULL AFTER test_key_secret_encrypted,
    ADD COLUMN live_key_secret_encrypted VARCHAR(1000) NULL AFTER live_key_id;

UPDATE company_razorpay_config
   SET live_key_id = key_id,
       live_key_secret_encrypted = key_secret_encrypted,
       mode = 'LIVE'
 WHERE key_id IS NOT NULL AND key_id <> '';

ALTER TABLE company_razorpay_config
    DROP COLUMN key_id,
    DROP COLUMN key_secret_encrypted;
