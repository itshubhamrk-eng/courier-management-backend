import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable, forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import qrcode from 'qrcode-generator';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { Manifest, DispatchManifestResponse, Shipment } from '@core/models/shipment.model';
import { ManifestService } from '@features/manifest/manifest.service';
import { VehicleService } from '@features/manifest/vehicle.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { MASTER_DEFINITIONS } from '@features/masters/master.config';
import { ShipmentMovementService } from './shipment-movement.service';
import { ManifestCard } from './components/manifest-card';
import { TruckIllustration } from '@shared/components/illustrations/truck-illustration';

/** Trip Hire Challan (THC) — renamed from "Dispatch" on direct request; the dispatch
 *  action itself is unchanged. A worklist of every open ("loading sheet created")
 *  manifest, each carrying its own THC action; picking one opens its shipment checklist
 *  and Assign Vehicle & Driver below. Unchecking a shipment only drops its row
 *  client-side (`pendingRemovals` — no popup, no immediate status change, on direct
 *  request); the actual `ManifestService.removeShipment` calls (same mutation
 *  `ManifestCard`'s own remove button uses) fire from `dispatch()`, right before the
 *  dispatch POST itself, so nothing about the manifest changes until Dispatch is
 *  clicked. Requires the manifest to still carry at least one MANIFEST_CREATED
 *  ("loading sheet created") shipment at dispatch time (enforced server-side).
 *  Departure Time is operator-entered and optional — blank defaults server-side to the
 *  dispatch moment itself (see Manifest.dispatch). Once dispatched, the THC preview tab
 *  opens automatically (`window.open` + `document.write`, no PDF service) with the
 *  manifest's vehicle/driver/departure and its LR table; that tab carries its own Print
 *  and Download PDF buttons (both `window.print()` — "Save as PDF" in the browser's print
 *  dialog *is* the PDF export, there's no separate file written). "Preview THC" on this
 *  page just reopens the same tab for an already-dispatched manifest. The THC follows a
 *  branded challan layout (KTC-style: title bar, company/challan-number header, meta strip,
 *  bordered shipment table, total row, terms & conditions, signature footer). The printed
 *  challan number is not `manifestNumber` itself but `THC/<booking-branch-code>/<DDMMYY
 *  dispatch date>/<manifestNumber's own random suffix>` (see `thcNumber()`) — the branch
 *  code and date make it human-readable on paper, the suffix keeps it unique without a new
 *  backend sequence. Its TO PAY FREIGHT column only shows a figure for `topayModeIds` —
 *  `collectAtDelivery` but not `cashOnDelivery` — since COD cash and billed/paid freight
 *  aren't the trip's to-pay amount; the footer total only adds up that column. INVOICE NO
 *  is each shipment's own current E-Way Bill invoice number — `Shipment.invoiceNumber`,
 *  batch-fetched server-side by `/manifests/{id}/shipments` the same "one query, not one
 *  per row" way as `netAmount`/`deliveredAt` already are (see `ShipmentService
 *  .invoiceNumbersFor`) — blank ("—") for a shipment with no E-Way Bill, the normal case
 *  below the mandatory-value threshold. The QR code encodes the challan number as plain
 *  text (same `qrcode-generator` lib `consignment-print.util.ts` already uses for the LR's
 *  own QR, no CDN dependency). */
