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
- `PUT /provisioning/v1/tenants/{companyId}` body: `companyCode, companyName, adminEmail, adminName?, adminPhone?` (the platform's names; `adminName` is split into first/last, `adminPhone` is the company mobile) (other platform fields ignored) → `{companyId, status: PROVISIONED, created}`.
- `DELETE /provisioning/v1/tenants/{companyId}` → 204, or 404 if never provisioned. Deactivates; no data is deleted.

## Rules
- `companyId` in the path is the **platform's** id, stored in `companies.external_company_id`. Tenancy still uses `companies.company_id`.
- Runs under a fixed company-less SUPER_ADMIN principal (restored afterwards) so `CompanyService.create` keeps its audit/events.
- Security chain matches only `/provisioning/v1/**`; everything else uses the app's own chain.
- A concurrent first PUT for the same id can fail on the unique key; the platform retries and then gets the existing company.
