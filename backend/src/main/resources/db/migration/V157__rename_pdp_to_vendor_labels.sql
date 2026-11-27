-- Display-only rename: "Pickup Delivery Partner" -> "Vendor". Codes, enums and routes are unchanged.
UPDATE menu_items
   SET title = 'Vendor Management'
 WHERE code = 'pdp-management' AND title = 'Pickup Delivery Partner Management';

UPDATE permissions
   SET permission_name = REPLACE(permission_name, 'Pickup-Delivery Partner', 'Vendor')
 WHERE module = 'PDP' AND permission_name LIKE '%Pickup-Delivery Partner%';

UPDATE menu_items SET title = 'Vendors'          WHERE code = 'pdp-partners'   AND title = 'Partners';
UPDATE menu_items SET title = 'Vendor Rates'     WHERE code IN ('pdp-rates', 'pdp-my-rates') AND title IN ('Partner Rates');
UPDATE menu_items SET title = 'Vendor Deliveries' WHERE code = 'pdp-deliveries' AND title = 'Partner Deliveries';
UPDATE menu_items SET title = 'Vendor Earnings'  WHERE code = 'pdp-earnings'  AND title = 'Partner Earnings';
UPDATE menu_items SET title = 'Vendor Billing'   WHERE code = 'pdp-billing'   AND title = 'Partner Billing';

UPDATE permissions
   SET permission_name = REPLACE(permission_name, 'Partner', 'Vendor')
 WHERE module IN ('PDP_RATE', 'PDP_BILLING') AND permission_name LIKE '%Partner%';
