import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { ShipmentService } from '@features/shipment/shipment.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { MASTER_DEFINITIONS } from '@features/masters/master.config';
import { ShipmentMovementService } from './shipment-movement.service';
import { Shipment, MovementOutcome } from '@core/models/shipment.model';
import { RouteIllustration } from '@shared/components/illustrations/route-illustration';

/** Out For Delivery — pick the Delivery User first, then check off which IN_SCAN
 *  shipments (delivery branch = my branch) ride with them, then Generate DRS. The same
 *  "search the worklist, not the whole company" shape In Scan's receiving-branch
 *  default uses; the shipment table only appears once a delivery user is picked, since
 *  a DRS always belongs to exactly one delivery user.
 *  **Print DRS**: once Generate DRS succeeds, the DRS preview tab opens automatically
 *  (`window.open` + `document.write`, no PDF service, no new endpoint) with the assigned
 *  batch as a Delivery Run Sheet — every field it needs (tracking no., receiver, contact,
 *  payment mode, amount) is already on the list-row `Shipment` this page holds before
 *  assigning. That tab carries its own Print and Download PDF buttons (both
 *  `window.print()` — "Save as PDF" in the browser's print dialog *is* the PDF export).
 *  "Print DRS" on this page just reopens the same tab. The Amount column on the DRS
 *  itself only shows a figure for a `collectAtDelivery` payment mode (TO_PAY / COD) — a
 *  PAID or TBB row is money the delivery boy isn't collecting, so it prints blank and
 *  the footer total only adds up what's actually being collected on the round. */
