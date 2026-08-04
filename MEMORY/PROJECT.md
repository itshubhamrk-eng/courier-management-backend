# PROJECT

## What this is

A **multi-tenant Courier / Logistics SaaS backend**. One deployment serves many
independent courier companies ("companies"). Each tenant manages its own branches,
staff, customers and shipments, and can never see another company's data.

## Product Scope

### In scope

| Capability | Description |
|---|---|
| Subscription plans | `SUPER_ADMIN` catalogue of tiers, prices, quotas and feature flags |
| Company onboarding | `SUPER_ADMIN` creates a company: company id, plan, default roles, settings and first admin. Self-serve signup is not built yet |
| Identity & access | Email+password login, JWT access/refresh, RBAC, password reset |
| Company profile | Branches, warehouses, service areas, rate cards |
| Shipment lifecycle | Booking -> pickup -> in-transit -> out-for-delivery -> delivered/RTO |
| Tracking | Public tracking by AWB number, scan history |
| Audit | Immutable trail of who changed what, when, from where |

### Explicitly out of scope (v1)

- Payments / invoicing engine
- Route optimisation, driver mobile app
- Real-time GPS streaming
- Multi-currency, multi-language

## Actors & Roles

| Role | Scope | Can do |
|---|---|---|
| `SUPER_ADMIN` | The platform itself | Own the subscription plan catalogue: pricing, quotas, feature flags. No tenant impersonation |
| `PLATFORM_ADMIN` | Cross-company | Manage companies, impersonate via `X-Company-ID`, view platform metrics |
| `COMPANY_ADMIN` | One company | Full control of their company: users, roles, branches, rates, shipments. Granted to the first user created with a company |
| `TENANT_ADMIN` | One tenant | The older name for the same scope, kept because issued tokens carry it |
| `BRANCH_MANAGER` | One branch | Manage shipments + staff of their branch |
| `HUB_MANAGER` | One hub | Inbound/outbound movement, scans, vehicles |
| `OPERATOR` | One branch | Book shipments, add scans |
| `DRIVER` | Assigned | Update delivery status of assigned shipments |
| `CUSTOMER` | Self | Book, view own shipments, track |
| `VIEWER` | One company | Read-only, for auditors, finance and support |

Roles are carried as a `roles` claim in the JWT. Authorisation is enforced with
`@PreAuthorize` at the service boundary, never only at the controller.

## Non-Functional Requirements

| NFR | Target |
|---|---|
| p95 API latency | < 200 ms (read), < 400 ms (write) |
| Availability | 99.5% |
| Company isolation | Hard requirement — a cross-company read is a **Sev-1** |
| Auditability | Every mutation attributable to a user + request id, retained 1 year |
| Passwords | BCrypt cost 12, never logged |
| Tokens | Access 15 min, refresh 7 days, refresh rotation + Redis denylist |
| Migrations | Forward-only, reviewed, never edited after merge |

## Environments

| Env | DB | Notes |
|---|---|---|
| `local` | Docker Compose MySQL 8.4 + Redis 7 | `docker compose up -d` |
| `dev` | Managed MySQL | Flyway auto-migrate on boot |
| `prod` | Managed MySQL, read replica | Flyway run as a separate job, `validate-on-migrate` |

Config precedence: `application.yml` <- `application-<profile>.yml` <- env vars.
**No secrets in the repo.** `JWT_SECRET`, `DB_PASSWORD`, `REDIS_PASSWORD` are env-only.

## Glossary

| Term | Meaning |
|---|---|
| **Tenant / Company** | A courier company subscribing to the platform, and the root of all data ownership. Modelled as `Company`; its `companyId` is the discriminator on every owned row. |
| **AWB** | Air Waybill number — the human-facing shipment tracking id. Unique per company. |
| **Branch** | A physical office/hub belonging to a company. |
| **Scan** | A timestamped status event on a shipment. |
| **RTO** | Return To Origin — undeliverable shipment sent back to shipper. |
| **Consignor / Consignee** | Sender / receiver of a shipment. |

## Repository Layout

```
courier-management/
├── MEMORY/                      <- source of truth, read before coding
├── docker/
│   └── docker-compose.yml       <- MySQL 8.4 + Redis 7 (+ api under a profile)
├── backend/                     <- the Spring Boot API
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/courier/
│       │   ├── shared/          <- cross-cutting foundation
│       │   └── modules/         <- business features (package by feature)
│       ├── main/resources/
│       │   ├── application*.yml
│       │   └── db/migration/    <- Flyway V__*.sql, forward only
│       └── test/java/com/courier/
├── .env.example
└── .gitignore
```

`backend/` is a sibling folder rather than the repository root so a `frontend/`
can be added later without moving anything.

## How to run

```bash
cp .env.example .env                 # then fill in JWT_SECRET
docker compose -f docker/docker-compose.yml up -d mysql redis

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Swagger UI: http://localhost:8080/swagger-ui.html
# Health:     http://localhost:8080/actuator/health
```

Run the whole stack in containers instead:

```bash
export JWT_SECRET=$(openssl rand -base64 48)
docker compose -f docker/docker-compose.yml --profile app up -d --build
```
