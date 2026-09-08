# POD Auto Verification

**Status:** DONE (v0.30.1, 2026-08-20). New package `com.courier.modules.pod`, migrations
`V48`/`V49`. Integrated into Shipment Movement's own delivery flow
(`com.courier.modules.shipment`) rather than replacing it — see "Integration point" below.

**2026-09-08 update — POD approval now gates delivery commission; company-level upload;
POD Dashboard pie:**
1. **Commission crediting moved off `deliver()` onto POD approval.** `V59` adds
   `shipments.pod_approved`/`commission_credited`. `ShipmentServiceImpl
   .creditDeliveryCommissionsIfEligible(shipment, companyId)` (extracted from `deliver()`'s
   old unconditional block) only fires `DrsChargeApplicable`/
   `DeliveryWeightCommissionApplicable`/`DeliveryCommissionEarned` when `status ==
   DELIVERED && podApproved && !commissionCredited` — called from `deliver()` itself and
   from new `ShipmentService.markPodApproved(shipmentId)`, whichever order the two
   conditions land in. **This is universal, on direct user confirmation**: a shipment that
   never gets a POD run never earns this commission, even though POD itself is still not
   mandatory to complete delivery (2026-09-04's own point 2 is untouched — "Complete
   Delivery" still shows regardless of verification status). `CodCollectedAtDelivery` (cash
   actually collected, a debit not a commission) is unaffected. `PodVerificationServiceImpl`
   calls `markPodApproved()` from `verify()` (AI resolves straight to PASS) and `review()`
   (approve).
