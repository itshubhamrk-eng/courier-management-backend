# Follow-up Management

New top-level module `com.courier.modules.followup` + `features/follow-up/`, built
2026-08-18 on a full spec ("Build a Follow-up module for Branch users to track
operational tasks requiring manual action"). Scoped via `AskUserQuestion` before
starting: reuse Ticket Support's tables, or build a separate module mirroring its
pattern? User chose separate — a follow-up's due-date/reschedule semantics and
mandatory branch ownership are a different domain from a support ticket's SLA/
conversation/escalation, and conflating them risked corrupting Ticket's own SLA
bucket math (`ON_TRACK/WARNING/BREACHED`) with a status (`RESCHEDULED`) that means
something else entirely.

## Data model (`V44__follow_up.sql`)

Two tables: `follow_up` (company- **and** branch-owned — `branch_id` is `NOT NULL`,
unlike Ticket's optional `related_branch_id`) and `follow_up_history` (one combined
immutable timeline table — creation/status-change/reschedule/assignment/note — not
Ticket's two separate history tables, per the spec's own single-table ask).
`reference_type`/`follow_up_type` are the same `FollowUpType` enum
(CUSTOMER/SHIPMENT/DELIVERY/PAYMENT/EXCEPTION/GENERAL) applied twice — deliberately:
"what this follow-up is about" and "what kind of follow-up this is" are the same
question asked two ways. `reference_id`/`customer_id`/`shipment_id` carry no physical
FK, same cross-module convention as `Ticket.relatedShipmentId`.

`FollowUpStatus`: OPEN → IN_PROGRESS/RESCHEDULED/COMPLETED/CANCELLED, RESCHEDULED loops
back to IN_PROGRESS. COMPLETED/CANCELLED are terminal — `FollowUpServiceImpl.update`/
`changeStatus`/`reschedule` all refuse once terminal ("cannot be edited except through
history"). `RESCHEDULED` is reachable only through the dedicated `POST .../reschedule`
endpoint — `PATCH .../status` explicitly rejects it.

## Backend

`FollowUpServiceImpl` mirrors `TicketServiceImpl`'s hand-rolled scoping shape (no
generic "requireVisible" helper in this codebase). Key difference: **no SUPER_ADMIN
cross-tenant view at all** — a follow-up is purely company/branch operational data,
so every method requires a bound company via `CompanyContext.requireCompanyId()`.
Non-admin (branch) callers are scoped to their own branch (`ownBranch()` — placed-at
or managed-by) plus anything they created or are assigned, mirroring Ticket's
requester/assignee exception. `resolveBranchForWrite` enforces "branch users can only
create for their own branch"; `requireAssigneeInBranch` enforces "assigned user must
belong to the branch" (placed at it or manages it) on both create and assign.

`FollowUpDirectoryPort` (module-owned interface) / `company.infrastructure
.FollowUpDirectory` (adapter) is a smaller copy of `TicketDirectoryPort`/
`TicketDirectory` — same hex-architecture seam, just the subset Follow-up needs
(no SLA settings, no ticket-number sequence).

**Overdue detection** is computed live, not stored: `FollowUpMapper.toResponse`
sets `overdue = !terminal && dueDate < now`, same "compute at read time" posture as
`TicketServiceImpl.slaBucket`. **Notifications** reuse Ticket Support's existing
`Notification`/`NotificationService` infrastructure rather than building a second
architecture (explicit spec instruction) — `Notification` gained a nullable
`follow_up_id` column alongside its existing `ticket_id` (mutually exclusive), a new
`NotificationService.notifyFollowUp(...)` overload, and four new `NotificationType`
constants (FOLLOWUP_ASSIGNED/DUE_TODAY/OVERDUE/URGENT). `FollowUpSweepJob`
(`@Scheduled(fixedDelay=1h)`) is this codebase's third scheduled job (after
`ShipmentSlaSweepJob`/`TicketSlaSweepJob`) — fires OVERDUE/DUE_TODAY once each per
due date via `overdueNotified`/`dueTodayNotified` idempotency flags on the entity,
reset whenever the due date moves (update or reschedule). URGENT fires immediately
on assignment instead of via sweep, since priority is already known at that point.

