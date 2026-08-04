# Module: company-settings (Company Settings)

**Status:** DONE and verified against MySQL 8.0.46 (Phase 3, v0.8.0).
**Package:** `com.courier.modules.company`.
**Depends on:** `shared`; reads the `Company` aggregate to seed defaults.
**Consumed by (as they land):** Branch, Hub, Shipment, Wallet, Payment, Report.

## Two settings tables, deliberately

There were already `company_settings` rows before this module — a **key/value** table
(V4) holding plan-derived facts (feature flags, quota limits) that provisioning seeds and
permission gating reads. The spec's Company Settings is a different shape: **one wide,
typed, sectioned row per company** of admin-tunable behaviour.

Rather than disturb the load-bearing key/value table, this module adds a **new** table:

| Table | Shape | Holds | Written by | Read by |
|---|---|---|---|---|
| `company_settings` (V4) | key/value, many rows | plan features + limits | provisioning | permission plan-gating |
| `company_settings_config` (V8) | wide, one row/company | 8 tunable sections | `COMPANY_ADMIN` | shipment/finance/… (future) |

The entity is `CompanySettings` (plural) — the existing key/value entity keeps the
singular `CompanySetting`. Two clearly-scoped concerns: "what the plan grants" versus "how
the company wants the app to behave".

## Entity

`CompanySettings` extends `CompanyOwnedEntity` (company-owned, `@Filter`). **No
`@SQLRestriction`** — soft delete does not apply; a company always has settings, so the
row is created once and updated in place, never deleted. `UNIQUE(tenant_id)` enforces one
row per company. `BaseEntity` still records `updatedBy`/`updatedAt` and carries `@Version`.

Eight sections, ~50 typed columns:

- **General / Regional** — companyName, displayName, supportEmail, supportMobile, website,
  language, timezone, currency, country, state, city.
- **Shipment** — awbPrefix, awbRunningNumber, bookingPrefix, manifestPrefix,
  defaultServiceType, defaultPackageType, weightUnit (`KG|GRAM|POUND`),
  dimensionUnit (`CM|INCH`), autoGenerateBarcode, allowDuplicateReferenceNumber,
  autoAssignTrackingNumber.
- **Finance** — gstPercentage (`DECIMAL(5,2)`, 0–100), invoicePrefix, creditLimit
  (`DECIMAL(19,4)`), walletEnabled, codEnabled, onlinePaymentEnabled, autoInvoiceGeneration.
- **Notification** — smsEnabled, emailEnabled, whatsappEnabled, pushNotificationEnabled.
- **Security** — passwordPolicy, sessionTimeoutMinutes, maxLoginAttempts,
  lockDurationMinutes, otpExpiryMinutes. **Stored but not yet consumed by auth** — auth
  still uses `AuthProperties`; these become effective when auth is made company-owned.
- **Branding** — companyLogo, favicon, primaryColor/secondaryColor (hex), theme
  (`LIGHT|DARK|SYSTEM`).
- **Document** — invoiceTemplate, labelTemplate, manifestTemplate, receiptTemplate.

Some General fields (companyName, supportEmail, timezone, currency, city…) mirror the
`companies` row. The `companies` record stays the **canonical identity**; these are the
settings-screen copy, seeded from it on creation.

## Business rules

| Rule | Where |
|---|---|
| One settings row per company | `UNIQUE(tenant_id)` + get-or-create |
| All values company-specific | company-owned entity; every op on the caller's company |
| Soft delete not applicable | no `@SQLRestriction`; row never deleted |
| Audit maintained | `BaseEntity` auditing + `AuditService` events |
| GST 0–100, valid hex colours, bounded timeouts | Bean Validation + a DB CHECK on GST |
| Optimistic locking on the full PUT | client `version`, 409 on mismatch |

**Get-or-create.** The row is created on first access (GET *or* the first write), seeded
from the company: identity fields copied, prefixes derived from the company code, and the
field defaults for everything else. So a brand-new company needs no explicit
initialisation, and every existing company gets its row lazily.

**Merge, never blank.** Both the full PUT and the section PATCHes apply only the fields
the caller supplied; an omitted field is left unchanged. One all-optional request DTO
drives every write — `set(value, setter)` in the service is the single place that rule
lives.

## Security

| Actor | Reach |
|---|---|
| Any authenticated **company** user | read (settings drive many modules, so the read gate is wide) |
| `COMPANY_ADMIN` | write |
| `SUPER_ADMIN` (no bound company) | refused — settings are inherently company-scoped |