2. **Approve/reject narrowed to `COMPANY_ADMIN`.** `REVIEWERS` was `hasAnyRole(COMPANY_ADMIN,
   BRANCH_MANAGER)`; now `hasRole(COMPANY_ADMIN)` only — on direct user request ("approve or
   reject access should be company level"), since approval now moves money.
   `BRANCH_MANAGER` keeps `WRITERS` (capture/upload, view the worklist); `pod-review.ts`
   hides the Approve/Reject form for anyone else client-side, backend is the real gate.
3. **Company-level upload, no branch context.** New `uploadByCompany(shipmentId, command)`
   — `COMPANY_ADMIN` only, works against any of the company's own `OUT_FOR_DELIVERY`/
   `DELIVERED` shipments regardless of branch, skips the AI provider entirely and always
   writes `PASS` (`aiProvider = "company-direct"`) — the company vouching for it directly,
   on direct request ("if uploaded by company then it should be direct approved"). New `POST
   /api/v1/pod/company-upload/{shipmentId}`, new frontend screen
   `pod-company-upload.ts` (finds the shipment by tracking/shipment number via the existing
   `GET /shipments/track/{trackingNumber}`), new nav leaf "Upload POD (Company)".
4. **POD Dashboard pie.** `DashboardServiceImpl` gained `podOverview` (pending upload /
   pending verification / approved / rejected) on both `CompanyOverviewResponse` (every
   branch) and `BranchOverviewResponse` (this branch), plus `rejectedPods` on the branch
   response — a small worklist, each row linking to Delivery's own re-upload flow. Candidate
   set: every `OUT_FOR_DELIVERY`/`DELIVERED` shipment, current state not month-bound (same
   philosophy `PENDING_DELIVERY` already uses) — one missing from
   `latestByShipmentIds` has never had a POD run (`pendingUpload`).

**Verified live (2026-09-08, second session)**: reported symptom "Approve/Reject button not
visible on POD review page at company login" — not a bug, working as designed. Root cause:
tester was logged in as `pune@gmail.com` (`BRANCH_MANAGER`), and the same-day change above
narrowed the Decision form to `COMPANY_ADMIN` only. Confirmed via throwaway `:8082`/`:4300`
instances (real `:8080`/`:4200` untouched) + a fixture `pod_verification` row (REVIEW/68,
`ai_provider='manual-fixture'`, shipment `26080000023`) inserted directly since no real
`REVIEW` row exists yet (still blocked on the storage-backend gap below — fixture row kept
per [[keep-test-data-in-dev-db]]): logging in as `first.admin@gmail.com` (`COMPANY_ADMIN`,
`COMPANY-C1`) does render the Approve/Reject buttons on that row's POD Review detail page;
`pune@gmail.com` sees the "Awaiting a company-level decision" caption instead, exactly as
`pod-review.ts:218`'s `canDecide()` gate intends. This also closes the "not verified live"
gap the 2026-08-20 note below left open for the POD-Review-approve-reject click path.

**Same session, continued — `decide()`'s actual POST `.../pod/review` call, and the
commission-credit side effect, also verified live.** Approved the fixture row as
`first.admin@gmail.com` via curl (same throwaway `:8082`): 200, `verificationStatus` flipped
`REVIEW` -> `PASS`, `reviewedBy`/`reviewedAt`/`reviewRemarks` stamped. `shipments.pod_approved`
and `commission_credited` both flipped `0` -> `1` on `PUNE-000017` (tracking `26080000023`,
already `DELIVERED`), and `wallet_transactions` gained two real rows at the same instant —
`DRS` ₹2 and `DWC` (delivery weight commission) ₹15, both `remarks: "... for shipment
PUNE-000017"` — confirming `markPodApproved()` -> `creditDeliveryCommissionsIfEligible()`
fires correctly end-to-end on approval, not just that the flags flip. First real, non-fixture
confirmation of this session's own point 1 (commission-on-approval) actually crediting money.

**Verified live (2026-08-20)**: both the Delivery page's AI-verify flow and the new
company-upload screen reach and correctly stop at the pre-existing "no storage backend
configured" gap (`FileStorageConfig`) — same accepted limitation the original build
already carried, still the only blocker to the real `PASS`/`REVIEW`/`FAIL` path and the
commission-credit event firing. The POD Dashboard pie renders correctly live for both a
branch-scoped and a company-wide caller. Found and fixed one real bug in the process:
`pod-company-upload.ts`'s search box was a bare `FormControl` inside a `<form
(ngSubmit)="find()">` with no form directive, so `ngSubmit` never bound and clicking the
`type="submit"` Find button triggered a native, un-prevented page reload instead —
wrapped the search box in its own `searchForm: FormGroup` instead. See `CHANGELOG.md`'s
2026-09-08 entry for the full writeup.

See `CHANGELOG.md`'s own 2026-09-08 entry for the full file list. The rest of this document
(flow diagram, RBAC table's original REVIEWERS row, API table) is otherwise still accurate
except where superseded by the four points above.

**2026-09-04 update — four fixes, in response to real usage feedback:**
1. **AI was never a content check.** Reported live: a random unrelated photo scored 85+/100
   (auto-PASS) since the heuristic scorer never looked at pixel *content*, only brightness/
   blur/resolution + string-length checks the operator's own form input trivially satisfies.
   Added `VisionPodVerificationProvider` — a real vision-AI call (Anthropic Messages API,
   `RestClient`, no SDK — same "one endpoint doesn't justify a dependency" call
   `RazorpayPaymentGateway` makes) that actually assesses whether the photo plausibly shows
   a genuine delivery/parcel/signature capture. `pod.ai.enabled` (boolean) renamed to
   `pod.ai.provider` (`heuristic` default | `vision` | `disabled`) — a **breaking env-var
   rename** (`POD_AI_ENABLED` → `POD_AI_PROVIDER`), since `application.yml` set the old key
   explicitly. New `pod.ai.vision.*` config (`POD_AI_VISION_API_KEY`/`_BASE_URL`/`_MODEL`).
   The AWB/QR-mismatch and duplicate-photo-hash hard-fail rules are shared between both
   providers via new `PodGroundTruthRules` (package-private) — this platform's own policy,
   not something either scorer should reproduce or a vendor could know independently.
2. **Delivery is explicitly informational-only now, on direct user instruction** ("AI
   verification should not be mandatory for delivery"). `delivery.ts`'s "Complete Delivery"
   button is no longer gated on `verificationStatus === 'PASS'` — it now shows for any
   result (PASS/REVIEW/FAIL), with REVIEW/FAIL keeping their existing secondary action
   (Check Review Status / Upload New POD) as an option, not a block. The flow diagram above
   is now stale for the *delivery-blocking* part — REVIEW/FAIL no longer stop delivery, they
   just carry a hint that a ticket was raised. `ShipmentServiceImpl.deliver()` itself:
   **still untouched**, exactly as designed originally — it never read `PodVerification`
   before this change either, so there was no backend gate to remove.
3. **Auto-ticket on REVIEW or FAIL.** `PodVerificationServiceImpl.verify()` now raises a
   support ticket (category `"POD Verification Issue"`, seeded by `V57`) when the result is
   REVIEW or FAIL — FAIL at `HIGH` priority, REVIEW at `MEDIUM`. Reuses the `support`
   module's existing "auto-raise from business rule" pattern (`TicketService.raiseSystemTicket`,
   the same one `ShipmentSlaSweepService` already used for SLA breaches), extended with
   `raiseSystemTicketIfNoneOpen` — a new dedup guard (one open ticket per shipment per
   category) so a courier retrying a FAIL a few times via "Upload New POD" doesn't spam a
   fresh ticket every attempt. `raiseSystemTicket` also gained a `statusHistoryRemark`
   parameter (was hardcoded to `"Auto-raised: SLA breach"` — now every caller names its own
   trigger). Ticket-raise failures are caught and logged, never propagated — a support-module
   hiccup must not roll back the POD verification or block delivery (point 2's own logic
   extended to its side effects).
4. **POD Review page rewritten** from "REVIEW-status worklist only" (no photo preview, raw
   `target=_blank` links) to a paginated table of **every delivered shipment**, whether or
   not a POD was ever run against it. New endpoint `GET /api/v1/pod/delivered` (batch-fetches
   latest `PodVerification` + POD `ShipmentAsset`s per shipment id, same "page then batch-join"
   idiom `ShipmentController.list` already uses for `netAmounts`/`charges`/`deliveredAt`) and
   a new click-to-enlarge `ImagePreviewDialog` (`DialogService.previewImage`). The
   Approve/Reject decision form now only renders for a REVIEW-status row — every other row
   (PASS/FAIL/no POD at all) is view-only, since `review()` only ever accepted REVIEW anyway.

The flow diagram and the rest of this document describe the **original 2026-08-20 build**
and are otherwise still accurate (thresholds, RBAC, entity shape, API surface below except
for the new `GET /pod/delivered` endpoint) — read both this update and the sections below
together.

## Purpose

AI-scored gate in front of the existing `deliver()` action, on direct full-spec request.
Delivery flow, per the brief:

```
OUT_FOR_DELIVERY -> upload POD -> AI Verification -> PASS -> Complete Delivery
                                                 \-> REVIEW -> manual approve/reject
                                                 \-> FAIL -> upload a new POD
```

**As of 2026-09-04 (see update above), this diagram is aspirational for the "blocks
delivery" part only** — REVIEW/FAIL no longer prevent "Complete Delivery" from being clicked;
they only add a secondary action (review/re-upload) plus an auto-raised ticket. The
AI-scoring branch (PASS/REVIEW/FAIL determination itself) is unchanged.

**AI never itself updates a shipment's status.** `ShipmentServiceImpl.deliver()` — the
only code path that ever writes `DELIVERED` — is completely untouched by this module. POD
Auto Verification only ever writes `pod_verification` rows; the frontend decides whether to
call the existing `deliver()` endpoint based on the verification's own status, exactly as
it decided when to call `deliver()` before this module existed (previously: as soon as a
photo/signature was captured, no gate at all).

## Why a heuristic provider, not a real AI vendor

There is no AI/vision vendor credential configured in this dev environment — the same
class of gap `NotificationPort`/SMTP and (elsewhere) `FileStoragePort`/S3 already carry
honestly in this codebase rather than fabricating a working integration. `PodVerificationProvider`
is the abstraction the brief asked for ("do not couple business logic directly to one AI
provider... AI should return structured JSON... do not allow arbitrary AI text to control
business actions"); the only implementation shipped, `HeuristicPodVerificationProvider`, is
a **deterministic local scorer**, not a trained model:

- Decodes the photo with the JDK's own `ImageIO` (no new dependency) and scores plain
  structural signals — average luminance (darkness), a gradient-magnitude proxy (blur, not
  a true Laplacian-variance detector), and pixel dimensions (resolution).
- Signature presence is presence-based (bytes were submitted at all), not stroke-detected.
- **No OCR.** `detectedReceiverName` is a passthrough of what the delivery user typed;
  `detectedAwb` is a passthrough of the claimed value; `detectedDate` is the verification
  timestamp. None of these are read out of the pixels.
- The one genuinely useful automated cross-check that needs no OCR at all: the claimed
  AWB/shipment number is compared against **this platform's own database record** for the
  shipment being delivered — ground truth already known server-side, catching a POD
  captured against the wrong shipment.
- Duplicate-POD detection is a real SHA-256 hash comparison against every prior
  verification's photo hash in the same company (`pod_verification.pod_hash`, not in the
  brief's own field list — added because "Duplicate POD" was an explicit requirement and
  needs something to compare against).
- Tampering is a **weak, honestly-labelled** signal (implausibly high compression ratio for
  the declared resolution — "possible edited file (unverified)"), never asserted as proof.

Swapping in a real vision/OCR provider (AWS Rekognition/Textract, Google Vision, an LLM
multimodal call) later is a second `PodVerificationProvider` implementation and a config
change — no caller of `PodVerificationService` changes. `pod.ai.enabled=false` swaps in
`UnavailablePodVerificationProvider` instead, simulating an unreachable/unconfigured
vendor — exercises the brief's own explicit rule ("if AI provider is unavailable, do NOT
automatically mark delivery as verified — move to MANUAL_REVIEW") for real.

## Shape

```
pod_verification (own table, CompanyOwnedEntity — V48, pod_hash widened CHAR->VARCHAR in V49)
├── shipmentId            no physical FK — cross-module id, same convention as
│                          manifests.vehicle_id / shipments.booking_branch_id
├── podDocumentId          ShipmentAsset.id of the primary (photo) document — also no
│                          physical FK
├── verificationStatus     PASS | REVIEW | FAIL — no PENDING/processing state, verify()
│                          runs synchronously and always resolves before the request returns
├── verificationScore      0-100
├── verificationReasons    newline-joined TEXT (no list-column convention existed yet in
│                          this codebase; PodVerification.reasons()/reasons(List) hide it)
├── detectedReceiverName, detectedAwb, detectedDate, signatureDetected, imageQuality
├── podHash                SHA-256 hex of the photo bytes — duplicate detection
├── aiProvider, aiModel    "heuristic-local"/"structural-v1", or "unavailable"/"n/a"
├── verifiedAt
├── reviewedBy, reviewedAt, reviewRemarks   set only by POST .../pod/review
```

`ShipmentService` gained one new method for this module: `attachPodAsset(shipmentId, kind,
url)` — persists a `ShipmentAsset` (POD/PHOTO or POD/SIGNATURE) **immediately**, unlike
`deliver()`'s own `recordAsset` which only fires once delivery actually commits. POD Auto
Verification needs a durable captured document *before* the delivery decision exists — a
`REVIEW` or `FAIL` result still needs the photo on record for the eventual manual
re-review / audit trail.

## Integration point — reuses the existing document store, doesn't duplicate it

`POST /shipments/{id}/pod/verify` calls `ShipmentService.uploadPodFile` (the same
`FileStoragePort`/S3 seam POD capture has used since v0.17.9) to get a URL, then
`attachPodAsset` to persist it, then runs the AI scorer. **In this dev environment
`FileStoragePort` has no backend configured** (`FileStorageConfig`'s own accepted,
documented gap — same one the original POD-upload endpoint has always carried), so `verify()`
correctly reaches the AI-analysis step and only then refuses with "no storage backend is
configured for this deployment" — confirmed by live boot, see Verification below. This is
the same graceful-degradation shape Ticket Support's attachment upload already
demonstrated, not a new kind of gap.

## Business rules (unit-tested, `PodVerificationServiceImplTest`/`HeuristicPodVerificationProviderTest`)

- `verify()` requires the shipment to be `OUT_FOR_DELIVERY`; refuses otherwise, before any
  AI call or upload.
- A missing photo is refused before any AI call. Signature is optional.
- A duplicate-hash match or a tampering signal **forces `REVIEW` even at a high score** —
  a safety override the score thresholds alone can't skip.
- Thresholds are never hardcoded: `pod.verification.auto-verify-threshold` (`POD_AUTO_VERIFY_THRESHOLD`,
  default 85) and `pod.verification.manual-review-threshold` (`POD_MANUAL_REVIEW_THRESHOLD`,
  default 60), `PodVerificationProperties`, same `@ConfigurationProperties` shape
  `PricingProperties` already uses.
- `review()` only transitions a `REVIEW`-status row — approve to `PASS`, reject to `FAIL` —
  refused otherwise ("illegal transition", same shape every other module's lifecycle
  actions use). Stamps `reviewedBy`/`reviewedAt`/`reviewRemarks`.
- An unavailable AI provider resolves to `REVIEW`, never a silent `PASS`.
- Company isolation is automatic (`ShipmentService.getById`'s existing scoping — confirmed
  live: a foreign company's shipment id 404s on `verify()`).

## RBAC — role-based, same posture as every module since Ticket Support

The brief named five permission codes (`POD_VIEW`/`POD_UPLOAD`/`POD_VERIFY`/`POD_REVIEW`/
`POD_APPROVE`). Checked `PermissionModule`/`DefaultPermissionCatalog` before adding rows for
them: **Ticket Support, Follow-up Management and Vehicle fleet's own permission-shaped
actions never got catalogue entries either** — this project's "authorise on permissions"
capstone is still not built (`BACKLOG.md`), and every module since 0.28.0 gates on JWT role
names directly instead. POD follows the same precedent rather than growing a second,
unused vocabulary:

| Brief's code | `@PreAuthorize` gate | Who |
|---|---|---|
| `POD_UPLOAD` + `POD_VERIFY` | `WRITERS` | `COMPANY_ADMIN`, `BRANCH_MANAGER`, `OPERATOR` — same tier `deliver()`/`uploadPodFile` already use |
| `POD_VIEW` | `READERS` | `isAuthenticated()` |
| `POD_REVIEW` + `POD_APPROVE` | `REVIEWERS` | **2026-09-08: `COMPANY_ADMIN` only** (was also `BRANCH_MANAGER`) — approval now credits delivery commission, so narrowed further than `WRITERS`; also gates the new `uploadByCompany` |

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/shipments/{id}/pod/verify` | multipart `photo` (required), `signature` (optional), `receiverName`, `awbNumber`, `shipmentNumber`, `deliveryDateTime` |
| `GET` | `/api/v1/shipments/{id}/pod/verification` | latest run for the shipment, 404 if none |
| `POST` | `/api/v1/shipments/{id}/pod/review` | `{approve, remarks}` — REVIEW-only, `COMPANY_ADMIN` only (2026-09-08) |
| `POST` | `/api/v1/pod/company-upload/{shipmentId}` | **added 2026-09-08** — multipart `photo`/`signature`/`receiverName`, `COMPANY_ADMIN` only, no branch context, always auto-approved |
| `GET` | `/api/v1/pod/pending-review` | **added beyond the brief's own API list** — the Manual Review screen's worklist; without it there is no way for a reviewer to discover what needs deciding |
| `GET` | `/api/v1/pod/delivered` | **added 2026-09-04** — every DELIVERED shipment with its latest POD verification if any; the rewritten POD Review table's source. Same filters as `GET /shipments` (branch/date-range/search), status always forced to DELIVERED |

## Frontend

`features/shipment-movement/delivery.ts` reworked: photo/signature file pickers -> "Run AI
Verification" -> a result card (score, receiver/AWB/date/signature/image-quality grid,
reasons list) -> `PASS` shows the existing "Complete Delivery" button (calls the unchanged
`deliver()`, with `signatureUrl`/`photoUrl` now `null` since the documents are already
attached by `verify()`), `REVIEW` shows a "Check Review Status" refresh + a hint that a
supervisor must decide, `FAIL` shows "Upload New POD" to recapture. Selecting a shipment
also silently hydrates any already-existing verification (`PodService.getLatest`,
`SILENT_ERRORS` context — a fresh shipment with no run yet is the common case, not an
error).

