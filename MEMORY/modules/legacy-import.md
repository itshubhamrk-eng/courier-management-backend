# Module: Legacy import (`com.courier.modules.legacyimport`)

**Status:** built + verified 2026-11-18 (V145 tables/columns/permissions, V146 COMPANY_ADMIN grant). Branch `feat/legacy-consignment-import`, not merged or deployed.

## What it does
Old software export (`Consignments_*.xls`: Date, Consignment No, Consignor, Consignee, From, To, Package, Actual Weight, Total — an HTML table with an .xls name) -> preview -> map origins -> commit.
- Lookup: consignment no == `shipments.tracking_number` (company scoped). Found = EXISTING (never modified, differences reported). Not found = NEW. Bad/duplicate row = INVALID.
- NEW row = DELIVERED shipment written by `LegacyShipmentWriter` (REQUIRES_NEW per row): tracking = shipment number = consignment no; sender = Consignor, receiver = Consignee; `fromCity` = From text, `toCity` = To; address/contact/pincode placeholders; freight = net = Total; PAID payment mode + STANDARD (else first active) service type; one status-history row; `legacy_import=1`, `legacy_batch_id`.
- Never calls `ShipmentServiceImpl.create()`; module has no event publisher / wallet / notification dependency (structural test enforces it).

## Gotchas
- `ck_shipments_actual_weight`/`chargeable_weight` require > 0: file weight 0 is stored as 0.001 kg (preview treats them as equal).
- Origins come from the text in `From` (trim + upper-case); `SANGALI ` and `SANGLI` are different origins.
- Legacy rows excluded ONLY from POD pending views (`ShipmentCriteria.excludeLegacy`, dashboard POD pie id queries). Volume/revenue dashboards and the 9 AM summary include them.
- No per-row report stored: the UI builds the CSV from the commit response. Past batches keep counts only (`/legacy-import/batches`).
- A no-op commit (everything EXISTING) writes no batch row.
- DB-backed tests (`LegacyImportSchemaTest`, `LegacyShipmentWriterIT`, `LegacyExclusionIT`) are skipped unless `SECURITY_IT_DB_URL` points at a scratch MySQL schema.
- Verified on a cloned DB `courier_verify` (mysqldump of courier_db at V144, now V146 + 2,869 imported rows), NOT on `courier_db` — applying V145 there would stop a `main`-branch backend booting (Flyway validate).
