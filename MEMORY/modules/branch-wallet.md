# Module: branch-wallet (Branch Wallet)

**Status:** DONE and verified against MySQL 8.0.46 (2026-07-28). 53 new tests, 376 in the
suite. One runtime gap remains — the cross-company HTTP check; see *Verified by running it*.
First module of **Phase 5 — Finance**.
**Package:** `com.courier.modules.finance` (new module).
**Depends on:** `shared`; `modules/company` for branch identity, through a port it owns.
**Depended on by:** Shipment (booking debit), COD settlement, commission and payout, as they land.

## Purpose

Every branch (vendor) runs on a prepaid wallet. Bookings are paid out of it, recharges and
settlements pay into it, and the balance is the branch's licence to operate. The module's
whole job is that the balance and the ledger can never disagree.

## The central rule

> **A balance is never assigned. It is only moved, and every move writes a ledger entry.**

This is expressed in three places so that no single lapse can break it:

1. `Wallet` has **no balance setter**. `applyCredit` / `applyDebit` are the only mutators and
   they enforce positive amounts, an operational wallet and no overdraft.
2. `WalletServiceImpl.post(...)` is the only caller of those two methods, and it writes the
   `WalletTransaction` in the same transaction.
3. `WalletService` exposes no method that takes a balance. There is no update endpoint on a
   wallet at all.

## Entity relationships

```
Company
  └── Branch *                              (modules/company)
        └── Wallet 1                        exactly one, created with the branch
              └── WalletTransaction *       append-only ledger
```

- **Wallet → Branch**: `wallets.branch_id`, with a real FK (`ON DELETE RESTRICT`) —
  unlike the branch module's deferred FKs, both sides are stable and `wallets` starts empty,
  so nothing could fail on boot.
- **WalletTransaction → Wallet**: FK, RESTRICT. A ledger entry outlives everything else.
- `UNIQUE (company_id, branch_id)` on `wallets` is what makes "exactly one wallet per branch"
  a database fact rather than a convention.

## Entities

### `Wallet` (`wallets`, company-owned)

`walletNumber` (immutable, **globally** unique, `WLT` + yyMM + 8), `branchId` (immutable),
`status`, `availableBalance`, `holdBalance`, `currency`, plus `BaseEntity` audit/version.

**Two balances, not one.** `availableBalance` is spendable now; `holdBalance` is money
reserved against in-flight work (a booked-but-uncharged shipment) — neither spendable nor
lost. `totalBalance` is derived and returned by the API so three clients don't each compute
it and one forget the hold. *Nothing writes `holdBalance` yet*: the field and its semantics
exist for the shipment module, which is what will place and release holds.

### `WalletTransaction` (`wallet_transactions`, company-owned)

`transactionNo` (globally unique, `TXN` + yyyyMMdd + 10), `walletId`, `transactionType`,
`subTransactionType`, `amount`, `balanceBefore`, `balanceAfter`, `referenceType`,
`referenceId`, `remarks`, `paymentGateway`, `paymentReference`, `paymentStatus`, `createdBy`,
`createdAt`.

Append-only: every column but `paymentStatus` is `updatable = false`. A correction is a new
entry, never an edit. `balanceBefore`/`balanceAfter` are denormalised deliberately — replaying
a ledger makes every statement O(history) and lets an out-of-order correcting entry silently
rewrite the past.

## Enums

| Enum | Values |
|---|---|
| `TransactionType` | `CR`, `DR` — the accounting codes, because statements and settlement files speak them |
| `SubTransactionType` | `WRC` recharge · `SBK` booking · `SRF` refund · `COD` COD settlement · `COM` commission · `BST` branch settlement · `MCR` manual credit · `MDB` manual debit · `TRI` transfer in · `TRO` transfer out · `ADJ` adjustment · `PNL` penalty |
| `ReferenceType` | `PAYMENT`, `SHIPMENT`, `SETTLEMENT`, `SYSTEM`, `MANUAL` |
| `PaymentStatus` | `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED` — null on non-payment entries |
| `WalletStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED`, `CLOSED` — only ACTIVE moves money |

Each `SubTransactionType` carries the direction it may appear in (`CREDIT`/`DEBIT`/`BOTH`), so
a shipment booking cannot be filed as a credit. `COD`, `COM`, `BST` and `ADJ` are genuinely
both; the rest are fixed. `requireSupports` throws 422 with the code and label named.

## Wallet creation

Two paths, on purpose:

- **`BranchCreated` event → `WalletProvisioningListener`** (`AFTER_COMMIT`, `REQUIRES_NEW`).
  The rule is a Finance rule, so it lives in Finance; `BranchServiceImpl` is untouched and
  branch creation cannot fail because an unrelated module did. Failures are logged, not
  propagated.