@Component({
  selector: 'app-trip-hire-challan',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiLoader, UiButton, UiSelect, UiInput, ManifestCard, TruckIllustration],
  template: `
    <div class="page">
      <header class="page__head" data-tour="thc-head">
        <div class="page__head-row">
          <app-truck-illustration class="page__head-ill" [size]="52" />
          <div><h1 class="text-h1">Trip Hire Challan (THC)</h1>
          <p class="text-caption">Assign a vehicle and driver, then send the manifest out.</p></div>
        </div>
        <app-button variant="stroked" icon="refresh" (pressed)="loadOpenManifests()">Refresh</app-button>
      </header>

      @if (!manifest()) {
        <app-card title="Search Manifest">
          <div class="row">
            <app-input [control]="searchControl" label="Manifest Number" placeholder="MFT-260101-1234" (keydown.enter)="search()" />
            <app-button icon="search" [loading]="searching()" (pressed)="search()">Search</app-button>
          </div>
        </app-card>

        <h2 class="text-h2 section-title">Loading Sheet Created</h2>
        @if (loadingManifests()) {
          <app-loader [minHeight]="120" caption="Loading…" />
        } @else if (!openManifests().length) {
          <app-card><p class="empty">No manifest is ready for a Trip Hire Challan.</p></app-card>
        } @else {
          @for (m of openManifests(); track m.id) {
            <app-manifest-card [manifest]="m" [branchNames]="branchNames()" [showDispatchAction]="true" (dispatch)="selectManifest($event)" />
          }
        }
      }

      @if (manifest(); as m) {
        <app-card>
          <div class="mh">
            <div><strong>{{ m.manifestNumber }}</strong>
              <span class="text-caption">Status: {{ m.status }}</span></div>
            <div class="mh__actions">
              @if (m.status !== 'CREATED') {
                <app-button variant="stroked" icon="visibility" [loading]="previewing()" (pressed)="previewThc()">Preview THC</app-button>
              }
              <app-button variant="stroked" icon="close" (pressed)="reset()">Change Manifest</app-button>
            </div>
          </div>
        </app-card>

        @if (m.status === 'CREATED') {
          <app-card title="Shipments on this Manifest" subtitle="Uncheck a shipment to leave it off this THC — it's removed from the loading sheet when you click Dispatch.">
            @if (loadingShipments()) {
              <app-loader [minHeight]="80" caption="Loading…" />
            } @else if (!manifestShipments().length) {
              <p class="empty">No shipments left on this manifest.</p>
            } @else {
              <div class="tbl__wrap">
                <table class="tbl">
                  <thead>
                    <tr><th></th><th>#</th><th>Tracking No.</th><th>Sender → Receiver</th><th class="tbl--right">Weight</th></tr>
                  </thead>
                  <tbody>
                    @for (s of manifestShipments(); track s.id; let i = $index) {
                      <tr>
                        <td><input type="checkbox" checked (change)="unselectShipment(s)" /></td>
                        <td>{{ i + 1 }}</td>
                        <td>{{ s.trackingNumber }}</td>
                        <td>{{ s.senderName }} → {{ s.receiverName }}</td>
                        <td class="tbl--right">{{ s.chargeableWeight }} kg</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </app-card>

          <app-card title="Assign Vehicle & Driver">
            <form [formGroup]="form" (ngSubmit)="dispatch()" class="df">
              <div class="grid2">
                <app-select [control]="c('vehicleId')" label="Vehicle" [options]="vehicleOptions()" placeholder="Select vehicle" />
                <app-select [control]="c('driverUserId')" label="Driver" [options]="driverOptions()" placeholder="Select driver" />
              </div>
              <label class="fld"><span class="fld__l">Departure Time</span>
                <input class="fld__i" type="datetime-local" [formControl]="c('departureTime')" />
                <span class="fld__hint">Blank means now</span></label>
              <div class="grid2">
                <app-input [control]="c('fuelCost')" type="number" label="Fuel Cost" placeholder="0.00" />
                <app-input [control]="c('driverAdvance')" type="number" label="Driver Advance" placeholder="0.00" />
                <app-input [control]="c('tollAmount')" type="number" label="Toll" placeholder="0.00" />
                <app-input [control]="c('otherAmount')" type="number" label="Other Amount" placeholder="0.00" />
              </div>
              @if (!vehicleOptions().length) { <p class="empty">No active vehicles — add one first.</p> }
              @if (!manifestShipments().length && !loadingShipments()) { <p class="empty">No shipments left on this manifest — cannot dispatch.</p> }
              <div class="df__bar">
                <app-button type="submit" icon="outbound" [loading]="dispatching()" [disabled]="!manifestShipments().length">Dispatch</app-button>
              </div>
            </form>
          </app-card>
        } @else {
          <app-card><p class="empty">This manifest has already been dispatched.</p></app-card>
        }

        @if (result(); as r) {
          <app-card title="Dispatched">
            <p>{{ r.shipmentCount }} shipment(s) moved to DISPATCHED on vehicle assignment.</p>
          </app-card>
        }
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .row { display:flex; gap:12px; align-items:flex-end; }
    .row app-input { flex:1; }
    .df { display:flex; flex-direction:column; gap:16px; }
    .df__bar { display:flex; justify-content:flex-end; gap:10px; }
    .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:16px 20px; }
    .mh { display:flex; justify-content:space-between; align-items:center; gap:12px; }
    .mh strong { display:block; font:600 15px var(--font-sans); }
    .mh__actions { display:flex; gap:10px; }
    .section-title { margin:8px 0 0; }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .tbl__wrap { overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl--right { text-align:right; }
    .fld { display:flex; flex-direction:column; gap:6px; }
    .fld__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .fld__i { height:44px; padding:0 14px; background:var(--surface-muted); border:1px solid transparent;
      border-radius:var(--r-field); box-shadow:var(--shadow-clay-inset); font:400 14px var(--font-sans); color:var(--content-fg); }
    .fld__i:focus { outline:0; border-color:var(--brand-400); box-shadow:var(--shadow-clay-inset), 0 0 0 3px var(--brand-100); }
    .fld__hint { font:400 12px var(--font-sans); color:var(--content-muted); }
    @media (max-width:760px){ .grid2 { grid-template-columns:1fr; } }
  `]
})
export class TripHireChallan implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly manifestService = inject(ManifestService);
  private readonly vehicleService = inject(VehicleService);
  private readonly masterData = inject(MasterDataService);
  private readonly movementService = inject(ShipmentMovementService);

  readonly manifest = signal<Manifest | null>(null);
  readonly result = signal<DispatchManifestResponse | null>(null);
  readonly searching = signal(false);
  readonly dispatching = signal(false);
  readonly previewing = signal(false);
  /** Ids of payment modes that are `collectAtDelivery` but NOT `cashOnDelivery` — i.e. pure
   *  "To Pay" freight, the only figure the THC's To Pay Freight column carries. COD is cash
   *  the driver collects on delivery, not freight owed on the trip itself, so it's excluded. */
  readonly topayModeIds = signal<Set<string>>(new Set());
  readonly vehicleOptions = signal<SelectOption[]>([]);
  readonly driverOptions = signal<SelectOption[]>([]);
  readonly openManifests = signal<Manifest[]>([]);
  readonly loadingManifests = signal(true);
  readonly branchNames = signal<Map<string, string>>(new Map());
  readonly branchCodes = signal<Map<string, string>>(new Map());
  /** Driver id -> {name, mobile} for the THC's own DRIVER MOBILE field — same
   *  `userDirectory()` lookup DRS Report/Detail already uses for the same reason
   *  (`userOptions()`'s Lookup has no mobile slot). */
  readonly driverDirectory = signal<Map<string, { name: string; mobile: string }>>(new Map());
  readonly manifestShipments = signal<Shipment[]>([]);
  readonly loadingShipments = signal(false);
  /** Ids unchecked in "Shipments on this Manifest" — dropped from the row list right
   *  away, but not actually detached (ManifestService.removeShipment) until dispatch()
   *  fires, so unchecking causes no server-side change on its own. */
  readonly pendingRemovals = signal<string[]>([]);

  readonly searchControl = new FormControl('');
  readonly form: FormGroup = this.fb.group({
    vehicleId: [null as string | null, Validators.required],
    driverUserId: [null as string | null, Validators.required],
    departureTime: [null as string | null],
    fuelCost: [null as number | null, Validators.min(0)],
    driverAdvance: [null as number | null, Validators.min(0)],
    tollAmount: [null as number | null, Validators.min(0)],
    otherAmount: [null as number | null, Validators.min(0)]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Trip Hire Challan (THC)' }]);
    this.vehicleService.list(true).subscribe((v) =>
      this.vehicleOptions.set(v.map((x) => ({ value: x.id, label: x.vehicleNumber }))));
    this.movementService.userOptions().subscribe((u) =>
      this.driverOptions.set(u.map((x) => ({ value: x.id, label: x.label }))));
    this.movementService.userDirectory().subscribe((m) => this.driverDirectory.set(m));
    this.masterData.branchDirectory().subscribe((list) => {
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`])));
      this.branchCodes.set(new Map(list.map((b) => [b.id, b.branchCode])));
    });
    this.masterData.list(MASTER_DEFINITIONS['payment-modes'], { page: 0, size: 100, status: 'ACTIVE' }).subscribe((p) =>
      this.topayModeIds.set(new Set(p.content
        .filter((r) => r['collectAtDelivery'] === true && r['cashOnDelivery'] !== true)
        .map((r) => r.id))));
    this.loadOpenManifests();
    const manifestNumber = this.route.snapshot.queryParamMap.get('manifestNumber');
    if (manifestNumber) {
      this.searchControl.setValue(manifestNumber);
      this.search();
    }
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  loadOpenManifests(): void {
    this.loadingManifests.set(true);
    this.manifestService.list({ page: 0, size: 50, status: 'CREATED', sort: 'createdAt,desc' }).subscribe({
      next: (p) => { this.openManifests.set(p.content); this.loadingManifests.set(false); },
      error: () => { this.openManifests.set([]); this.loadingManifests.set(false); }
    });
  }

  selectManifest(m: Manifest): void {
    this.manifest.set(m);
    this.result.set(null);
    this.loadManifestShipments(m);
  }

  search(): void {
    const query = this.searchControl.value?.trim();
    if (!query) return;
    this.searching.set(true);
    this.manifestService.list({ page: 0, size: 1, search: query }).subscribe({
      next: (p) => {
        this.searching.set(false);
        if (!p.content.length) { this.notify.error('No manifest found.'); return; }
        this.manifest.set(p.content[0]);
        this.result.set(null);
        this.loadManifestShipments(p.content[0]);
      },
      error: (e: HttpErrorResponse) => { this.searching.set(false); this.notify.error(e.error?.message ?? 'Search failed.'); }
    });
  }

  reset(): void {
    this.manifest.set(null);
    this.result.set(null);
    this.manifestShipments.set([]);
    this.pendingRemovals.set([]);
    this.form.reset();
  }

  private loadManifestShipments(m: Manifest): void {
    this.pendingRemovals.set([]);
    if (m.status !== 'CREATED') { this.manifestShipments.set([]); return; }
    this.loadingShipments.set(true);
    this.manifestService.shipments(m.id).subscribe({
      next: (s) => { this.manifestShipments.set(s); this.loadingShipments.set(false); },
      error: () => { this.manifestShipments.set([]); this.loadingShipments.set(false); }
    });
  }

  /** Drops the row locally only — no confirm, no server call. The shipment is actually
   *  detached at dispatch() time, right before the vehicle/driver are assigned. */
  protected unselectShipment(shipment: Shipment): void {
    this.manifestShipments.update((list) => list.filter((s) => s.id !== shipment.id));
    this.pendingRemovals.update((ids) => [...ids, shipment.id]);
  }

  dispatch(): void {
    const manifest = this.manifest();
    if (!manifest || this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.dispatching.set(true);
    const removals = this.pendingRemovals();
    const removed$: Observable<unknown> = removals.length
      ? forkJoin(removals.map((shipmentId) => this.manifestService.removeShipment(manifest.id, shipmentId)))
      : of(null);
    removed$.pipe(
      switchMap(() => {
        const v = this.form.getRawValue();
        return this.movementService.dispatch({
          manifestId: manifest.id, vehicleId: v.vehicleId, driverUserId: v.driverUserId,
          departureTime: v.departureTime ? new Date(v.departureTime).toISOString() : null,
          fuelCost: v.fuelCost, driverAdvance: v.driverAdvance,
          tollAmount: v.tollAmount, otherAmount: v.otherAmount
        });
      })
    ).subscribe({
      next: (r) => {
        this.dispatching.set(false);
        this.result.set(r);
        this.pendingRemovals.set([]);
        const dispatched: Manifest = {
          ...manifest, status: r.status, vehicleId: r.vehicleId, driverUserId: r.driverUserId,
          dispatchedAt: r.dispatchedAt, departureTime: r.departureTime,
          fuelCost: r.fuelCost, driverAdvance: r.driverAdvance,
          tollAmount: r.tollAmount, otherAmount: r.otherAmount
        };
        this.manifest.set(dispatched);
        this.notify.success(`Manifest ${r.manifestNumber} dispatched.`);
        this.loadOpenManifests();
        this.openThcTab(dispatched, this.manifestShipments());
      },
      error: (e: HttpErrorResponse) => { this.dispatching.set(false); this.notify.error(e.error?.message ?? 'Could not dispatch the manifest.'); }
    });
  }

  previewThc(): void {
    const m = this.manifest();
    if (!m || m.status === 'CREATED') return;
    this.previewing.set(true);
    this.manifestService.shipments(m.id).subscribe({
      next: (shipments) => { this.previewing.set(false); this.openThcTab(m, shipments); },
      error: () => { this.previewing.set(false); this.openThcTab(m, []); }
    });
  }

  private label(id: string | null | undefined, options: SelectOption[]): string {
    return options.find((o) => o.value === id)?.label ?? id ?? '—';
  }

  private esc(s: string | null | undefined): string {
    return (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  /** `THC/<booking-branch-code>/<DDMMYY dispatch date>/<manifestNumber's own random
   *  suffix>` — human-readable on paper, still unique without a new DB sequence. Falls back
   *  to "now" when there's no dispatch/departure time yet (a not-yet-dispatched preview). */
  private thcNumber(m: Manifest): string {
    const code = this.branchCodes().get(m.bookingBranchId) ?? 'NA';
    const dispatched = m.departureTime ?? m.dispatchedAt;
    const d = dispatched ? new Date(dispatched) : new Date();
    const dd = String(d.getDate()).padStart(2, '0');
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const yy = String(d.getFullYear()).slice(-2);
    const seq = m.manifestNumber.split('-').pop() ?? m.manifestNumber;
    return `THC/${code}/${dd}${mm}${yy}/${seq}`;
  }

  /** Same inline `qrcode-generator` pattern as `consignment-print.util.ts`'s own `qrSvg` —
   *  no CDN, no separate PDF/image service. */
  private qrSvg(value: string): string {
    const qr = qrcode(0, 'M');
    qr.addData(value);
    qr.make();
    return qr.createSvgTag({ cellSize: 3, margin: 0 });
  }

  /** Mirrors the branded KTC-style Trip Hire Challan layout. TO PAY FREIGHT only carries a
   *  figure for `topayModeIds` (collectAtDelivery, not cashOnDelivery) — see that signal's
   *  doc. INVOICE NO is the shipment's E-Way Bill invoice number, blank where none exists. */
  private renderThcHtml(m: Manifest, shipments: Shipment[]): string {
    const topayIds = this.topayModeIds();
    const topayFreight = (s: Shipment): number | null => topayIds.has(s.paymentModeId) ? (s.netAmount ?? 0) : null;
    const totalWeight = shipments.reduce((sum, s) => sum + (s.chargeableWeight ?? 0), 0);
    const totalFreight = shipments.reduce((sum, s) => sum + (topayFreight(s) ?? 0), 0);
    const bookingDate = (s: Shipment) => s.bookingDate ? this.esc(new Date(s.bookingDate).toLocaleDateString('en-GB')) : '—';
    const rows = shipments.map((s, i) => `<tr>
      <td class="center">${i + 1}</td>
      <td>${this.esc(s.trackingNumber)}</td>
      <td>${this.esc(s.invoiceNumber) || '—'}</td>
      <td>${this.esc(s.senderName)}</td>
      <td>${this.esc(s.receiverName)}</td>
      <td class="center">${bookingDate(s)}</td>
      <td class="right">${s.chargeableWeight}</td>
      <td class="right">${topayFreight(s) ?? ''}</td>
      <td></td>
      <td></td>
    </tr>`).join('');
    const dispatched = m.departureTime ?? m.dispatchedAt;
    const dispatchedDate = dispatched ? new Date(dispatched) : null;
    const companyName = this.esc(this.auth.companyName() ?? 'Trip Hire Challan');
    const companyLogo = this.auth.companyLogo();
    const driver = this.driverDirectory().get(m.driverUserId ?? '');
    const thcNumber = this.thcNumber(m);
    const fuelCost = m.fuelCost ?? 0;
    const driverAdvance = m.driverAdvance ?? 0;
    const tollAmount = m.tollAmount ?? 0;
    const otherAmount = m.otherAmount ?? 0;
    const totalExpenses = fuelCost + driverAdvance + tollAmount + otherAmount;

    return `<!doctype html><html><head><meta charset="utf-8"><title>THC ${this.esc(m.manifestNumber)}</title>
      <style>
        * { box-sizing: border-box; }
        body { margin: 0; padding: 20px; background: #f3f3f3; font-family: Arial, Helvetica, sans-serif; color: #111; font-size: 9px; }
        .toolbar { width: 900px; margin: 0 auto 10px; }
        button { padding: 5px 12px; margin-right: 5px; border: 1px solid #777; background: #eee; cursor: pointer; font-size: 12px; }
        .challan { width: 900px; margin: auto; background: #fff; border: 1px solid #777; }
        .title { text-align: center; font-size: 15px; font-weight: bold; padding: 4px 0; border-bottom: 1px solid #777; }
        .header { display: grid; grid-template-columns: 1fr 230px 90px; border-bottom: 1px solid #777; }
        .company-info { text-align: center; padding: 10px; line-height: 15px; }
        .company-info .big { font-size: 13px; font-weight: bold; }
        .company-info .mark { max-width: 100%; max-height: 40px; object-fit: contain; }
        .challan-box { border-left: 1px solid #777; padding: 10px; text-align: center; }
        .challan-number { font-size: 11px; font-weight: bold; word-break: break-all; }
        .qr-box { border-left: 1px solid #777; padding: 8px; text-align: center; }
        .qr-box svg { width: 70px; height: 70px; }
        .meta { display: grid; grid-template-columns: repeat(4, 1fr); border-bottom: 1px solid #777; }
        .meta div { padding: 4px 6px; border-right: 1px solid #777; }
        .meta div:last-child { border-right: 0; }
        .label { font-weight: bold; display: block; }
        table { width: 100%; border-collapse: collapse; table-layout: fixed; }
        th, td { border-right: 1px solid #777; border-bottom: 1px solid #777; padding: 3px 4px; vertical-align: middle; height: 19px; word-wrap: break-word; }
        th { font-weight: bold; text-align: center; background: #fafafa; font-size: 8px; }
        td { font-size: 8px; }
        td.center, th.center { text-align: center; }
        td.right, th.right { text-align: right; }
        .c-sr { width: 30px; }
        .c-date { width: 75px; }
        .c-weight, .c-freight { width: 75px; }
        .c-sign { width: 60px; }
        .total-row td { font-weight: bold; height: 22px; }
        .footer { display: grid; grid-template-columns: 1fr 150px; min-height: 38px; }
        .footer-left { padding: 5px; border-right: 1px solid #777; }
        .footer-right { text-align: center; padding: 5px; font-weight: bold; }
        .signature { height: 22px; margin-top: 2px; }
        .terms { border-bottom: 1px solid #777; padding: 6px 8px; font-size: 8px; line-height: 13px; }
        .terms .label { display: block; margin-bottom: 2px; }
        .terms ol { margin: 0; padding-left: 14px; }
        .expenses { display: grid; grid-template-columns: repeat(5, 1fr); border-bottom: 1px solid #777; }
        .expenses div { padding: 4px 6px; border-right: 1px solid #777; }
        .expenses div:last-child { border-right: 0; }
        .expenses .total { font-weight: bold; }
        @media print {
          body { background: #fff; padding: 0; margin: 0; }
          .toolbar { display: none; }
          .challan { width: 100%; border: 1px solid #000; }
          @page { size: A4 portrait; margin: 8mm; }
        }
        @media screen and (max-width: 950px) { .challan, .toolbar { width: 100%; overflow-x: auto; } }
      </style></head><body>

      <div class="toolbar">
        <button onclick="window.print()">Print</button>
        <button onclick="window.print()">Download PDF</button>
      </div>

      <div class="challan">
        <div class="title">TRIP HIRE CHALLAN</div>

        <div class="header">
          <div class="company-info">${companyLogo ? `<img class="mark" src="${this.esc(companyLogo)}" alt="${companyName}">` : `<span class="big">${companyName}</span>`}</div>
          <div class="challan-box">
            <div class="challan-number">${this.esc(thcNumber)}</div>
          </div>
          <div class="qr-box">${this.qrSvg(thcNumber)}</div>
        </div>

        <div class="meta">
          <div><span class="label">DATE</span>${dispatchedDate ? this.esc(dispatchedDate.toLocaleDateString('en-GB')) : '—'}</div>
          <div><span class="label">TIME</span>${dispatchedDate ? this.esc(dispatchedDate.toLocaleTimeString()) : '—'}</div>
          <div><span class="label">FROM</span>${this.esc(this.branchNames().get(m.bookingBranchId) ?? '—')}</div>
          <div><span class="label">TO</span>${this.esc(this.branchNames().get(m.deliveryBranchId) ?? '—')}</div>
          <div><span class="label">VEHICLE NO</span>${this.esc(this.label(m.vehicleId, this.vehicleOptions()))}</div>
          <div><span class="label">DRIVER NAME</span>${this.esc(this.label(m.driverUserId, this.driverOptions()))}</div>
          <div><span class="label">DRIVER MOBILE</span>${this.esc(driver?.mobile) || '—'}</div>
          <div><span class="label">QTY (PKGS)</span>${m.totalPackages}</div>
        </div>

        <table>
          <thead><tr>
            <th class="c-sr">SR<br>NO.</th>
            <th>TRACKING NO</th>
            <th>INVOICE NO</th>
            <th>CONSIGNOR NAME</th>
            <th>CONSIGNEE NAME</th>
            <th class="c-date">BOOKING<br>DATE</th>
            <th class="c-weight">WEIGHT</th>
            <th class="c-freight">TO PAY<br>FREIGHT</th>
            <th class="c-sign">RECEIVER<br>SIGN</th>
            <th class="c-sign">STAMP</th>
          </tr></thead>
          <tbody>${rows || '<tr><td colspan="10" class="center">No shipments</td></tr>'}</tbody>
          <tfoot><tr class="total-row">
            <td colspan="6" class="right">Total</td>
            <td class="right">${totalWeight}</td>
            <td class="right">${totalFreight}</td>
            <td></td>
            <td></td>
          </tr></tfoot>
        </table>

        ${totalExpenses > 0 ? `<div class="expenses">
          <div><span class="label">FUEL COST</span>${fuelCost || '—'}</div>
          <div><span class="label">DRIVER ADVANCE</span>${driverAdvance || '—'}</div>
          <div><span class="label">TOLL</span>${tollAmount || '—'}</div>
          <div><span class="label">OTHER</span>${otherAmount || '—'}</div>
          <div class="total"><span class="label">TOTAL EXPENSES</span>${totalExpenses}</div>
        </div>` : ''}

        <div class="terms">
          <span class="label">TERMS &amp; CONDITIONS</span>
          <ol>
            <li>Goods are carried entirely at the owner's risk.</li>
            <li>Driver must carry a valid driving license, RC and this Trip Hire Challan for the entire trip.</li>
            <li>Vehicle and load must match the QTY and shipment list stated above at every checkpoint.</li>
            <li>Any shortage, damage or tampering of seal must be reported to the branch immediately.</li>
            <li>${companyName} is not liable for delay or loss caused by circumstances beyond its control.</li>
            <li>Freight/other charges become payable only on safe and timely delivery at the destination branch.</li>
            <li>Driver is responsible for the vehicle, load and all documents until handover is acknowledged at destination.</li>
            <li>Any discrepancy in quantity or condition must be reported at the time of delivery.</li>
            <li>All disputes are subject to the jurisdiction of the courts at the booking branch's location.</li>
          </ol>
        </div>

        <div class="footer">
          <div class="footer-left"><strong>${companyName}</strong></div>
          <div class="footer-right">SIGNATURE<div class="signature"></div></div>
        </div>
      </div>
    </body></html>`;
  }

  private openThcTab(m: Manifest, shipments: Shipment[]): void {
    const win = window.open('', '_blank');
    if (!win) { this.notify.error('Pop-up blocked — allow pop-ups to preview the THC.'); return; }
    win.document.write(this.renderThcHtml(m, shipments));
    win.document.close();
    win.focus();
  }
}
