# Manifest Creation: "Send to Vendor" (short design)

## Intent
Vendors (branch type PICKUP_DELIVERY_PARTNER) deliver shipments in their city. The branch should be able to
hand a manifest to a vendor from Manifest Creation, the vendor in-scans it, and TO_PAY freight is debited from the
vendor's wallet, exactly as it is for any delivery branch.

## Goals
- New "Send to Vendor" mode button beside Send to Hub / Send to Branch on Manifest Creation (`loading-sheet.ts`).
- Pick a vendor (vendor branches only, shown with city), tick the shipments waiting at my branch, Create Manifest.
- Manifest's deliveryBranchId = the vendor, so shipments are assigned to that vendor.

## Non-goals
- No change to wallet/commission logic. No migration.
- Generate DRS vendor option is a separate, still-open question (moving a DRS to a vendor changes the delivery
  branch and so who gets the DRS charge / delivery commission). Not in this change.

## Already true (verified in code)
- Manifest create accepts any branch as deliveryBranchId; the shipment gets that delivery branch.
- At in-scan at the final delivery branch, a TO_PAY shipment publishes `ToPayReceivedAtDeliveryBranch`, and
  `debitForToPayReceivedAtBranch` debits that branch's wallet (needs an ACTIVE wallet; all 48 vendors have one
  with Wallet enabled). So vendor in-scan -> vendor wallet debit needs no backend change.
- A vendor delivery requires a POD photo; vendor is paid per its rate card, not branch commission fields.
- Vendor delivery: the Delivery screen (`/movement/delivery`) is reachable from a vendor login's Operations menu; its
  Complete Delivery button is disabled until a POD photo is chosen, and the backend
  (`requirePodForPartnerDelivery`) refuses a vendor delivery with no POD photo on file. Already mandatory; no change.
- A failed debit is only logged (shipment stays in-scanned, undebited).

## Approaches
A. New "vendor" mode reusing the Send-to-Hub shipment picker, vendor-only dropdown. (Picked; recommended)
B. Filter the Send-to-Branch dropdown to include vendors. Rejected: no new button, vendors mixed with branches.

## Builder notes (my calls)
- Mode name 'vendor'; vendor list = branchDirectory entries with branchType PICKUP_DELIVERY_PARTNER, excluding own branch.
- Shipment picker = same as hub mode (`loadBookedForHub`: unassigned delivery branch, BOOKED/READY_FOR_MANIFEST).
- `hubTransfer: false`, `deliveryMode: 'BRANCH_DELIVERY'`.
