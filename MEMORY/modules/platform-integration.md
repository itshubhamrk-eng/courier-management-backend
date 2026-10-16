# platform-integration

Lets the OCCPL SaaS platform treat this app as one of its products. Provisioning only (Step A); single sign-on is not built.

## Switch
`occpl.platform.enabled` (default false). When false no controller, service or security chain exists.

| Property | Meaning |
|---|---|
| `occpl.platform.issuer-uri` | Keycloak realm issuer (`iss`) |
| `occpl.platform.jwk-set-uri` | realm signing keys |
| `occpl.platform.audience` | this product's audience (`aud`) |
| `occpl.platform.provisioning-client-id` | platform service client (`client_id`) |
| `occpl.platform.default-plan-code` | courier plan given to a provisioned company |
| `occpl.platform.default-mobile` | optional; company mobile when the platform sends none |

## Endpoints
- `PUT /provisioning/v1/tenants/{companyId}` body: `companyCode, companyName, adminEmail, adminName?, adminPhone?` (the platform's names; `adminName` is split into first/last, `adminPhone` is the company mobile), plus the optional profile `country, website, addressLine1/2, city, state, postalCode, gstNumber, panNumber, supportEmail, supportPhone, logoUrl, faviconUrl` (other platform fields ignored) → `{companyId, status: PROVISIONED, created}`.
- `DELETE /provisioning/v1/tenants/{companyId}` → 204, or 404 if never provisioned. Deactivates; no data is deleted.

## Rules
- `companyId` in the path is the **platform's** id, stored in `companies.external_company_id`. Tenancy still uses `companies.company_id`.
- Runs under a fixed company-less SUPER_ADMIN principal (restored afterwards) so `CompanyService.create` keeps its audit/events.
- Security chain matches only `/provisioning/v1/**`; everything else uses the app's own chain.
- A concurrent first PUT for the same id can fail on the unique key; the platform retries and then gets the existing company.

## Hosting beside the platform (dev)
- Images: `.github/workflows/images.yml` publishes `ghcr.io/<owner>/courier-backend` and `courier-frontend` (tags: SHA and `dev`); the web image is built with `BUILD_CONFIGURATION=production`.
- Compose, Caddy and `bin/courier-setup.sh` live in the OCCPL repository (`infra/vps`). One public host serves both: `/provisioning/*` goes to the backend, everything else to the web image (nginx forwards `/api/`).
- A fresh database has **no users and no subscription plans** (no seed; plans and the first `SUPER_ADMIN` are created by hand). The plan named in `OCCPL_PLATFORM_DEFAULT_PLAN_CODE` must exist before the first company is provisioned, otherwise PUT fails with 404 for the plan.
- Profile fields from the platform fill the new company's website, address, GST and PAN. `logoUrl`/`faviconUrl` and the support contact are accepted but **not stored**: the links are signed and expire within a day, the `logo`/`favicon` columns hold plain URLs (500 chars), and the courier company has no support-contact fields.