- **`WalletService.getOrCreateForBranch`**, idempotent, on every read path. This is what
  repairs a missed event *and* what gives the branches that predate this module a wallet.

There is deliberately **no SQL backfill** in `V10`: wallet numbers are generated in Java and
SQL cannot produce them. A provisioning race is arbitrated by `uk_wallets_company_branch` — the
loser gets a 409 and the retry finds the winner's wallet. That can happen at most once in a
branch's life.

## Recharge, and why it is two calls

The spec's flow is *Branch → Razorpay → success → CREDIT*. Implemented securely that needs
**two** server calls, and the second listed endpoint is the reason:

1. `POST /branch-wallet/recharge/order` — the server opens the gateway order for an amount
   **it** fixes, and returns it for checkout. Nothing is credited. Without this, a browser
   opens checkout for ₹1 and claims ₹10,000.
2. `POST /branch-wallet/recharge` — settles it. Three guards, in order:
   - **idempotency first** (before verification, before anything): a payment already recorded
     for this company returns the existing entry unchanged;
   - **signature verification** — HMAC-SHA256 over `orderId|paymentId`, constant-time compare;
   - **`fetchPayment` from the gateway** — the wallet is credited with the amount *the gateway
     reports*, never the one in the request body, and only if the payment is `captured` and
     belongs to the presented order and the wallet's currency.

**No `PENDING` ledger row is written when an order is opened.** An entry is a record of money
that moved; a row for a checkout the user may simply close makes every statement and every
`balanceBefore/After` a guess. Intent is recorded in the audit trail
(`WALLET_RECHARGE_INITIATED`), which is where "who tried what" belongs.

**`uk_wallet_txn_payment_ref` is global, not per-company** — the only unique key in the project
that is. One merchant account serves the whole platform, so a payment id is globally unique at
the gateway; scoping it per company would let company B present company A's payment id and be
credited for it. The pre-insert check for that case is native
(`countByPaymentReferenceAcrossCompanies`) precisely because the Hibernate filter would otherwise
confine it to the caller's own company — the one query in this module that must not be
company-scoped. A hit is refused flatly ("This payment has already been recorded."), never
saying whose it was.

## Payment gateway

`PaymentGatewayPort` (three methods: `createOrder`, `verifyPayment`, `fetchPayment`) with two
implementations chosen by `app.payment.razorpay.enabled`:

- `RazorpayPaymentGateway` — REST directly, no SDK (the surface is two endpoints and one HMAC;
  an SDK would add a dependency tree for that and hide the part that must be obviously correct).
  Rupees↔paise conversion is `BigDecimal` only.
- `UnconfiguredPaymentGateway` — the **default**. Refuses every online recharge with a 422.
  Fails closed: the tempting "skip verification when there's no secret" flag credits wallets
  for payments nobody made, and always reaches production. Manual credit still works.

Both conditions are explicit `@ConditionalOnProperty` (no `@ConditionalOnMissingBean`), same
reasoning as `CompanyDirectory`. Enabling Razorpay without both keys fails at **startup**.

