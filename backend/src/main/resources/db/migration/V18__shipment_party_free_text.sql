-- =============================================================================
-- V18 — Shipment Booking: Consignor/Consignee become plain text
--
-- The booking desk's UI reference (a real transport-operations screen) has no customer
-- lookup on the Parties block at all — Name / Address / Contact Number, three plain
-- fields per party, typed fresh every booking. Sender/receiver stop being a reference
-- into the Customer module (`sender_customer_id`/`receiver_customer_id`/
-- `sender_address_id`/`receiver_address_id`, V17) and become six plain columns on the
-- shipment itself. The Customer module is untouched — this only disconnects Shipment
-- Booking from it.
--
-- Losing the address record also loses its pincode, which the Pricing Engine call
-- (com.courier.modules.pricing) needs for its serviceability check — so `pickup_pincode`/
-- `delivery_pincode` are added as their own plain columns, typed directly rather than
-- resolved from an address id, the same "From Pincode"/"To Pincode" fields the reference
-- UI shows above the Parties block.
--
-- Existing rows (dev fixtures, MEMORY/local-dev-environment.md) are backfilled from the
-- customer/address data they still reference before the old columns are dropped, so nothing
-- already booked loses its sender/receiver on this upgrade.
-- =============================================================================

ALTER TABLE shipments
    ADD COLUMN pickup_pincode   VARCHAR(10)  NULL AFTER delivery_branch_id,
    ADD COLUMN delivery_pincode VARCHAR(10)  NULL AFTER pickup_pincode,
    ADD COLUMN sender_name      VARCHAR(150) NULL AFTER delivery_pincode,
    ADD COLUMN sender_address   VARCHAR(500) NULL AFTER sender_name,
    ADD COLUMN sender_contact   VARCHAR(20)  NULL AFTER sender_address,
    ADD COLUMN receiver_name    VARCHAR(150) NULL AFTER sender_contact,
    ADD COLUMN receiver_address VARCHAR(500) NULL AFTER receiver_name,
    ADD COLUMN receiver_contact VARCHAR(20)  NULL AFTER receiver_address;

UPDATE shipments s
    JOIN customers sc ON sc.id = s.sender_customer_id
    JOIN customer_addresses sa ON sa.id = s.sender_address_id
    LEFT JOIN master_pincodes spc ON spc.id = sa.pincode_id
SET s.sender_name    = COALESCE(NULLIF(TRIM(CASE
                            WHEN sc.customer_type = 'BUSINESS' AND sc.company_name IS NOT NULL
                                 AND TRIM(sc.company_name) <> '' THEN sc.company_name
                            ELSE CONCAT_WS(' ', sc.first_name, sc.middle_name, sc.last_name)
                        END), ''), 'Unknown'),
    s.sender_address   = COALESCE(NULLIF(TRIM(CONCAT_WS(', ', sa.address_line1, sa.address_line2,
                              sa.landmark)), ''), 'Unknown'),
    s.sender_contact   = COALESCE(sc.mobile, '0000000000'),
    s.pickup_pincode   = COALESCE(spc.code, '000000');

UPDATE shipments s
    JOIN customers rc ON rc.id = s.receiver_customer_id
    JOIN customer_addresses ra ON ra.id = s.receiver_address_id
    LEFT JOIN master_pincodes rpc ON rpc.id = ra.pincode_id
SET s.receiver_name    = COALESCE(NULLIF(TRIM(CASE
                            WHEN rc.customer_type = 'BUSINESS' AND rc.company_name IS NOT NULL
                                 AND TRIM(rc.company_name) <> '' THEN rc.company_name
                            ELSE CONCAT_WS(' ', rc.first_name, rc.middle_name, rc.last_name)
                        END), ''), 'Unknown'),
    s.receiver_address   = COALESCE(NULLIF(TRIM(CONCAT_WS(', ', ra.address_line1, ra.address_line2,
                              ra.landmark)), ''), 'Unknown'),
    s.receiver_contact   = COALESCE(rc.mobile, '0000000000'),
    s.delivery_pincode   = COALESCE(rpc.code, '000000');

-- Any row the two backfills above didn't touch (no matching customer/address row left)
-- still needs a value before the columns turn NOT NULL.
UPDATE shipments
SET pickup_pincode   = COALESCE(pickup_pincode, '000000'),
    delivery_pincode = COALESCE(delivery_pincode, '000000'),
    sender_name       = COALESCE(sender_name, 'Unknown'),
    sender_address    = COALESCE(sender_address, 'Unknown'),
    sender_contact    = COALESCE(sender_contact, '0000000000'),
    receiver_name     = COALESCE(receiver_name, 'Unknown'),
    receiver_address  = COALESCE(receiver_address, 'Unknown'),
    receiver_contact  = COALESCE(receiver_contact, '0000000000')
WHERE sender_name IS NULL OR receiver_name IS NULL;

ALTER TABLE shipments
    MODIFY COLUMN pickup_pincode   VARCHAR(10)  NOT NULL,
    MODIFY COLUMN delivery_pincode VARCHAR(10)  NOT NULL,
    MODIFY COLUMN sender_name      VARCHAR(150) NOT NULL,
    MODIFY COLUMN sender_address   VARCHAR(500) NOT NULL,
    MODIFY COLUMN sender_contact   VARCHAR(20)  NOT NULL,
    MODIFY COLUMN receiver_name    VARCHAR(150) NOT NULL,
    MODIFY COLUMN receiver_address VARCHAR(500) NOT NULL,
    MODIFY COLUMN receiver_contact VARCHAR(20)  NOT NULL;

ALTER TABLE shipments
    DROP INDEX idx_shipments_sender,
    DROP INDEX idx_shipments_receiver,
    DROP COLUMN sender_customer_id,
    DROP COLUMN receiver_customer_id,
    DROP COLUMN sender_address_id,
    DROP COLUMN receiver_address_id;
