# Module: customer (Customer Management)

**Status:** DONE, code complete and verified against MySQL 8.0.46 and the Angular console
(Phase 7, v0.13.0). Built 2026-07-30, pulled forward ahead of Hub and Rate Master by
explicit request — see `MEMORY/BACKLOG.md`.
**Package:** `com.courier.modules.customer` — a new module, not folded into `company`.
**Depends on:** `shared`; `com.courier.modules.master`'s `CountryService` /
`StateService` / `DistrictService` / `CityService` / `AreaService` / `PincodeService`
(application interfaces, not repositories or entities — see *Geography validation*).
**Depended on by:** Shipment, eventually, through a bare `customerId` — see the
independence rule below. Nothing depends on it yet.

## Purpose

A customer is reusable master data: a person or business a company ships for or delivers
to, independent of any particular booking. A customer can have many addresses (pickup,
delivery, or both). Branch staff create and reuse customers while booking a shipment at
the counter — that is the whole reason `CUSTOMER_CREATE`/`CUSTOMER_UPDATE` are held by
roles below `COMPANY_ADMIN`.

## Customer must not depend on Shipment, and vice versa

`MEMORY/modules/shipment.md` already documented this from the other side, before either
module existed in code: a shipment's consignor/consignee fields are frozen copies taken
at booking time, and an optional `customerId` is carried only for "this booking is linked
to that repeat customer" reporting — never joined at read or write time. This module is
the other half of that promise: `Customer`/`CustomerAddress` know nothing about
`ShipmentOrder`, and nothing here will ever gain a foreign key into it. Deleting or
editing a customer must never touch, and must never be blocked by, a past shipment.

## Data model

```
Customer (aggregate root, CompanyOwnedEntity)
├── customerCode      unique per company, immutable, reserved past soft delete
├── customerType       INDIVIDUAL | BUSINESS
├── companyName, firstName, middleName, lastName
├── mobile             unique per company, NOT reserved past soft delete
├── alternateMobile, email, gstNumber, panNumber
└── status              ACTIVE | INACTIVE

CustomerAddress (CompanyOwnedEntity, real FK to customers.id)
├── customerId          FK, ON DELETE RESTRICT (soft delete means rows persist anyway)
├── addressType         HOME | OFFICE | WAREHOUSE
├── countryId, stateId, districtId, cityId, areaId, pincodeId   — cross-module UUIDs,
│                        validated in the service, no DB FK (see below)
├── addressLine1 (required), addressLine2, landmark
├── latitude, longitude  DECIMAL(9,6), same range checks as Branch
├── isDefaultPickup, isDefaultDelivery   at most one true per customer, per flag
└── status               ACTIVE | INACTIVE
```

**`customer_addresses.customer_id` carries a real foreign key** — unlike
`branches.manager_id` or `users.company_id`, both tables here are written by this same
module from day one, so there is no "orphan rows already in the dev database" reason to
defer it. The geography columns (`country_id` … `pincode_id`) do **not** get a physical
FK: they reference `V12`'s global masters, a different module's tables, and the project's
established pattern (`branches.manager_id`) is to validate cross-module references in the
service layer rather than force a schema-level coupling between modules.

## Business rules

1. **Mobile is unique within the company, but code and mobile are reserved differently.**
   `customerCode` behaves like `branchCode` — the native uniqueness check counts
   soft-deleted rows, because a shipment may quote the code. `mobile`'s check does
   **not** count them: a deleted customer's number is not a code anything else refers to,
   and re-registering the same person later must not be blocked by their own removed
   record. Two different native queries, deliberately.
2. **GST is mandatory only for `BUSINESS`.** Enforced in `Customer.applyInvariants()`,
   the same place `Branch` validates its own hours/coordinates — a domain invariant, not
   a cross-field bean-validation annotation, because it depends on another field's value.
3. **At most one default pickup and one default delivery address per customer — enforced
   by clearing, not rejecting.** Setting `isDefaultPickup=true` on one address unsets it
   on every other address of that customer in the same transaction
   (`CustomerAddressServiceImpl.clearOtherDefaults`). This is deliberately a "radio
   button", not a 409: a booking screen that flips a toggle expects the old one to just
   turn off, not to be told to turn it off itself first.