@Component({
  selector: 'app-out-for-delivery',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiLoader, UiButton, UiSelect, RouteIllustration],
  template: `
    <div class="page">
      <header class="page__head" data-tour="out-for-delivery-head">
        <div class="page__head-row">
          <app-route-illustration class="page__head-ill" [size]="52" />
          <div><h1 class="text-h1">DRS</h1>
          <p class="text-caption">Assign received shipments to a delivery user.</p></div>
        </div>
        <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
      </header>

      @if (!myBranchId) {
        <app-card><p class="empty">No branch assigned — ask an admin.</p></app-card>
      } @else {
        <app-card title="Search Shipment" subtitle="IN_SCAN shipments waiting to go on DRS at your branch.">
          @if (loading()) {
            <app-loader [minHeight]="120" caption="Loading…" />
          } @else if (!shipments().length) {
            <p class="empty">Nothing waiting — every received shipment is already on DRS.</p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="assign()" class="df">
              <app-select [control]="c('deliveryUserId')" label="Delivery User" [options]="userOptions()" placeholder="Select delivery user" />

              @if (c('deliveryUserId').value) {
                <div class="tbl__wrap">
                  <table class="tbl">
                    <thead>
                      <tr>
                        <th><input type="checkbox" [checked]="allSelected()" (change)="toggleAll($event)" /></th>
                        <th>#</th>
                        <th>Tracking No.</th>
                        <th>Receiver</th>
                        <th>Contact</th>
                        <th class="tbl--right">Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (s of shipments(); track s.id; let i = $index) {
                        <tr>
                          <td><input type="checkbox" [checked]="isSelected(s.id)" (change)="toggleOne(s.id)" /></td>
                          <td>{{ i + 1 }}</td>
                          <td>{{ s.trackingNumber }}</td>
                          <td>{{ s.receiverName }}</td>
                          <td>{{ s.receiverContact }}</td>
                          <td class="tbl--right">{{ s.netAmount ?? 0 }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
                <div class="df__bar">
                  <app-button type="submit" icon="directions_run" [loading]="assigning()" [disabled]="!selectedIds().size">Generate DRS</app-button>
                </div>
              }
            </form>
          }
        </app-card>

        @if (outcomes().length) {
          <app-card [title]="'Result (' + successCount() + ' of ' + outcomes().length + ' assigned)'">
            @if (drsNumber()) { <p class="text-caption">DRS No. <span class="mono">{{ drsNumber() }}</span></p> }
            <div class="ol">
              @for (o of outcomes(); track o.reference) {
                <div class="ol__row" [class.ol__row--fail]="!o.success">
                  <span>{{ o.reference }}</span>
                  @if (o.message) { <span class="text-caption">— {{ o.message }}</span> }
                </div>
              }
            </div>
            @if (printableShipments().length) {
              <div class="df__bar">
                <app-button variant="stroked" icon="print" (pressed)="printDrs()">Print DRS</app-button>
              </div>
            }
          </app-card>
        }
      }
    </div>
  `,
  styles: [`
    .df { display:flex; flex-direction:column; gap:16px; }
    .df__bar { display:flex; justify-content:flex-end; gap:10px; }
    .ol { display:flex; flex-direction:column; gap:6px; }
    .ol__row { display:flex; align-items:center; gap:8px; font:400 13px var(--font-sans); color:var(--success-600, #16a34a); }
    .ol__row--fail { color:var(--danger-600, #dc2626); }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .tbl__wrap { overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl--right { text-align:right; }
    .mono { font:600 13px var(--font-mono, ui-monospace); color:var(--content-fg); }
  `]
})
export class OutForDelivery implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly masterData = inject(MasterDataService);
  private readonly movementService = inject(ShipmentMovementService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly loading = signal(true);
  readonly assigning = signal(false);
  readonly shipments = signal<Shipment[]>([]);
  readonly userOptions = signal<SelectOption[]>([]);
  readonly paymentModeOptions = signal<SelectOption[]>([]);
  /** Ids of payment modes flagged `collectAtDelivery` (TO_PAY / COD) — the only ones the
   *  DRS's Amount column shows a figure for; see the class doc for why. */
  readonly collectAtDeliveryModeIds = signal<Set<string>>(new Set());
  readonly branchLabel = signal('');
  readonly outcomes = signal<MovementOutcome[]>([]);
  readonly drsNumber = signal<string | null>(null);
  readonly printableShipments = signal<Shipment[]>([]);
  readonly successCount = computed(() => this.outcomes().filter((o) => o.success).length);
  readonly selectedIds = signal<Set<string>>(new Set());
  readonly allSelected = computed(() =>
    this.shipments().length > 0 && this.selectedIds().size === this.shipments().length);

  readonly form: FormGroup = this.fb.group({
    deliveryUserId: [null as string | null, Validators.required]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'DRS' }]);
    this.movementService.userOptions().subscribe((u) =>
      this.userOptions.set(u.map((x) => ({ value: x.id, label: x.label }))));
    this.masterData.options('payment-modes').subscribe((o) => this.paymentModeOptions.set(o));
    this.masterData.list(MASTER_DEFINITIONS['payment-modes'], { page: 0, size: 100, status: 'ACTIVE' }).subscribe((p) =>
      this.collectAtDeliveryModeIds.set(new Set(p.content.filter((r) => r['collectAtDelivery'] === true).map((r) => r.id))));
    if (this.myBranchId) {
      this.masterData.branchDirectory().subscribe((list) => {
        const b = list.find((x) => x.id === this.myBranchId);
        this.branchLabel.set(b ? `${b.branchName} (${b.branchCode})` : '');
      });
    }
    this.load();
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  load(): void {
    if (!this.myBranchId) return;
    this.loading.set(true);
    this.selectedIds.set(new Set());
    this.shipmentService.list({
      page: 0, size: 100, deliveryBranchId: this.myBranchId, status: 'IN_SCAN'
    }).subscribe({
      next: (p) => { this.shipments.set(p.content); this.loading.set(false); },
      error: () => { this.shipments.set([]); this.loading.set(false); }
    });
  }

  toggleOne(id: string): void {
    const next = new Set(this.selectedIds());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selectedIds.set(next);
  }

  toggleAll(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.selectedIds.set(checked ? new Set(this.shipments().map((s) => s.id)) : new Set());
  }

  isSelected(id: string): boolean { return this.selectedIds().has(id); }

  assign(): void {
    if (this.form.invalid || !this.selectedIds().size) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const shipmentIds = Array.from(this.selectedIds());
    const selected = this.shipments().filter((s) => shipmentIds.includes(s.id));
    this.assigning.set(true);
    this.movementService.outForDelivery({ shipmentIds, deliveryUserId: v.deliveryUserId }).subscribe({
      next: (r) => {
        this.assigning.set(false);
        this.outcomes.set(r.results);
        this.drsNumber.set(r.drsNumber);
        const assignedNumbers = new Set(r.results.filter((o) => o.success).map((o) => o.reference));
        this.printableShipments.set(selected.filter((s) => assignedNumbers.has(s.shipmentNumber)));
        this.deliveryUserLabel = this.label(v.deliveryUserId, this.userOptions());
        if (r.failureCount) this.notify.error(`${r.failureCount} of ${r.results.length} could not be assigned.`);
        else this.notify.success(`${r.successCount} shipment(s) assigned.`);
        this.selectedIds.set(new Set());
        this.load();
        if (this.printableShipments().length) this.printDrs();
      },
      error: (e: HttpErrorResponse) => { this.assigning.set(false); this.notify.error(e.error?.message ?? 'Could not assign.'); }
    });
  }

  private deliveryUserLabel = '';

  private label(id: string | null | undefined, options: SelectOption[]): string {
    return options.find((o) => o.value === id)?.label ?? id ?? '—';
  }

  private esc(s: string | null | undefined): string {
    return (s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  printDrs(): void {
    const rows = this.printableShipments();
    if (!rows.length) return;
    const win = window.open('', '_blank', 'width=800,height=900');
    if (!win) { this.notify.error('Pop-up blocked — allow pop-ups to print the DRS.'); return; }
    const collectIds = this.collectAtDeliveryModeIds();
    const collectAmount = (s: Shipment): number | null => collectIds.has(s.paymentModeId) ? (s.netAmount ?? 0) : null;
    const totalAmount = rows.reduce((sum, s) => sum + (collectAmount(s) ?? 0), 0);
    const tableRows = rows.map((s, i) => `<tr>
      <td>${i + 1}</td>
      <td>${this.esc(s.trackingNumber)}</td>
      <td>${this.esc(s.receiverName)}</td>
      <td>${this.esc(s.receiverContact)}</td>
      <td>${this.esc(this.label(s.paymentModeId, this.paymentModeOptions()))}</td>
      <td style="text-align:right">${collectAmount(s) ?? '—'}</td>
    </tr>`).join('');
    win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>DRS ${this.esc(this.deliveryUserLabel)}</title>
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
      <h1>Delivery Run Sheet (DRS)</h1>
      <div class="sub">${this.esc(this.branchLabel())}</div>
      <div class="meta">
        <div><span>DRS No.</span><span>${this.esc(this.drsNumber() ?? '—')}</span></div>
        <div><span>Delivery Boy</span><span>${this.esc(this.deliveryUserLabel)}</span></div>
        <div><span>Date</span><span>${this.esc(new Date().toLocaleDateString())}</span></div>
        <div><span>Shipments</span><span>${rows.length}</span></div>
      </div>
      <table><thead><tr><th>#</th><th>Tracking No.</th><th>Receiver</th><th>Contact</th><th>Payment</th><th style="text-align:right">Amount</th></tr></thead>
      <tbody>${tableRows}</tbody>
      <tfoot><tr><td colspan="5">Total to Collect</td><td style="text-align:right">${totalAmount}</td></tr></tfoot></table>
    </body></html>`);
    win.document.close();
    win.focus();
  }
}
