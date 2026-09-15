import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { CompanyProfileService, CompanyLetterhead } from '@features/company/company-profile.service';
import { companyAddressLine, renderPrintHeader, PRINT_HEADER_CSS } from '@features/shipment/consignment-print.util';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { ShipmentService } from '@features/shipment/shipment.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { MASTER_DEFINITIONS } from '@features/masters/master.config';
import { VehicleService } from '@features/manifest/vehicle.service';
import { ShipmentMovementService } from './shipment-movement.service';
import { Shipment, MovementOutcome } from '@core/models/shipment.model';
import { RouteIllustration } from '@shared/components/illustrations/route-illustration';

/** Out For Delivery — every IN_SCAN shipment at my branch is listed and selectable up
 *  front (direct request: "without delivery partner selection all pending delivery
 *  shipment should be visible"), independent of whether a Delivery User is picked yet.
 *  Check off shipments, pick the Delivery User, optionally assign a Vehicle/Fuel
 *  Cost/Delivery Charge and verify a Delivery User OTP, then Generate DRS — the same
 *  Vehicle/Fuel Cost/OTP-verification shape Trip Hire Challan's own Dispatch action
 *  uses (`trip-hire-challan.ts`), adapted to a delivery user instead of a driver+
 *  manifest (there is still no separate DRS/batch table to hang vehicle/fuel/OTP state
 *  off before Generate DRS runs — see `ShipmentService.assignOutForDelivery`'s own doc).
 *  OTP verification is optional, same "does not gate the action" stance THC's own
 *  driver OTP takes.
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
  imports: [ReactiveFormsModule, UiCard, UiLoader, UiButton, UiInput, UiSelect, RouteIllustration],
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
        <app-card title="Shipments" subtitle="IN_SCAN shipments waiting to go on DRS at your branch.">
          @if (loading()) {
            <app-loader [minHeight]="120" caption="Loading…" />
          } @else if (!shipments().length) {
            <p class="empty">Nothing waiting — every received shipment is already on DRS.</p>
          } @else {
            <div class="tbl__wrap">
              <table class="tbl">
                <thead>
                  <tr>
                    <th><input type="checkbox" [checked]="allSelected()" (change)="toggleAll($event)" /></th>
                    <th>#</th>
                    <th>Tracking No.</th>
                    <th>Receiver</th>
                    <th>Contact</th>
                    <th>From Branch → To Branch</th>
                    <th>From City → To City</th>
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
                      <td>{{ branchNames().get(s.bookingBranchId) || '—' }} → {{ branchNames().get(s.deliveryBranchId ?? '') || '—' }}</td>
                      <td>{{ s.fromCity || '—' }} → {{ s.toCity || '—' }}</td>
                      <td class="tbl--right">{{ s.netAmount ?? 0 }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </app-card>

        @if (shipments().length) {
          <app-card title="Assign Delivery User & Vehicle">
            <form [formGroup]="form" (ngSubmit)="assign()" class="df">
              <div class="grid2">
                <app-select [control]="c('deliveryUserId')" label="Delivery User" [options]="userOptions()" placeholder="Select delivery user" />
                <app-select [control]="c('vehicleId')" label="Vehicle" [options]="vehicleOptions()" placeholder="Select vehicle (optional)" [allowEmpty]="true" emptyLabel="None" />
              </div>

              <div class="otp">
                @if (!otpVerified()) {
                  <app-button type="button" variant="stroked" icon="sms" [loading]="sendingOtp()"
                    [disabled]="!c('deliveryUserId').value || resendCooldown() > 0" (pressed)="requestOtp()">
                    {{ otpRequested() ? (resendCooldown() > 0 ? 'Resend OTP (' + resendCooldown() + 's)' : 'Resend OTP') : 'Send Delivery User OTP (optional)' }}
                  </app-button>
                  @if (otpRequested()) {
                    <span class="text-caption">Sent to {{ maskedMobile() }} — valid {{ otpExpiresInMinutes() }} min.</span>
                    <app-input [control]="otpControl" type="tel" label="Enter OTP" placeholder="4-digit code" [maxLength]="4" />
                    <app-button type="button" icon="check" [loading]="verifyingOtp()" (pressed)="verifyOtp()">Verify OTP</app-button>
                  }
                } @else {
                  <span class="otp__ok">✓ Delivery User OTP verified</span>
                }
              </div>

              <div class="grid2">
                <app-input [control]="c('fuelCost')" type="number" label="Fuel Cost" placeholder="0.00" />
                <app-input [control]="c('deliveryCharge')" type="number" label="Delivery Charge" placeholder="0.00" />
              </div>

              <div class="df__bar">
                <app-button type="submit" icon="directions_run" [loading]="assigning()" [disabled]="!selectedIds().size">Generate DRS</app-button>
              </div>
            </form>
          </app-card>
        }

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
    .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:16px 20px; }
    .otp { display:flex; flex-wrap:wrap; align-items:flex-end; gap:12px; padding:12px; background:var(--surface-muted); border-radius:var(--r-field); }
    .otp app-input { min-width:160px; }
    .otp__ok { font:600 14px var(--font-sans); color:var(--success-600, #1a7f37); }
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
    @media (max-width:760px){ .grid2 { grid-template-columns:1fr; } }
  `]
})
export class OutForDelivery implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly masterData = inject(MasterDataService);
  private readonly vehicleService = inject(VehicleService);
  private readonly movementService = inject(ShipmentMovementService);
  private readonly companyProfile = inject(CompanyProfileService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;
  /** Company letterhead for the DRS header — same `CompanyProfileService` the LR and THC
   *  prints use, so every printed document shares one masthead. */
  readonly companyLetterhead = signal<CompanyLetterhead | null>(null);

  readonly loading = signal(true);
  readonly assigning = signal(false);
  readonly shipments = signal<Shipment[]>([]);
  readonly userOptions = signal<SelectOption[]>([]);
  readonly vehicleOptions = signal<SelectOption[]>([]);
  readonly paymentModeOptions = signal<SelectOption[]>([]);
  /** Ids of payment modes flagged `collectAtDelivery` (TO_PAY / COD) — the only ones the
   *  DRS's Amount column shows a figure for; see the class doc for why. */
  readonly collectAtDeliveryModeIds = signal<Set<string>>(new Set());
  readonly branchLabel = signal('');
  /** Branch id -> "Name (CODE)" for the worklist's From Branch / To Branch column — same
   *  `branchDirectory()` lookup Loading Sheet/THC already use for the same reason. */
  readonly branchNames = signal<Map<string, string>>(new Map());
  readonly outcomes = signal<MovementOutcome[]>([]);
  readonly drsNumber = signal<string | null>(null);
  readonly printableShipments = signal<Shipment[]>([]);
  readonly successCount = computed(() => this.outcomes().filter((o) => o.success).length);
  readonly selectedIds = signal<Set<string>>(new Set());
  readonly allSelected = computed(() =>
    this.shipments().length > 0 && this.selectedIds().size === this.shipments().length);

  /** Delivery User OTP — optional, same "sending/verifying works end to end but doesn't
   *  block the action" stance THC's driver OTP takes. Reset whenever the delivery user
   *  selection changes so a code issued for one is never shown as verified for another. */
  readonly sendingOtp = signal(false);
  readonly verifyingOtp = signal(false);
  readonly otpRequested = signal(false);
  readonly otpVerified = signal(false);
  readonly maskedMobile = signal('');
  readonly otpExpiresInMinutes = signal(5);
  readonly otpControl = new FormControl('', [Validators.pattern(/^\d{4}$/)]);
  /** Seconds left before Resend OTP is clickable again — same THC convention. */
  readonly resendCooldown = signal(0);
  private resendTimer: ReturnType<typeof setInterval> | null = null;

  readonly form: FormGroup = this.fb.group({
    deliveryUserId: [null as string | null, Validators.required],
    vehicleId: [null as string | null],
    fuelCost: [null as number | null, Validators.min(0)],
    deliveryCharge: [null as number | null, Validators.min(0)]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'DRS' }]);
    this.movementService.userOptions().subscribe((u) =>
      this.userOptions.set(u.map((x) => ({ value: x.id, label: x.label }))));
    this.vehicleService.list(true).subscribe((v) =>
      this.vehicleOptions.set(v.map((x) => ({ value: x.id, label: x.vehicleNumber }))));
    this.companyProfile.get().pipe(catchError(() => of(null))).subscribe((c) => this.companyLetterhead.set(c));
    this.masterData.options('payment-modes').subscribe((o) => this.paymentModeOptions.set(o));
    this.masterData.list(MASTER_DEFINITIONS['payment-modes'], { page: 0, size: 100, status: 'ACTIVE' }).subscribe((p) =>
      this.collectAtDeliveryModeIds.set(new Set(p.content.filter((r) => r['collectAtDelivery'] === true).map((r) => r.id))));
    this.masterData.branchDirectory().subscribe((list) => {
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`])));
      if (this.myBranchId) {
        const b = list.find((x) => x.id === this.myBranchId);
        this.branchLabel.set(b ? `${b.branchName} (${b.branchCode})` : '');
      }
    });
    this.c('deliveryUserId').valueChanges.subscribe(() => this.resetOtpState());
    // Digits-only, 4 chars max — strips anything a paste or a non-numeric keyboard slips
    // in, since the field itself (a plain text input) doesn't block that on its own.
    this.otpControl.valueChanges.subscribe((v) => {
      const digits = (v ?? '').replace(/\D/g, '').slice(0, 4);
      if (digits !== v) this.otpControl.setValue(digits, { emitEvent: false });
    });
    this.load();
  }

  ngOnDestroy(): void {
    this.stopResendCooldown();
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

  private resetOtpState(): void {
    this.otpRequested.set(false);
    this.otpVerified.set(false);
    this.maskedMobile.set('');
    this.otpControl.reset();
    this.stopResendCooldown();
  }

  requestOtp(): void {
    const deliveryUserId = this.c('deliveryUserId').value;
    if (!deliveryUserId) return;
    this.sendingOtp.set(true);
    this.movementService.requestDeliveryOtp(deliveryUserId).subscribe({
      next: (r) => {
        this.sendingOtp.set(false);
        this.otpRequested.set(true);
        this.otpVerified.set(false);
        this.maskedMobile.set(r.maskedMobile);
        this.otpExpiresInMinutes.set(r.expiresInMinutes);
        this.otpControl.reset();
        this.notify.success(`OTP sent to ${r.maskedMobile}.`);
        this.startResendCooldown();
      },
      error: (e: HttpErrorResponse) => { this.sendingOtp.set(false); this.notify.error(e.error?.message ?? 'Could not send OTP.'); }
    });
  }

  private startResendCooldown(): void {
    this.stopResendCooldown();
    this.resendCooldown.set(15);
    this.resendTimer = setInterval(() => {
      const next = this.resendCooldown() - 1;
      if (next <= 0) { this.stopResendCooldown(); return; }
      this.resendCooldown.set(next);
    }, 1000);
  }

  private stopResendCooldown(): void {
    if (this.resendTimer !== null) { clearInterval(this.resendTimer); this.resendTimer = null; }
    this.resendCooldown.set(0);
  }

  verifyOtp(): void {
    const deliveryUserId = this.c('deliveryUserId').value;
    const otp = this.otpControl.value?.trim();
    if (!deliveryUserId || !otp) return;
    this.verifyingOtp.set(true);
    this.movementService.verifyDeliveryOtp(deliveryUserId, otp).subscribe({
      next: () => {
        this.verifyingOtp.set(false);
        this.otpVerified.set(true);
        this.notify.success('OTP verified.');
      },
      error: (e: HttpErrorResponse) => { this.verifyingOtp.set(false); this.notify.error(e.error?.message ?? 'Incorrect OTP.'); }
    });
  }

  assign(): void {
    if (this.form.invalid || !this.selectedIds().size) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const shipmentIds = Array.from(this.selectedIds());
    const selected = this.shipments().filter((s) => shipmentIds.includes(s.id));
    this.assigning.set(true);
    this.movementService.outForDelivery({
      shipmentIds, deliveryUserId: v.deliveryUserId,
      vehicleId: v.vehicleId, fuelCost: v.fuelCost, deliveryCharge: v.deliveryCharge
    }).subscribe({
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
    const company = this.companyLetterhead();
    const companyName = company?.companyName ?? this.auth.companyName() ?? '';
    const companyLogo = company?.logo ?? this.auth.companyLogo();
    const companyAddress = companyAddressLine(company);
    const companyGst = company?.gstNumber ?? null;
    const companyContact = company?.mobile ?? null;
    const drsNo = this.drsNumber() ?? '—';
    const collectIds = this.collectAtDeliveryModeIds();
    const collectAmount = (s: Shipment): number | null => collectIds.has(s.paymentModeId) ? (s.netAmount ?? 0) : null;
    const totalAmount = rows.reduce((sum, s) => sum + (collectAmount(s) ?? 0), 0);
    const tableRows = rows.map((s, i) => `<tr>
      <td>${i + 1}</td>
      <td>${this.esc(s.trackingNumber)}</td>
      <td>${this.esc(s.ewayBillNumber) || '—'}</td>
      <td>${this.esc(s.receiverName)}</td>
      <td>${this.esc(s.receiverContact)}</td>
      <td>${this.esc(s.fromCity) || '—'}</td>
      <td>${this.esc(s.toCity) || '—'}</td>
      <td>${this.esc(this.label(s.paymentModeId, this.paymentModeOptions()))}</td>
      <td style="text-align:right">${collectAmount(s) ?? '—'}</td>
      <td></td>
      <td></td>
    </tr>`).join('');
    win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>DRS ${this.esc(this.deliveryUserLabel)}</title>
      <style>
        body { font-family: sans-serif; padding: 24px; color: #111; }
        ${PRINT_HEADER_CSS}
        .head { margin-bottom: 16px; }
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
      ${renderPrintHeader(
        { companyName, companyLogo, companyAddress, companyGst, companyContact, companyWebsite: null },
        { label: 'DRS No', value: drsNo, barcodeValue: drsNo !== '—' ? drsNo : undefined, qrValue: drsNo !== '—' ? drsNo : undefined }
      )}
      <h1>Delivery Run Sheet (DRS)</h1>
      <div class="sub">${this.esc(this.branchLabel())}</div>
      <div class="meta">
        <div><span>DRS No.</span><span>${this.esc(drsNo)}</span></div>
        <div><span>Delivery Boy</span><span>${this.esc(this.deliveryUserLabel)}</span></div>
        <div><span>Date</span><span>${this.esc(new Date().toLocaleDateString())}</span></div>
        <div><span>Shipments</span><span>${rows.length}</span></div>
      </div>
      <table><thead><tr><th>#</th><th>Tracking No.</th><th>E-Way Bill No.</th><th>Receiver</th><th>Contact</th><th>From City</th><th>To City</th><th>Payment</th><th style="text-align:right">Amount</th><th>Receiver Sign</th><th>Stamp</th></tr></thead>
      <tbody>${tableRows}</tbody>
      <tfoot><tr><td colspan="8">Total to Collect</td><td style="text-align:right">${totalAmount}</td><td></td><td></td></tr></tfoot></table>
    </body></html>`);
    win.document.close();
    win.focus();
  }
}
