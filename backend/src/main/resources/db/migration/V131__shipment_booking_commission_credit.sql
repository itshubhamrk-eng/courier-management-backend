-- Booking-branch commission is now credited on the shipment's first in-scan at a hub (or at
-- its final delivery branch, whichever comes first) — once per shipment. These two columns
-- record that credit so the trigger is idempotent and the Commission Credit report can tell
-- CREDITED from PENDING without reading the wallet ledger.
ALTER TABLE shipments
    ADD COLUMN booking_commission_credited_at TIMESTAMP(6) NULL,
    ADD COLUMN booking_commission_credited_amount DECIMAL(19,2) NULL;

-- Shipments whose commission was already credited under the old (final-branch in-scan /
-- delivery) triggers: take it from the wallet ledger so they are not credited a second time.
UPDATE shipments s
JOIN (SELECT company_id, reference_id, MIN(created_at) AS at, SUM(amount) AS amt
        FROM wallet_transactions
       WHERE sub_transaction_type = 'COM' AND transaction_type = 'CR'
         AND reference_type = 'SHIPMENT'
       GROUP BY company_id, reference_id) t
  ON t.company_id = s.company_id AND t.reference_id = s.shipment_number
SET s.booking_commission_credited_at = t.at,
    s.booking_commission_credited_amount = t.amt;

-- Commission Credit report in the User Permissions tree (sidebar node id: commission-credit-report).
INSERT INTO menu_items (id, parent_id, code, title, icon, route, permission_module,
                        display_order, is_active, is_system, created_at, updated_at, deleted, version)
SELECT UNHEX(REPLACE(UUID(), '-', '')), p.id, 'commission-credit-report', 'Commission Credit Report',
       'price_check', '/reports/commission-credit', 'REPORT',
       111, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), FALSE, 0
FROM menu_items p
WHERE p.code = 'reports'
  AND NOT EXISTS (SELECT 1 FROM menu_items x WHERE x.code = 'commission-credit-report');

-- Company-level choice of when the booking branch's commission is credited:
-- HUB_IN_SCAN | DELIVERY_BRANCH_IN_SCAN | DELIVERED.
ALTER TABLE company_settings_config
    ADD COLUMN commission_credit_trigger VARCHAR(30) NOT NULL DEFAULT 'HUB_IN_SCAN';