RBAC is role-based (`Roles.COMPANY_ADMIN`/`BRANCH_MANAGER`/`HUB_MANAGER`), same as
every other recent module — the "authorise on permissions" capstone
(`[[responsibility-list-ahead-of-code]]`) is still not built, so `FOLLOWUP_VIEW/
CREATE/UPDATE/ASSIGN/COMPLETE` are conceptual permission names mapped to roles in the
frontend nav/route guards, not enforced permission-catalogue codes — same posture as
every module since Ticket Support. `FOLLOWUP_DELETE` has no endpoint behind it
(the spec's own API list has no `DELETE /follow-ups/{id}`) — same "seeded-but-unused"
precedent as `CUSTOMER_DELETE`/`RATE_DELETE`.

Dashboard integration is a **separate, self-contained endpoint**
(`GET /follow-ups/dashboard` → `FollowUpDashboardStats{overdue,dueToday,upcoming,
urgent}`), not folded into `DashboardSummaryResponse` — the frontend's
`FollowUpWidget` fetches its own counts, same posture as `TrackBox`, so
`DashboardServiceImpl`/`DashboardController` needed zero changes.

## Frontend

`features/follow-up/`: `follow-up-list.ts` (search/filter/sort/paginate, hydrates
its filters from query params so the dashboard widget's tiles can deep-link),
`follow-up-create.ts` (routed, reads `shipmentId`/`customerId`/`branchId` query
params — same pattern as `ticket-create.ts`), `follow-up-edit.ts` (full-field PUT,
409-reload-on-stale-version), `follow-up-detail.ts` (info sidebar + Assignment/
Status/Reschedule cards, all hidden once terminal), `components/follow-up-history
-timeline.ts` (copies `TicketConversationTimeline`'s vertical-line markup).
`features/dashboard/components/follow-up-widget.ts` — four clickable tiles
(Overdue/Urgent/Due Today/Upcoming), each navigating to `/follow-ups` with a
pre-applied filter; mounted on the Dashboard page next to Track Shipment (hidden for
the PLATFORM profile, same as Track Shipment — a follow-up has no platform-wide
view). New nav section "Follow-ups" (order 6.4, just above Ticket Support).

Cross-page entry points: Shipment Details gained a "Create Follow-up" link next to
its existing "Raise Ticket" one; Customer Details gained a "Create Follow-up" button
next to "Raise Ticket". Both prefill `shipmentId`/`customerId` + the record's own
branch, same query-param convention Ticket Support already established.

`NotificationMenu`/`notification-feed.service.ts`/`ticket.model.ts`'s
`NotificationType`/`AppNotification` all extended (not duplicated) to carry
`followUpId` alongside `ticketId` — clicking a follow-up notification navigates to
`/follow-ups/:id`.

## Verification

`mvn test`: 782/782 (761 baseline + 21 new `FollowUpServiceImplTest` cases — CRUD,
branch-scoped create/foreign-branch refusal, assignee-must-belong-to-branch, stale
version, illegal/RESCHEDULED-via-status-refused transitions, COMPLETED stamping,
reschedule due-date swap, BRANCH_MANAGER cross-branch assign refusal, assignment
notification, notes, history, branch/company isolation, assignee-sees-across-branches
exception, dashboard bucket counts). `tsc --noEmit -p tsconfig.app.json` and
`ng build` both clean. **Not verified live** in a browser or against real MySQL this
session — no DB boot attempted; verification stopped at the same
compile/build/unit-test bar most modules pass before their first live check, honestly
flagged rather than claimed. No frontend `.spec.ts` files were added, matching Ticket
Support's own precedent (that module has none either, despite `[[frontend-test-runner]]`
existing since 2026-07-28).

## Known gaps, flagged not guessed

- `FOLLOWUP_DELETE` permission name exists in the spec but no endpoint implements it
  (spec's own API list omits `DELETE /follow-ups/{id}`).
- Permission-code enforcement (`FOLLOWUP_VIEW` etc. as real `Permission` catalogue
  rows) not added — consistent with every module since Ticket Support, not a
  regression specific to this one.
- Live UI/browser verification and a real MySQL boot were not performed this session.
