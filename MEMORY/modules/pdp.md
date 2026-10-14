# Module: pdp (Pickup-Delivery Partner)

**Status:** phases 1-3 built 2026-10-14: branch type + rate cards, pickup requests, earnings, billing statements, mandatory POD, partner-scoped login/menu. Pincode auto-suggest built (manual confirm). Reversal on shipment cancel built (negative adjustment next statement, unit-tested only). Open: GST/TDS (undecided), prod deploy.
**Package:** `com.courier.modules.pdp`. **Migrations:** `V115` (type, permissions, menu, rate cards), `V116` (pickup_requests), `V117` (pdp_earnings, pdp_statements, menu).

## Model
A PDP is a `Branch` with `branchType = PICKUP_DELIVERY_PARTNER` (replaced the unused `VENDOR`). It is created through the normal branch flow (branch + login user + role + wallet). It only collects and delivers. It is paid from its own rate cards, NOT from the branch commission fields (those stay unused for PDP).

## Rate cards (built)
`pdp_rate_cards` (partner branch, `rate_type` PICKUP|DELIVERY, optional district, `min_chargeable_weight`, delivery-only mathadi/ODA/return fee, failed_attempt_fee, validity, status) + `pdp_rate_slabs` (per-kg, half-open [min,max), non-overlapping, only last open-ended). Pay = max(actual, min) x slab rate. District card beats the all-districts card. No card = error; no fallback. One ACTIVE card per (partner, type, district, period). `/api/v1/pdp-rates` (+ `/calculate`). Customer freight is unchanged and independent (District Level Freight / Business Customer zones).

## Planned, not built
1. ~~Pickup module~~ BUILT (phase 2, `com.courier.modules.pickup`, `/api/v1/pickups`, admin-only create/assign/cancel, partner-scoped work, booking links back via `PATCH /pickups/{id}/shipment`; auto-assign by `branch_pincode_mapping` NOT built, assignment is manual). Original design: `pickup_requests` (REQUESTED -> ASSIGNED -> ACCEPTED -> OUT_FOR_PICKUP -> PICKED_UP -> BOOKED, side exits REJECTED/FAILED/CANCELLED), auto-assign by `branch_pincode_mapping`, convert to a shipment booked at the PDP branch. Uses existing PICKUP_* permission codes.
2. Delivery: reuse Load Sheet to the PDP branch, then In Scan / Generate DRS / Delivery / POD. **Mandatory POD for a PDP delivery** (photo required); delivery earning releases only on POD approval (mirrors V59 delivery commission gating).
3. `pdp_earnings` accrued, never credited to the wallet. Pickup earning at pickup Booked, delivery earning at POD approval; reversed on cancel.
4. Partner Billing statements mirroring Customer Billing (per PDP per period, pickup + delivery sections, net payable, export, Draft/Approved/Paid manual mark). No COD handling fee.
5. PDP roles (Manager / Rider), PDP-scoped "My Pickups / My Deliveries", Missing-POD worklist.

## Open questions
Reversal adjustment lines on later statements; GST/TDS on statements; pickup trigger (staff only vs customer); rider logins; failed-pickup fee semantics.

## Built in phase 3 (see CHANGELOG 2026-10-14)
Earnings accrue per job (`pdp_earnings`), never to a wallet; statements (`pdp_statements`) bill them. Partner delivery needs a POD photo and is paid only once the POD is approved. A partner login (BRANCH_MANAGER at a PDP branch) sees only its own data and a narrowed sidebar (`PartnerContextService`). Setup per partner: branch (type PDP), District Level Freight rows (what customers pay), wallet float, pickup + delivery rate cards (what the partner earns).

## Billing frequency (built 2026-10-14, V121)
`pdp_billing_settings` (one optional row per partner; none = MONTHLY, reuses customer `BillingFrequency` + `BillingCycle`). `PdpBillingCycle.due()` = pure period maths (from = day after last statement else oldest unbilled earning; to = last closed cycle end). `PdpBillingCycleJob` daily 02:00 (`pdp.billing.cron`) drafts statements via `generateScheduled` (no auth, idempotent). Manual + scheduled generate reject a period overlapping an existing statement. Endpoints: `GET /pdp-statements/schedule`, `PUT /pdp-billing-frequency/{branchId}`, `POST /pdp-statements/generate-due`. UI: "Billing schedule" card on Partner Billing. Live-verified frequency/overlap/generate-due(0); an actual due statement was only unit-tested (fixtures already billed to 2026-10-31).