4. **A duplicate address is refused, but the definition of "duplicate" is narrow.**
   `CustomerAddress.duplicateKey()` compares `addressLine1` + `addressLine2` (trimmed,
   case-insensitive, whitespace-collapsed) + `pincodeId` — not the full geography stack.
   Two addresses that share every geography id but typed the street differently are not
   the same address; two that share the lines and pincode are, regardless of which
   optional geography ids happen to be filled in. Checked against **active** addresses
   only (a plain filtered query) — unlike code/name, a deleted address does not reserve
   its lines, so re-adding an identical one after a delete is allowed.
5. **Geography ids are validated, not trusted.** Each of `countryId`…`pincodeId`, if
   supplied, must resolve through the corresponding master service's `getById` — which
   already runs as the platform company internally for these global rows (decision 51),
   so this "just works" for an ordinary company user without this module knowing
   anything about `GlobalMasters.PLATFORM_COMPANY_ID`. A miss is translated from the
   master module's `ResourceNotFoundException` into a `BusinessRuleException` naming the
   field — a bad pincode id is this module's problem, not a 404 about someone else's row.
6. **There is no `DELETE /customers/{id}`.** The spec's endpoint list has none; only
   activate/deactivate. `CUSTOMER_DELETE` is a seeded permission code
   (`DefaultPermissionCatalog`, `V6`) with no service or endpoint behind it — the same
   "responsibility list ahead of the code" pattern this project has flagged before. An
   address, by contrast, *does* get a real `DELETE`, and is soft-deleted like everything
   else.

## RBAC

