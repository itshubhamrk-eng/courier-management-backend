# Meta recharge button + WhatsApp spend card (short design)

## Why
Meta bills WhatsApp usage from a prepaid balance / payment method managed only in Meta Business Manager. No API tops it up or reads the balance. Admins find out it ran dry when sends fail. Give them a one-click path to Meta billing and an early warning.

## Goals
- "Recharge on Meta" button opening Meta billing in a new tab.
- "WhatsApp spend this month" card on the Communication Dashboard: spend so far vs a monthly budget, amber at >=80%, red at >=100%.
- Admin sets the monthly budget in Channel Settings (WhatsApp card).

## Non-goals / anti-goals
- No payment taken in the portal; no portal wallet.
- No claim of "remaining balance" - Meta does not expose it. UI says "spend vs your budget", never "balance".
- No new migration, no new secret storage.

## Constraints
- `businessAccountId` and access token already live in `communication_settings` (WHATSAPP row, `configJson` + secret). Reuse them.
- `WhatsAppProvider.fetchPricing(credentials, from, to)` already reads Meta pricing analytics (used by `WhatsAppRateSyncServiceImpl`). Reuse it; do not add a second Meta client.
- Budget stored as `monthlyBudget` key in the WHATSAPP `configJson` (same place as `rateUtility` etc.), written through `CommunicationSettingService.upsert` so it stays audited/role-checked.
- Meta analytics lag a few hours; card says "as reported by Meta".
- Currency from Meta's report (`rateCurrency` style), shown as-is.

## Approaches considered
1. Button only - cheap, no early warning.
2. **Button + spend card (chosen by user).**
3. Button + failure banner on Meta payment error - warns only after sends fail.

## Design
Backend (communication module):
- `GET /api/v1/communication/dashboard/whatsapp-spend` -> `{ currency, spent, budget|null, percentUsed|null, level: OK|WARN|OVER|NO_BUDGET, periodStart, periodEnd, note }`. Period = 1st of current month 00:00 India time to now. Spent = sum of `cost` over all pricing points (REGULAR and otherwise; free types cost 0). New `WhatsAppSpendService` beside `WhatsAppRateSyncService`; same role guard (COMPANY_ADMIN or NOTIFICATION_UPDATE... read side: COMPANY_ADMIN or NOTIFICATION_READ).
- Not configured / no Business Account ID -> level `NOT_CONFIGURED` with a message, not an error.
- Meta error (bad token, missing permission) -> returned as `note`, card shows it; dashboard still loads.
- `monthlyBudget` added to the allowed WhatsApp config keys in settings save.

Frontend:
- Dashboard: spend card (progress bar, amount, % of budget, state colour) + "Recharge on Meta" button. Card hidden for roles that cannot read settings.
- Channel Settings WhatsApp card: "Monthly budget" field.
- Button URL: `https://business.facebook.com/billing_hub/payment_settings` (constant in one place; opens `_blank` with `rel=noopener`). Query `?asset_id=<businessAccountId>` NOT added - unverified; plain URL is safe.

## Left to the builder
Exact file/class names within the module, card styling consistent with existing dashboard cards, unit tests for the level thresholds (79/80/99/100%, no budget, zero spend) and a spec for the card states.

## Verification
Unit tests for thresholds; live check on the verify stack against the Meta test number (expect spend 0, level OK/NO_BUDGET) and a stubbed over-budget response for amber/red.
