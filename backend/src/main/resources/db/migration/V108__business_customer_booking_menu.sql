-- Operations > Business Customer Booking: same SHIPMENT_CREATE gate as Shipment Booking (the
-- permission module is shared, so no new permission rows). Sits right after 'booking' in the
-- Shipment Management group so the User Permissions tree lists it next to its sibling.
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), p.id, 'business-booking', 'Business Customer Booking', 'business_center',
       '/business-customer/booking', 'SHIPMENT', 1, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM menu_items p
WHERE p.code = 'operations' AND p.parent_id IS NULL AND p.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = 'business-booking');
