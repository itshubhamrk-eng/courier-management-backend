# District Level Freight: copy rates from another branch

**Size:** small change.

## Intent
COMPANY_ADMIN sets up a new branch's district freight by copying another branch's rates instead of entering or importing each district row.

## Goals
- Pick one source branch and one or more target branches; copy the source's rate rows to each target.
- Existing target rows are kept untouched (skip, never overwrite).

## Non-goals
- No overwrite mode, no per-district selection, no rate adjustment during copy.
- No change to calculation, import, or the existing create/update endpoints.

## Constraints
- Unique key (company, branch, district) counts soft-deleted rows, so "existing" = any row including deleted; those districts are skipped.
- Company isolation: source and targets validated via `BranchLookupPort` (company-owned, active).
- COMPANY_ADMIN only; branch managers cannot use it.

## Approaches
- A (chosen): single backend endpoint, one transaction, per-target copied/skipped counts, one audit entry per copy.
- B (rejected): frontend loops over existing create endpoint; N calls, partial failure, no single audit.

## Design
- `POST /api/v1/district-level-freight/copy` body `{sourceBranchId, targetBranchIds[]}`. Response per target: `{branchId, branchCode, copied, skipped}`.
- Copies source rows that are ACTIVE and not deleted; new rows start ACTIVE with identical six slab rates, ODA flag and charge.
- Source excluded from targets (400 if present); empty target list 400; source with no active rows returns zeros.
- Audit: new action/entry summarising source, targets, counts (builder's call on exact AuditAction naming).
- Frontend: "Copy rates from branch" button on the list page (COMPANY_ADMIN only) opening a dialog: source branch select, target branches multi-select, result summary after submit; list refreshes.

## Left to builder
Exact DTO/class names, dialog styling per existing components, tests (service-level: copy, skip existing, skip soft-deleted, source==target rejected).
