# Daily business summary (WhatsApp)

Built 2026-10-14, `V118`. See `CHANGELOG.md` 2026-10-14 for the full list.

## Meta template (submit from the app: Communication -> Templates -> Daily Business Summary -> Submit to Meta)

Name `daily_business_summary`, category **Utility**, language **English (US)** (`en_US`). The seeded
content below uses `{{companyName}}`-style variables; the app converts them to `{{1}}..{{11}}`.

Body (variables must not start or end the body, so it closes with plain text):

```
Daily business summary for {{1}} - {{2}}

Bookings: {{3}}
Revenue: Rs {{4}}
Wallet recharges: Rs {{5}} ({{6}} recharges)
Delivered: {{7}}
Cancelled: {{8}}
COD / To-Pay settled: Rs {{9}}

Right now: {{10}} in transit, {{11}} out for delivery.

Have a great day!
```

Sample values for approval: Amazing Logistics, 13 Oct 2026, 15, 8,240.00, 50,000.00, 3, 20, 2,
2,120.00, 8, 5.

## Turning it on for a company

Communication -> Channel Settings -> WhatsApp: fill **Daily report numbers** (10+ digit numbers,
comma separated, country code optional — bare 10-digit gets `91`). The template's own name,
content and ACTIVE switch live in Communication -> Templates. The WhatsApp channel must be enabled with a Phone
Number ID + token. Leave the numbers empty to switch it off.

## Gotchas

- "Yesterday" is the IST calendar day. In-transit / out-for-delivery are live counts.
- A day+number already SENT (or out of 3 attempts) is never re-sent; to re-send, delete that
  `daily_report_log` row.
- Log-only WhatsApp (`COMMUNICATION_WHATSAPP_ENABLED=false`) marks the day SENT with a synthetic
  id — switch to real mode *before* adding real numbers.