New `features/shipment-movement/pod-review.ts` — the Manual Review screen: worklist ->
select -> photo/signature thumbnails (real `<img>` tags against the stored URLs, opens the
original in a new tab) -> AI result grid + reasons -> Approve/Reject with an optional
remark. New nav leaf "POD Review" under Operations (`COMPANY_ADMIN`/`BRANCH_MANAGER` only,
narrower than the rest of Operations' delivery-desk leaves), new route
`/movement/pod-review`.

## A real, live-found platform bug fixed in passing

`GlobalExceptionHandler` had a handler for a missing plain `@RequestParam` (`Missing­ServletRequestParameterException`)
but none for a missing **multipart** part (`MissingServletRequestPartException`) — calling
`verify()` with no `photo` file 500'd as "An unexpected error occurred" instead of a clean
400. This is a pre-existing gap in shared infrastructure, not new to this module (the
original `uploadPodFile`/`uploadShipmentImage` endpoints have carried the same latent gap
since 0.17.9/booking-photo — just never tripped because every prior caller always sent a
file). Fixed by adding the missing handler, mirroring the existing one's exact shape.

## Verified live (2026-08-20, MySQL 8.0.46, real `courier_db`, throwaway `:8082`/`:4300`)

`:8100`/`:4200` (the real dev instances, run by a concurrent session — see Concurrent-session
note below) untouched throughout. As `pune@gmail.com` (`BRANCH_MANAGER`, `COMPANY-C1`) over
real HTTP: missing-photo now returns a clean 400 (confirming the `GlobalExceptionHandler`
fix); wrong-status (`BOOKED`) refused with the exact business-rule message; a real
`OUT_FOR_DELIVERY` shipment (`PUNE-000001`) correctly ran the full pipeline (status check,
duplicate-hash check, AI analysis) and stopped exactly at the pre-existing, accepted
"no storage backend is configured for this deployment" gap — proving the AI step itself
executed without error, not that it was skipped; `GET .../pod/verification` 404s when no
run exists; `GET /pod/pending-review` returns an empty list cleanly; a foreign company's
shipment id 404s on `verify()` (company isolation). Through the Angular console: logged in,
confirmed the new "POD Review" nav leaf renders and its empty state ("Nothing awaiting
review.") loads with no console errors; `Delivery` page itself loads correctly with the
reworked form present (no fixture existed at Pune branch in `OUT_FOR_DELIVERY` this
session to click-test the file pickers/AI-verification button live — not fabricated,
flagged honestly).

**Not verified live**: the actual `PASS`/`REVIEW`/`FAIL` happy path end to end (blocked on
no S3/file-storage backend in this dev environment, an accepted pre-existing gap, not a
defect in this module — same as the original POD-upload endpoint's own long-standing "Not
verified live" note), the Delivery page's photo-picker/"Run AI Verification"
button/POD-Review-approve-reject click path in the browser (no `OUT_FOR_DELIVERY` fixture
at the logged-in branch this session). `mvn test` 835/835 (22 new: 13
`PodVerificationServiceImplTest`, 9 `HeuristicPodVerificationProviderTest` — real JPEG/PNG
bytes generated with `java.awt.Graphics2D`, not fixture files), `tsc --noEmit -p
tsconfig.app.json`/`ng build --configuration development` clean. `ng test`: one
pre-existing, unrelated `navigation.config.spec.ts` failure (`No nav node with id
"reports-dashboard"`), confirmed present before this task's own changes too (git-stash
comparison) — not touched, not caused by this module.

## Concurrent-session note

This working tree had at least two other sessions' uncommitted work present throughout
this task — E-Way Bill Management (`com.courier.modules.ewaybill`, `V47`, claimed
version `0.30.0`) and a Razorpay-per-company-config feature (`V46`,
`CompanyRazorpayConfig`) — neither touched by this task. One of those sessions (or a third)
also independently found and fixed a real schema bug in *this* module partway through this
task: `V48` declared `pod_hash CHAR(64)` but the entity (`@Column(length = 64)`, no
`columnDefinition`) maps a `String` to `VARCHAR` by default — a genuine
`ddl-auto: validate` mismatch this session's own unit tests couldn't catch (no
Testcontainers boot in this module's test suite). Fixed forward-only as `V49`, found
already-applied against the real dev `courier_db` (schema at v49) by the time this task's
own live-boot verification ran. Left as-is rather than folding into `V48` — same "don't
edit a merged/already-applied migration" discipline this project follows everywhere else.

## Not exercised

- A `BOOKING_OPERATOR`/`DELIVERY_OPERATOR`-scoped JWT token specifically (the pre-existing
  "company role, not JWT authority" gap this project has flagged since Branch RBAC)
- The full `PASS`/`REVIEW`/`FAIL` happy path against a real configured object store
- Concurrent `verify()` calls against the same shipment
- A real, non-heuristic AI/vision provider — `PodVerificationProvider` is built to accept
  one, none is wired in this codebase

## Deliberately not built

A standalone Manifest/Vehicle-style admin CRUD screen for `pod_verification` rows (not
asked for — the two screens the brief names, Delivery's own capture flow and the Manual
Review screen, are both built). Persisted DB-level enforcement that `deliver()` itself
requires a `PASS` verification to exist — the brief's own flow diagram, and this
implementation, leave that gate at the frontend (the same trust boundary
`shipment-create.ts`'s E-Way-Bill-mandatory chip uses: UX-level, with the real business
rule enforced server-side wherever it already lived). Adding that server-side gate to
`deliver()` itself was considered and deliberately deferred — it would mean
`ShipmentServiceImpl` taking on a dependency on the new `pod` module, the same kind of
cross-module coupling this project avoids introducing without being asked (see
`shipment-movement.md`'s own note on the one-directional Manifest/Shipment dependency).
