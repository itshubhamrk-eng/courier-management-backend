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

## "Sign in with OCCPL" (single sign-on, backend)
`POST /api/v1/auth/platform/exchange` (public, only exists when `occpl.platform.enabled=true`), body `{accessToken, platformCompanyId, deviceId?, deviceName?}` → the same response as `/auth/login` (courier access + refresh tokens, roles, permissions).

Flow: verify the platform token (signature, issuer, audience `courier-api`; `azp` must equal `web-client-id`, no `client_id` claim, `email` present and `email_verified`) → find the company by `external_company_id` → ask the platform `GET /api/v1/service/access?companyId&userSubject` with this app's own client-credentials token (`courier-service`) → `TrustedSignInService` finds the user by email in that company (creating it on first sign-in) and opens a normal session.

- **Roles:** platform `COMPANY_ADMIN` → courier `COMPANY_ADMIN`; everyone else → `VIEWER` (lowest). Roles are set only when the account is created; an existing account keeps its roles.
- **Existing accounts:** matched by email within the company; a `PENDING` account is activated (the platform proved the address); `DISABLED`/`LOCKED` accounts are refused; platform-scoped accounts (`SUPER_ADMIN`, `PLATFORM_ADMIN`) are never signed in this way.
- **Created users** get a random, discarded password (`UserProvisioningService.provisionPlatformUser`), so they can only enter through the platform or a password reset they request.
- **Refusals** after a valid token (unknown company, not a member, no entitlement) all answer the same 403, so company and membership existence is not revealed. A platform outage answers 503.
- **Security chain:** `@Order(2)` chain for `/api/v1/auth/platform/**` (permit-all; the body token is the credential). Settings: `OCCPL_PLATFORM_WEB_CLIENT_ID`, `_CORE_BASE_URL`, `_CORE_TOKEN_URI`, `_SERVICE_CLIENT_ID`, `_SERVICE_CLIENT_SECRET`.
- **Not done here:** the Keycloak `courier-web` and `courier-service` clients (see the OCCPL runbook), shared sign-out, rate limiting of the endpoint (a forged token cannot pass verification, so brute force is not a concern, but a limit would still cap load).

### Web side (`frontend/src/app`)
- `GET /api/v1/auth/platform/config` (public; 404 when the platform sign-in is off) returns the Keycloak authorization and token endpoints and the web client id, so they are server settings and need no web rebuild.
- Route `/sso` (`features/auth/platform-sign-in.ts`) is both ends of the flow. Opened from the platform as `/sso?company=<platform company id>` it starts an authorization-code + PKCE round trip (`core/auth/platform-sso.service.ts`, `core/utils/pkce.util.ts`); when Keycloak sends the browser back to `/sso?code&state` it checks `state`, trades the code for the platform token with a plain `fetch` (our interceptors must not touch another host), calls `AuthService.loginWithPlatform` and opens the normal session, then goes to `/dashboard`.
- The platform token is used once and never stored. The platform company id is remembered in `localStorage` (`occpl.sso.company`) so the login page can offer a **Sign in with OCCPL** link on browsers that have used it.
- Keycloak client `courier-web` must be a public client with PKCE (S256), redirect URI `https://<courier host>/sso`, web origin `https://<courier host>`, and the `courier-api` audience scope.
- Product configuration on the platform: launch path `/sso?company={companyId}`.