Per-method `@PreAuthorize` on `CompanySettingsServiceImpl`: read = `isAuthenticated()` +
a bound company; write = `COMPANY_ADMIN`. `SecurityConfig` only requires authentication on
`/api/v1/company-settings/**`. Isolation is inherent: there is no id in any path — every
call resolves the caller's own company, so one company can never read or write another's.

## REST APIs

| Method | Path | Who | Notes |
|---|---|---|---|
| `GET` | `/api/v1/company-settings` | any company user | get-or-create |
| `PUT` | `/api/v1/company-settings` | `COMPANY_ADMIN` | all sections; `version` required |
| `PATCH` | `…/general` | `COMPANY_ADMIN` | general + regional fields |
| `PATCH` | `…/shipment` | `COMPANY_ADMIN` | |
| `PATCH` | `…/finance` | `COMPANY_ADMIN` | |
| `PATCH` | `…/security` | `COMPANY_ADMIN` | stored, not yet enforced by auth |
| `PATCH` | `…/notification` | `COMPANY_ADMIN` | |
| `PATCH` | `…/branding` | `COMPANY_ADMIN` | |

Document-template and regional-only fields are editable through the full `PUT` (no
dedicated PATCH, matching the spec's endpoint list). The response groups fields by section
(nested records) for a settings UI; nulls are serialised.

### Errors
400 validation (bad GST, hex colour, enum, out-of-range timeout) · 401 unauthenticated ·
403 non-admin write · 404 no company for the company · 409 stale version on PUT · 422 no
bound company.

## Configuration usage

How other modules will read these (none consume them yet):

- **Shipment** — awb/booking/manifest prefixes + `awbRunningNumber`, weight/dimension
  units, barcode/tracking toggles, duplicate-reference rule, default service/package type.
- **Finance / Wallet / Payment** — GST %, invoice prefix, credit limit, wallet/COD/online
  toggles, auto-invoice.
- **Notification** — per-channel enable flags.
- **Report / Document** — the template names.
- **Branding** — logo/favicon/colours/theme for the company console.
- **Security** — *pending*: auth reads `AuthProperties`, not these, until it is made
  company-owned.

## Database changes — `V8__company_settings_config.sql`

New `company_settings_config`: id, tenant_id, the ~50 section columns with sensible
defaults, `BaseEntity` columns, `UNIQUE(tenant_id)`, and a CHECK that GST is null or
0–100. No index beyond the unique key — a single row per company needs none. The V4
key/value `company_settings` table is untouched.

## Tests

11 new unit tests (303 in the suite): `CompanySettingsTest` (seed-from-company, field
defaults) and `CompanySettingsServiceImplTest` (get-or-create seeding + audit, existing
row not reseeded, refused without a company, 404 for a company with no company, section
patch merges and does not blank, replace spans sections + normalises, stale-version 409,
no-version skips the check).

## Verified by running it

Against MySQL 8.0.46 on 2026-07-23, using the Phase-3 company fixtures:

- `V8` applied; `ddl-auto: validate` passed.
- GET on a company with no settings created and seeded the row (company name, currency,
  `LEGACY` AWB prefix, GST 18.00, LIGHT theme, version 0).
- A `VIEWER` read (200) but could not write (403); a `SUPER_ADMIN` with no company got 422.
- `PATCH /finance` changed GST + wallet + invoice prefix (uppercased) and left the
  shipment section untouched; `PATCH /shipment` set enums and a prefix; `PATCH /branding`
  set theme + hex colour.
- Validation: GST > 100, a non-hex colour, and a bad enum each 400.
- Full `PUT` with a stale version → 409; with the correct version applied
  email (lowercased), sms, session timeout, and **kept** the previously-set GST — proving
  merge, not replace-with-blank.
- **Cross-company**: a second company got its own row with its own name and untouched
  defaults; the first company's edits did not leak. Two rows, one per company.
- Audit: `COMPANY_SETTINGS_INITIALIZED` ×2, `COMPANY_SETTINGS_UPDATED` ×4.

## Next

- [ ] Consume the settings in the modules that need them, starting with Shipment
      (prefixes, units, toggles).
- [ ] Make auth company-owned so `security.*` overrides `AuthProperties` per company.
- [ ] `CompanySettingsSpecifications` is built and tested but unexposed — wire a
      `SUPER_ADMIN` cross-company report (e.g. "companies with wallet enabled") if needed.
