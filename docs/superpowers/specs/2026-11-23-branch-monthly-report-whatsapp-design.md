# Branch monthly report: Vendor Audit Report extended, PDF over WhatsApp

Status: awaiting approval. Nothing built yet.

## Intent

Each branch manager should get a PDF statement of their branch's month on their own
WhatsApp, automatically on the 1st of every month, and an admin should be able to send
it on demand from a button on the Vendor Audit Report. Today managers open six separate
screens to see this, and no report exports a PDF.

## Goals

- Extend the existing **Vendor Audit Report** (`/reports/vendor-audit`) with what is
  missing for a branch statement: wallet recharges/payments, opening and closing wallet
  balance, and DRS charge, so one report answers "what happened at this branch this month".
- Per-branch PDF: page 1 totals per category, then the full transaction list as an appendix.
- A **Send on WhatsApp** button per branch row, a **Send to all** button, and a
  **Download PDF** button, on the report page.
- Automatic send on the 1st of each month, for the previous calendar month (IST), to
  each branch manager's own phone.
- Sends show up on the Communication dashboard, the same way the daily summary now does.

## Non-goals

- No change to the Commission, Branch Performance or other existing reports.
- No in-app archive of past PDFs (see Approaches, B). Re-generate on demand.
- No per-branch scheduling options, no custom report day.
- Not a replacement for the Partner Statement (PDP module).

## Anti-goals

- The PDF and the screen must never disagree. A statement whose commission total differs
  from what the branch wallet was actually credited would be worse than no statement.
- A manager must only ever receive their own branch's figures.

## Constraints (found in the code)

- Backend has no PDF library. Add one (OpenPDF).
- `MetaWhatsAppProvider` only sends template text messages. It has no media upload and
  no document header. Needs a new template (PDF header), approved by Meta, like
  `daily_business_summary` was (days, not minutes).
- The Vendor Audit Report recomputes delivery commission at the branch's **current** DRS
  rate (documented in `VendorAuditRow`), so older deliveries can differ from what was
  credited. Wallet ledger (`WalletTransaction`, sub type `COM`/`DRS`) holds the real credited amounts.
- The report is gated to `COMPANY_ADMIN`/`FINANCE_USER`/`ACCOUNTS`; the button and
  endpoints use the same gate.
- Manager phone = `users.phone` of the `BRANCH_MANAGER` user(s) of that branch.
- Meta test number can only message verified recipients; real use needs the
  production number (see whatsapp-meta-setup notes).
- New migration number: check `db/migration` immediately before writing it (V152 is last as of 2026-11-23).

## Approaches

- **A. Host the PDF in S3 and send a link.** Keeps a copy; needs a new S3 prefix in the
  IAM policy (otherwise 503), public/temporary links Meta can reach. Not chosen.
- **B. Upload the PDF to Meta's media endpoint, send by media id.** No S3/IAM change, no
  public links; PDF is not stored, it is rebuilt from the ledger on demand. **Chosen
  (my recommendation; the user did not object and asked for an on-demand button, which
  suits rebuild-on-demand).**
- **C. Text summary only, PDF in app.** Dropped: does not meet "sent via WhatsApp".

Placement of the button: the user chose to extend the existing Vendor Audit Report
rather than add a new page (mockup A/B was shown; they asked for it "in that report").

## Design (the parts decided)

**Report additions** (backend `vendorAudit` + frontend table/CSV/stat tiles):
opening balance, wallet recharges (count, amount), closing balance, DRS charge, taken from
the wallet ledger for the same date range. Existing columns unchanged.

**Statement data**: one service builds a `BranchMonthlyStatement` (summary totals per
category + transaction list) from the shipment/ledger data. The report table, the PDF and
the CSV all read the same service so they cannot diverge. Commission in the PDF uses the
credited ledger amounts.

**PDF**: OpenPDF, built in code. Page 1 header (branch, month, generated on), summary
table per category, closing balance. Appendix: every wallet transaction of the month
(date, type, reference, debit, credit, balance).

**Sending**: new `WhatsAppProvider` method to upload a document and send a template with
a document header. New template event `BRANCH_MONTHLY_REPORT` (WhatsApp only, seeded like
`DAILY_BUSINESS_SUMMARY`, submitted to Meta from the Templates page, body variables:
branch name, month).

**Ledger of sends**: new table (`branch_report_log`: company, branch, period, recipient,
status, provider message id, error, attempts, sent/delivered/read) with a unique key on
(company, branch, period, recipient). It is the duplicate guard and retry record, like
`daily_report_log`. Receipt webhook updates it; the dashboard counts it as WhatsApp
messages. Retries up to 3 attempts.

**Schedule**: `BranchMonthlyReportJob`, same tick pattern as `DailyReportJob`, on day 1
from 09:00 IST, previous calendar month in IST. Skips while the template is not approved
at Meta (not counted as an attempt).

**Manual button**: `POST /shipments/vendor-audit/{branchId}/send?from&to` and a
`send-all` variant; `GET .../pdf` for download. Same gate as the report. A manual send for
a month already sent asks to confirm (resend), and records a new attempt on the same log row.

**Branch with no manager phone**: skipped, row shows "no phone", counted on the
dashboard as a warning, never an error.

## My calls (change any of these)

- Send to every active `BRANCH_MANAGER` of the branch that has a phone, not only one.
- Report month is the previous calendar month in IST; manual button uses the report's
  own From/To so it can send any range, and the PDF title shows the range.
- Branches with zero activity in the month still get a PDF (zeros), so managers know the
  system ran. Easy to flip to skip.
- Send day/time fixed at the 1st, first tick after 09:00 IST, not configurable.
- OpenPDF over a headless-browser renderer: smaller, no new runtime dependency on the EC2 box
  (prod has ~1.9 GB RAM, already OOM-killed once).

## Left to the builder

Exact PDF layout and fonts, table column order, the Meta template wording and sample
values, DTO and endpoint naming, test fixtures. Verify the PDF totals against raw SQL
for one real month before shipping, as was done for the daily summary.

## Rollout notes

New migration (ledger table + template seed). Template must be submitted and approved at
Meta before the 1st-of-month job can send. Prod deploy by the user (prod SSH deploy is
blocked for me); `V` migration runs on backend boot.
