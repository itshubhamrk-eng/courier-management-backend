import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable, forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
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
 *  page just reopens the same tab for an already-dispatched manifest. The Amount column
 *  on the THC only shows a figure for a `collectAtDelivery` payment mode (TO_PAY / COD)
 *  — a PAID or TBB shipment isn't cash the vehicle is collecting on this trip — and the
 *  footer total only adds up what's actually being collected. */
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
  /** Ids of payment modes flagged `collectAtDelivery` (TO_PAY / COD) — the only ones the
   *  THC's Amount column shows a figure for; see the class doc for why. */
  readonly collectAtDeliveryModeIds = signal<Set<string>>(new Set());
  readonly vehicleOptions = signal<SelectOption[]>([]);
  readonly driverOptions = signal<SelectOption[]>([]);
  readonly openManifests = signal<Manifest[]>([]);
  readonly loadingManifests = signal(true);
  readonly branchNames = signal<Map<string, string>>(new Map());
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
    departureTime: [null as string | null]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Trip Hire Challan (THC)' }]);
    this.vehicleService.list(true).subscribe((v) =>
      this.vehicleOptions.set(v.map((x) => ({ value: x.id, label: x.vehicleNumber }))));
    this.movementService.userOptions().subscribe((u) =>
      this.driverOptions.set(u.map((x) => ({ value: x.id, label: x.label }))));
    this.masterData.branchDirectory().subscribe((list) =>
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`]))));
    this.masterData.list(MASTER_DEFINITIONS['payment-modes'], { page: 0, size: 100, status: 'ACTIVE' }).subscribe((p) =>
      this.collectAtDeliveryModeIds.set(new Set(p.content.filter((r) => r['collectAtDelivery'] === true).map((r) => r.id))));
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
          departureTime: v.departureTime ? new Date(v.departureTime).toISOString() : null
        });
      })
    ).subscribe({
      next: (r) => {
        this.dispatching.set(false);
        this.result.set(r);
        this.pendingRemovals.set([]);
        const dispatched: Manifest = {
          ...manifest, status: r.status, vehicleId: r.vehicleId, driverUserId: r.driverUserId,
          dispatchedAt: r.dispatchedAt, departureTime: r.departureTime
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

  private renderThcHtml(m: Manifest, shipments: Shipment[]): string {
    const collectIds = this.collectAtDeliveryModeIds();
    const collectAmount = (s: Shipment): number | null => collectIds.has(s.paymentModeId) ? (s.netAmount ?? 0) : null;
    const totalAmount = shipments.reduce((sum, s) => sum + (collectAmount(s) ?? 0), 0);
    const rows = shipments.map((s, i) => `<tr>
      <td>${i + 1}</td>
      <td>${this.esc(s.trackingNumber)}</td>
      <td>${this.esc(s.senderName)} → ${this.esc(s.receiverName)}</td>
      <td style="text-align:right">${s.chargeableWeight} kg</td>
      <td style="text-align:right">${collectAmount(s) ?? '—'}</td>
    </tr>`).join('');
    return `<!doctype html><html><head><meta charset="utf-8"><title>THC ${this.esc(m.manifestNumber)}</title>
      <style>
        body { font-family: sans-serif; padding: 24px; color: #111; }
        h1 { font-size: 18px; margin: 0 0 4px; }
        .sub { color: #666; font-size: 13px; margin-bottom: 20px; }
        .meta { display: flex; gap: 32px; margin-bottom: 20px; font-size: 13px; }
        .meta div { display: flex; flex-direction: column; }
        .meta span:first-child { color: #666; font-size: 11px; text-transform: uppercase; }
        table { width: 100%; border-collapse: collapse; font-size: 13px; }
        th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; }
        tfoot td { font-weight: 600; }
        .actions { display: flex; gap: 10px; margin-bottom: 20px; }
        .actions button { font: 600 13px sans-serif; padding: 9px 16px; border-radius: 6px; cursor: pointer; }
        .actions .print { background: #4f46e5; color: #fff; border: 1px solid #4f46e5; }
        .actions .pdf { background: #fff; color: #4f46e5; border: 1px solid #4f46e5; }
        @media print { .actions { display: none; } }
      </style></head><body>
      <div class="actions">
        <button class="print" onclick="window.print()">Print</button>
        <button class="pdf" onclick="window.print()">Download PDF</button>
      </div>
      <h1>Trip Hire Challan (THC)</h1>
      <div class="sub">Manifest ${this.esc(m.manifestNumber)}</div>
      <div class="meta">
        <div><span>Vehicle</span><span>${this.esc(this.label(m.vehicleId, this.vehicleOptions()))}</span></div>
        <div><span>Driver</span><span>${this.esc(this.label(m.driverUserId, this.driverOptions()))}</span></div>
        <div><span>Departure</span><span>${(m.departureTime ?? m.dispatchedAt) ? this.esc(new Date((m.departureTime ?? m.dispatchedAt)!).toLocaleString()) : '—'}</span></div>
      </div>
      <table><thead><tr><th>#</th><th>Tracking No.</th><th>Sender → Receiver</th><th style="text-align:right">Weight</th><th style="text-align:right">Amount</th></tr></thead>
      <tbody>${rows || '<tr><td colspan="5">No shipments</td></tr>'}</tbody>
      <tfoot><tr><td colspan="4">Total to Collect</td><td style="text-align:right">${totalAmount}</td></tr></tfoot></table>
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