The "authorise on permissions" capstone (`MEMORY/AI_CONTEXT.md`'s *Next Task*) is still
not wired for any module, so this one follows the same convention every other module
does: `@PreAuthorize` against **JWT role authorities** (`com.courier.shared.security.Roles`
— `SUPER_ADMIN`, `PLATFORM_ADMIN`, `COMPANY_ADMIN`, `BRANCH_MANAGER`, `HUB_MANAGER`,
`OPERATOR`, `DRIVER`, `CUSTOMER`, `VIEWER`), **not** against the company-role catalogue
names (`BOOKING_OPERATOR`, `CUSTOMER_SERVICE`, …) that `DefaultRoleCatalog` seeds into
`user_company_roles`. Those company roles carry no JWT authority of their own yet (the
same gap AI_CONTEXT records for Branch's `BOOKING_OPERATOR`/`DELIVERY_OPERATOR`/
`ACCOUNTS`), so a counter clerk today reaches this module through the generic `OPERATOR`
JWT role.

| Action | Roles |
|---|---|
| Create / update a customer | `COMPANY_ADMIN`, `BRANCH_MANAGER`, `OPERATOR` |
| Activate / deactivate a customer | `COMPANY_ADMIN`, `BRANCH_MANAGER` |
| Read / search a customer | any authenticated user (`isAuthenticated()`) |
| Create / update an address | `COMPANY_ADMIN`, `BRANCH_MANAGER`, `OPERATOR` |
| **Delete** an address | `COMPANY_ADMIN` only — no seeded company role starts with `ADDRESS_DELETE` |

Verified live: `SUPER_ADMIN` gets a **403** creating a customer — the same invariant every
other operational module asserts (`SuperAdminBoundaryTest`'s reasoning; this module has no
dedicated boundary test of its own, but was exercised over HTTP with a real `ravi@legacy.test`
token during verification).

## Endpoints (9)

```
POST   /api/v1/customers
PUT    /api/v1/customers/{id}
GET    /api/v1/customers
GET    /api/v1/customers/{id}                       — full profile + every address
PATCH  /api/v1/customers/{id}/activate
PATCH  /api/v1/customers/{id}/deactivate
POST   /api/v1/customers/{id}/addresses
PUT    /api/v1/customers/{id}/addresses/{addressId}
DELETE /api/v1/customers/{id}/addresses/{addressId}
```

Sort whitelist: `customerCode`, `firstName`, `lastName`, `mobile`, `customerType`,
`status`, `createdDate`, `updatedDate`. `size` capped at 100, same convention as Branch
and Master Data.

## Frontend

`features/customer` (Angular 20, standalone, signals), API-only, no mock. List (server
pagination/sort/search/filter drawer/CSV export), create, edit (full-replacement PUT,
409 reloads), view (profile + gated action bar + address book). New model
`core/models/customer.model.ts`, `customer.service.ts` (CRUD + lifecycle + address
sub-resource calls + a small geography-cascade reader against `/global-masters/...`).
`AddressFormDialog` is a `MatDialog` (same shape as Branch's `AssignManagerDialog`) with
six cascading pickers (country → state → district → city → area → pincode); picking a
level clears and reloads everything beneath it. Nav gained a top-level "Customers" entry
(previously an aspirational stub under Masters pointing at a route that did not exist);
it carries no `roles` bridge, matching the backend's `isAuthenticated()` read policy —
`PermissionService.hasAnyRole([])` admits everyone signed in, the same rule `roleGuard`
already used for an empty `data.roles`.

**A real bug the browser check caught, not the test suite:** the address dialog's content
had no internal scroll region. `MatDialogContainer` does not scroll arbitrary projected
content by default — only a `<div mat-dialog-content>` does that, and this dialog used a
plain `<div>`. With six pickers plus the rest of the form, the "Add Address" button was
below the fold with no way to reach it on a normal-height viewport. Fixed with an explicit
`max-height: 85vh; overflow-y: auto` on the dialog's own root element. Neither `ng build`
nor the vitest suite could have caught this — it is a real-viewport, real-content-height
problem, which is exactly the class of defect this project's "verify by running it, not
just by testing it" rule exists for (see decision 31 and the Master Data verification
entry in `MEMORY/AI_CONTEXT.md`).

## Verified by running it (2026-07-30)

Booted against MySQL 8.0.46 on a second instance (port 8082, the shared dev database) per
`MEMORY/local-dev-environment.md`'s rule to never touch the user's own 8081/4200. `V14`
applied clean, `ddl-auto: validate` passed. Exercised over real HTTP with the
`asha@legacy.test` (`COMPANY_ADMIN`, `LEGACY_CO`) token: create (with and without a
supplied code), duplicate code, duplicate mobile (409), business customer with and
without GST (422/201), default-pickup exclusivity across two addresses, duplicate address
rejection (422), address delete, customer deactivate, search, a foreign id (404), and a
`SUPER_ADMIN` token refused on create (403). Then through the Angular console on a second
dev-server instance (port 4300 proxied at 8082, per the same isolation rule): list, view,
edit (code shown immutable, type/company name/GST hydrate correctly), and add-address —
which is where the scroll bug above was found and fixed live, then re-verified by
resubmitting the same form.

**Not exercised:** a `BRANCH_MANAGER`- or `OPERATOR`-scoped token (only `COMPANY_ADMIN`
and `SUPER_ADMIN` accounts exist in the dev fixtures today); the cross-company isolation
check, for the same long-standing reason every other module's doc gives — no active
`RIVAL_CO` user exists yet to attack from.

## Tests

19 backend unit tests (`CustomerServiceImplTest`, `CustomerAddressServiceImplTest`,
Mockito, mirroring `BranchServiceImplTest`/`StateServiceImplTest`'s shape): code
generation and normalisation, duplicate code/mobile, the GST rule both ways, stale-version
409, a foreign customer id, activate/deactivate idempotency, unknown-customer address
creation, invalid geography id, duplicate address, default-pickup exclusivity (and that
default-delivery is independent of it), and that an address of a different customer in
the *same* company still 404s. 12 new frontend tests (`customer.service.spec.ts` —
URL/verb/body shape per endpoint including the geography cascade's query param;
`customer-form.spec.ts` — the GST-for-BUSINESS rule client-side, and that a blank code is
sent as `null` rather than an empty string) plus one updated `navigation.config.spec.ts`
assertion for the nav entry's new shape. `mvn test`: **542/542**. `ng test`: **82/82**.
`ng build`: clean.