Config: `app.payment.razorpay.{enabled,key-id,key-secret,api-base-url,merchant-name}`, env-only
secrets (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`).

## Cross-module seam

Finance owns `BranchDirectoryPort` (`findBranch`, `branchOfUser`, `branchManagedBy`);
`modules/company` supplies `CompanyBranchDirectory`. The same arrangement auth uses for companies.
Finance therefore never imports `Branch`, `BranchRepository` or a company user row — it gets a
flat `BranchRef`. `BranchRepository` gained one additive query, `findFirstByCompanyIdAndManagerId`.

## Concurrency

**Pessimistic, not optimistic.** Every money path loads through
`WalletRepository.lockByBranchIdWithinCompany` (`SELECT … FOR UPDATE`). Two concurrent bookings
against one branch must serialise; with optimistic locking one would lose and a customer would
see a failed payment for a race they had no part in. No JPA lock-timeout hint — MySQL's dialect
does not render one, and a hint that is silently ignored reads as protection that isn't there.
The bound is the server's `innodb_lock_wait_timeout` (50 s default; worth lowering).

## Security

| Actor | Reach |
|---|---|
| Branch user (any placement) | read + recharge **their own** branch's wallet |
| `BRANCH_MANAGER` | same; falls back to the branch they manage if unplaced |
| `COMPANY_ADMIN` | read/recharge/credit/debit **any** branch of the company |
| `SUPER_ADMIN` | read within a bound company |

Per-method `@PreAuthorize` gates the tier (`COMPANY_ADMIN` only for credit/debit); the
"your branch" part is in code, from the caller's own placement. **Two answers, on purpose**, as
in the branch module: a read out of scope → **404**; a write out of scope → **403**.

The path is **singular** (`/branch-wallet`, not `/branch-wallets/{id}`) and no wallet id appears
in any URL — a wallet cannot be reached by guessing an identifier. A company admin selects with
`?branchId=`; anyone else naming another branch gets 404.

Company isolation is the usual two layers plus the one documented native escape above.

## REST APIs

| Method | Path | Who |
|---|---|---|
| `GET` | `/api/v1/branch-wallet` | any company user (own branch); admin may pass `branchId` |
| `GET` | `/api/v1/branch-wallet/summary` | same |
| `GET` | `/api/v1/branch-wallet/transactions` | same; paged/sorted/filtered/searchable |
| `POST` | `/api/v1/branch-wallet/recharge/order` | branch user (own) / `COMPANY_ADMIN` — **added beyond the spec's six**, see *Recharge* |
| `POST` | `/api/v1/branch-wallet/recharge` | same |
| `POST` | `/api/v1/branch-wallet/credit` | `COMPANY_ADMIN` |
| `POST` | `/api/v1/branch-wallet/debit` | `COMPANY_ADMIN` |

Sort whitelist: `createdDate`, `transactionNo`, `amount`, `balanceAfter`, `transactionType`,
`subTransactionType`, `paymentStatus`. Newest first by default; `size` capped at 100.
`search` covers entry number, remarks, reference id and payment id (wildcard-escaped).

**Export ready** means the transaction response is flat and fully denormalised — enum labels
included, no ids to resolve — so a page maps straight onto CSV columns. An export walks the
pages; there is no unbounded endpoint.

### Errors
400 validation / bad sort · 401 unauthenticated · 403 credit/debit without `COMPANY_ADMIN`, or
transacting on another branch · 404 unknown branch, or another branch's wallet on a read ·
409 provisioning race · 422 insufficient balance, non-positive amount, wrong direction for a
reason code, non-ACTIVE wallet, unverified/uncaptured/duplicate payment, no gateway configured,
no bound company.

## Events

`WalletEvent` (sealed, `AFTER_COMMIT`): `WalletCreated`, `WalletCredited`, `WalletDebited`,
`WalletRecharged`. Today they log; this is where low-balance alerts and recharge receipts attach.

## Database — `V10__branch_wallet.sql`

`wallets`: identity, branch, status, two balances (`DECIMAL(19,4)`), currency, `BaseEntity`
columns. `UNIQUE (company_id, branch_id)`, `UNIQUE (wallet_number)`; indexes on `branch_id` and
`(company_id, status)`; FK to `branches`; CHECKs that neither balance goes negative.

`wallet_transactions`: the fields above. `UNIQUE (transaction_no)`, `UNIQUE (payment_reference)`
(global — see *Recharge*); indexes `(wallet_id, created_at)`, `(company_id, created_at)`,
`(company_id, transaction_type, sub_transaction_type)`, `(company_id, reference_type,
reference_id)`; FK to `wallets`; CHECKs `amount > 0` and `transaction_type IN ('CR','DR')`.

Every enum column is `@JdbcTypeCode(SqlTypes.VARCHAR)` — Hibernate 6.5+ otherwise renders a
native MySQL `enum(...)` and `ddl-auto: validate` fails on boot (the `plan_type` defect,
CHANGELOG 0.3.0).

## Audit

`WALLET_CREATED`, `WALLET_CREDITED`, `WALLET_DEBITED`, `WALLET_RECHARGE_INITIATED`,
`WALLET_RECHARGED`. Credit/debit details carry the entry number, direction, reason, amount and
both balances — enough to reconcile from the audit trail alone.

## Tests

53 new unit tests (376 in the suite):
- `WalletTest` (11) — credit, debit, insufficient balance, held money not spendable, debit to
  exactly zero, non-positive amounts, every non-ACTIVE status, scale normalisation, total.
- `SubTransactionTypeTest` (5) — direction enforcement, both-direction codes, `requireSupports`,
  the creditable/debitable lists, catalogue completeness.
- `WalletNumberGeneratorTest` (4) — shapes, column fit, unambiguous alphabet, 10k no collisions.
- `WalletTransactionSpecificationsTest` (3) — **fails closed**: no wallet scope matches nothing.
- `WalletServiceImplTest` (30) — provisioning (creates/reuses/404 on unknown branch), scoping
  (own branch, manager fallback, 404 read / 403 write on another, unplaced user, no company),
  credit (entry + balances + defaults + wrong-direction reason + non-positive), debit
  (entry + insufficient + wrong direction), non-ACTIVE wallet, recharge (**gateway amount wins
  over the client's**, idempotent replay, another company's payment refused, uncaptured, order
  mismatch, currency mismatch, bad signature credits nothing, missing fields), statement pinning,
  summary figures, number-collision retry, and that every money path takes the row lock.

## Verified by running it

Against MySQL 8.0.46 on 2026-07-28, using the Phase-3/4 `LEGACY_CO` fixtures:

- **`V10` applied** (`Successfully applied 1 migration … now at version v10`) and Hibernate
  `ddl-auto: validate` passed. `SHOW CREATE TABLE` confirms every enum column is `varchar`,
  not a native MySQL `enum` — the 6.5+ trap that broke `plan_type` is avoided; both balances
  and every amount are `decimal(19,4)`; all keys, indexes, FKs and CHECKs are as designed,
  including the deliberately global `uk_wallet_txn_payment_ref (payment_reference)`.
- **Lazy provisioning works on a pre-V10 branch**: `GET /branch-wallet?branchId=…` on
  `PUNE_MAIN` (created under V9) returned a freshly created `WLT2607JYECG825`, zero balances,
  ACTIVE.
- **Event provisioning works**: creating branch `WALLET_TEST` produced its wallet from the
  `BranchCreated` listener with the company bound — log line
  `Wallet WLT2607Q7E4SYB9 created for branch … in company …`.
- **Ledger arithmetic is exact.** 2×credit 5000 (MCR) and 2×debit 1250.50 (PNL) left
  `availableBalance = 7499.0000`, with `balanceBefore`/`balanceAfter` chaining correctly
  across all four entries (0→5000→10000→8749.5→7499).
- **`sumSettledSince` is right and null-safe**: summary reported todayCredit 10000,
  todayDebit 2501, month and lifetime the same, `transactionCount` 4,
  `lastRechargeAmount` null (no recharge yet).
- **Refusals, all with the intended status**: insufficient balance **422** (message names
  available and required), `SBK` on a credit **422** (`'SBK' (Shipment Booking) cannot be
  recorded as a credit.`), zero amount **400**, bad sort key **400**, unknown branch **404**,
  no token **401**.
- **Fail-closed gateway confirmed**: `POST …/recharge/order` with no Razorpay configured →
  **422**, "no payment gateway is configured … A company administrator can credit the wallet
  manually." Startup logged the warning as designed.
- **RBAC**: a branch user (`branch@legacy.test`) read their own wallet and statement with no
  `branchId`, and got **403** on both credit and debit. Statement filter `transactionType=DR`
  returned exactly the 2 debits.
- **Audit**: `WALLET_CREATED` ×2, `WALLET_CREDITED` ×2, `WALLET_DEBITED` ×2 written.

**Left untested, and why:** the cross-company HTTP check (a rival admin getting 404 on every
wallet verb). `RIVAL_CO`'s only user is `PENDING` and cannot log in, and it owns no branch, so
there was nothing to attack from. The isolation is the project's usual two layers plus
`findByIdWithinCompany`, and the unit suite covers the scoping — but this is a **real gap in the
runtime evidence**. Provision an active `RIVAL_CO` admin and a rival branch, then run it.

## Next

- [ ] **Cross-company runtime check** — needs an active `RIVAL_CO` admin and a rival branch,
      neither of which exists in the dev database.
- [ ] **Hold balance has no writer.** `applyHold`/`releaseHold` land with Shipment.
- [x] **Booking debit seam** — closed 2026-07-30 by Shipment Booking:
      `WalletService.debitForBooking(BookingDebitCommand)`, `isAuthenticated()` not
      `COMPANY_ADMIN`-gated, debited AFTER_COMMIT via `ShipmentBookingWalletListener`.
      See `MEMORY/modules/shipment-booking.md`.
- [ ] Razorpay **webhook** endpoint (`payment.captured`), so a closed browser still settles.
      `PaymentStatus.PENDING`/`FAILED` exist for it.
- [ ] Refunds — a `SRF`/`REFUNDED` reversal path.
- [ ] Low-balance threshold + alert, on `WalletDebited`.
- [ ] **The frontend contract does not match this module** (`features/branch-wallet`, UI-11):
      it expects `/branch-wallets/{id}`, `CREDIT`/`DEBIT` rather than `CR`/`DR`, and sub-types
      `RECHARGE`/`BOOKING_CHARGE`/… rather than the three-letter codes. UI-11 was built against
      a guessed contract before this module existed. Realigning it is a frontend task.
