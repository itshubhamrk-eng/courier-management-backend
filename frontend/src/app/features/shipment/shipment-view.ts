import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '@core/auth/auth.service';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { AppRole } from '@core/models/role.model';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentResponse, ShipmentCharge, TimelineStep, CANCELLABLE_STATUSES } from '@core/models/shipment.model';
import { TrackingCard } from './components/tracking-card';
import { ChargeSummary } from './components/charge-summary';
import { ShipmentCommunicationCard } from '@features/communication/components/shipment-communication-card';
import { ShipmentService } from './shipment.service';
import { EwayBillService } from './eway-bill.service';
import { printConsignmentCopies } from './consignment-print.util';

const WRITERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.BOOKING_OPERATOR];
const TIMELINE_ICONS: Record<string, string> = {
  BOOKED: 'add_box', MANIFEST_CREATED: 'qr_code_scanner',
  DISPATCHED: 'outbound', IN_SCAN: 'move_to_inbox', OUT_FOR_DELIVERY: 'directions_run',
  DELIVERED: 'task_alt'
};

/** Shipment Details — the booking in full, with the sender/receiver resolved, and links
 *  to the three sub-resource pages (Charges, History, Documents) the backend exposes as
 *  their own endpoints. */
@Component({
  selector: 'app-shipment-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, DatePipe, RouterLink, MatIconModule, UiCard, UiLoader, UiButton, TrackingCard,
    ChargeSummary, ShipmentCommunicationCard],
  template: `
    @if (loading()) {
      <app-loader [minHeight]="320" caption="Loading…" />
    } @else if (!shipment()) {
      <app-card><p class="empty">Shipment not found or outside your scope.</p></app-card>
    } @else {
      <div class="sv">
        <app-tracking-card [shipmentNumber]="shipment()!.shipmentNumber" [trackingNumber]="shipment()!.trackingNumber"
                           [status]="shipment()!.status" [bookingDate]="shipment()!.bookingDate"
                           [expectedDeliveryDate]="shipment()!.expectedDeliveryDate ?? null" />

        <div class="sv__actions">
          <a class="sv__link" [routerLink]="['/shipments', id, 'history']"><mat-icon>history</mat-icon> History</a>
          <a class="sv__link" [routerLink]="['/shipments', id, 'documents']"><mat-icon>description</mat-icon> Documents</a>
          <a class="sv__link" [routerLink]="['/support/tickets/new']"
             [queryParams]="{ shipmentId: id, branchId: shipment()!.bookingBranchId }">
            <mat-icon>support_agent</mat-icon> Raise Ticket</a>
          <a class="sv__link" [routerLink]="['/follow-ups/new']"
             [queryParams]="{ shipmentId: id, branchId: shipment()!.bookingBranchId }">
            <mat-icon>event_repeat</mat-icon> Create Follow-up</a>
          <span class="sv__spacer"></span>
          @if (charge(); as c) {
            <app-button variant="stroked" icon="print" (pressed)="print(c)">Print LR</app-button>
          }
          @if (can().update && shipment()!.status === 'BOOKED') {
            <app-button variant="stroked" icon="edit" (pressed)="edit()">Edit</app-button>
          }
          @if (can().cancel && cancellable()) {
            <app-button variant="danger" icon="cancel" (pressed)="cancel()">Cancel</app-button>
          }
        </div>

        <div class="sv__grid-parties">
          <app-card title="Sender">
            <p class="sv__name">{{ shipment()!.senderName }}</p>
            <p class="sv__sub">{{ shipment()!.senderContact }}</p>
            <p class="sv__sub">{{ shipment()!.senderAddress }}</p>
          </app-card>

          <app-card title="Receiver">
            <p class="sv__name">{{ shipment()!.receiverName }}</p>
            <p class="sv__sub">{{ shipment()!.receiverContact }}</p>
            <p class="sv__sub">{{ shipment()!.receiverAddress }}</p>
          </app-card>
        </div>

        <div class="sv__grid2">
          <app-card title="Timeline">
            @if (loadingDetails()) {
              <app-loader [minHeight]="140" caption="Loading…" />
            } @else {
              <div class="tl">
                @for (step of steps(); track step.status) {
                  <div class="tl__row" [class.tl__row--done]="step.completed">
                    <div class="tl__icon"><mat-icon>{{ timelineIcon(step.status) }}</mat-icon></div>
                    <div class="tl__body">
                      <strong>{{ step.label }}</strong>
                      @if (step.completed) {
                        <span class="text-caption">{{ step.changedAt | date: 'medium' }}</span>
                      } @else {
                        <span class="text-caption tl__pending">Pending</span>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </app-card>

          <app-card title="Charges">
            @if (loadingDetails()) {
              <app-loader [minHeight]="140" caption="Loading…" />
            } @else if (charge(); as c) {
              <app-charge-summary [charges]="c" />
            } @else {
              <p class="empty">No charge record.</p>
            }
          </app-card>
        </div>

        @if (!loadingDetails() && charge(); as c) {
          <app-card title="Booking Branch Commission" subtitle="Computed from the booking branch's own charge percentages.">
            <div class="commission">
              <div class="commission__row"><span>Commission on Basic Freight</span><strong>{{ c.commissionOnBasicFreight | number:'1.2-2' }}</strong></div>
              <div class="commission__row"><span>Branch Commission on Other Amount</span><strong>{{ c.branchCommissionOnOtherAmount | number:'1.2-2' }}</strong></div>
              <div class="commission__row"><span>Company Commission on Basic Freight</span><strong>{{ c.companyCommissionOnBasicFreight | number:'1.2-2' }}</strong></div>
              <div class="commission__row commission__row--total"><span>Total Commission</span><strong>{{ c.totalCommission | number:'1.2-2' }}</strong></div>
            </div>
          </app-card>
        }

        <div class="sv__grid">
          <app-card title="Service">
            <dl class="kv">
              <dt>Booking Branch</dt><dd>{{ branchLabel(shipment()!.bookingBranchId) }}</dd>
              <dt>Delivery Branch</dt><dd>{{ branchLabel(shipment()!.deliveryBranchId) }}</dd>
              <dt>Pincode</dt><dd>{{ shipment()!.pickupPincode }} → {{ shipment()!.deliveryPincode }}</dd>
              <dt>Service Type</dt><dd>{{ serviceTypeLabel(shipment()!.serviceTypeId) }}</dd>
              <dt>Package Type</dt><dd>{{ packageTypeLabel(shipment()!.packageTypeId) }}</dd>
              <dt>Payment Mode</dt><dd>{{ paymentModeLabel(shipment()!.paymentModeId) }}</dd>
              <dt>Shipment Type</dt><dd>{{ shipment()!.shipmentType }}</dd>
            </dl>
          </app-card>

          <app-card title="Weight &amp; Value">
            <dl class="kv">
              <dt>Actual Weight</dt><dd class="mono">{{ shipment()!.actualWeight | number: '1.3-3' }} kg</dd>
              <dt>Volumetric Weight</dt><dd class="mono">{{ shipment()!.volumetricWeight | number: '1.3-3' }} kg</dd>
              <dt>Chargeable Weight</dt><dd class="mono">{{ shipment()!.chargeableWeight | number: '1.3-3' }} kg</dd>
              <dt>Number of Packages</dt><dd>{{ shipment()!.numberOfPackages }}</dd>
              @if (shipment()!.declaredValue) { <dt>Declared Value</dt><dd class="mono">{{ shipment()!.declaredValue | number: '1.2-2' }}</dd> }
            </dl>
          </app-card>

          <app-card title="Items" [subtitle]="shipment()!.items.length + ' package(s)'">
            <div class="sv__items">
              @for (i of shipment()!.items; track i.id) {
                <div class="sv__item">
                  <span>{{ i.itemName }} × {{ i.quantity }}@if (i.fragile) { <span class="tag">Fragile</span> }@if (i.dangerousGoods) { <span class="tag tag--d">DG</span> }</span>
                  <span class="mono">{{ i.weight }} kg</span>
                </div>
              }
            </div>
          </app-card>

          @if (shipment()!.remarks) {
            <app-card title="Remarks"><p class="sv__remarks">{{ shipment()!.remarks }}</p></app-card>
          }

          @if (shipment()!.ewayBillRequired || shipment()!.invoiceValue || shipment()!.ewayBill) {
            <app-card title="E-Way Bill">
              <dl class="kv">
                <dt>Required</dt><dd>{{ shipment()!.ewayBillRequired ? 'Yes' : 'No' }}</dd>
                @if (shipment()!.invoiceValue != null) {
                  <dt>Invoice Value</dt><dd class="mono">{{ shipment()!.invoiceValue | number: '1.2-2' }}</dd>
                }
                @if (shipment()!.ewayBill; as eb) {
                  <dt>E-Way Bill Number</dt><dd>{{ eb.ewayBillNumber || '—' }}</dd>
                  <dt>Status</dt><dd>{{ eb.status }}</dd>
                  @if (eb.validFrom || eb.validUntil) {
                    <dt>Validity</dt>
                    <dd>{{ eb.validFrom ? (eb.validFrom | date: 'mediumDate') : '—' }} – {{ eb.validUntil ? (eb.validUntil | date: 'mediumDate') : '—' }}</dd>
                  }
                  @if (eb.documentUrl) {
                    <dt>Document</dt><dd><a [href]="eb.documentUrl" target="_blank" rel="noopener">View document</a></dd>
                  }
                } @else if (shipment()!.ewayBillRequired) {
                  <dt>E-Way Bill</dt><dd class="eway-missing">Missing — required before AWB generation</dd>
                }
              </dl>
              @if (can().update && shipment()!.ewayBill; as eb) {
                <div class="eway-actions">
                  @if (eb.status !== 'VALIDATED' && eb.status !== 'CANCELLED') {
                    <app-button variant="stroked" [loading]="ewayBillBusy()" (pressed)="validateEwayBill(eb.id)">Validate</app-button>
                  }
                  @if (eb.status !== 'CANCELLED') {
                    <app-button variant="stroked" (pressed)="ewayBillFile.click()">Upload Document</app-button>
                    <input #ewayBillFile type="file" accept=".pdf,.jpg,.jpeg,.png" hidden (change)="onEwayBillFile($event, eb.id)" />
                    <app-button variant="danger" [loading]="ewayBillBusy()" (pressed)="cancelEwayBill(eb.id)">Cancel</app-button>
                  }
                </div>
              }
            </app-card>
          }

          @if (shipment()!.shipmentImageUrl) {
            <app-card title="Shipment Photo">
              <img class="sv__pod" [src]="shipment()!.shipmentImageUrl" alt="Shipment photo" />
            </app-card>
          }

          @if (shipment()!.podPhotoUrl) {
            <app-card title="Proof of Delivery" [subtitle]="shipment()!.deliveredAt ? ('Delivered ' + (shipment()!.deliveredAt | date: 'medium')) : ''">
              <img class="sv__pod" [src]="shipment()!.podPhotoUrl" alt="Proof of delivery photo" />
            </app-card>
          }

          <app-shipment-communication-card [shipmentId]="shipment()!.id" />
        </div>
      </div>
    }
  `,
  styles: [`
    .sv { display:flex; flex-direction:column; gap:16px; }
    .sv__actions { display:flex; align-items:center; gap:16px; flex-wrap:wrap; }
    .sv__link { display:inline-flex; align-items:center; gap:6px; font:600 13px var(--font-sans); color:var(--content-fg);
      text-decoration:none; padding:8px 4px; }
    .sv__link:hover { color:var(--brand-600); }
    .sv__link mat-icon { font-size:18px; width:18px; height:18px; }
    .sv__spacer { flex:1; }
    .sv__grid-parties { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
    .sv__grid2 { display:grid; grid-template-columns:1.3fr 1fr; gap:16px; align-items:start; }
    .tl { display:flex; flex-direction:column; }
    .tl__row { display:flex; gap:14px; align-items:flex-start; padding:10px 4px; position:relative; }
    .tl__row:not(:last-child)::before { content:''; position:absolute; left:19px; top:40px; bottom:-2px; width:2px; background:var(--surface-border); }
    .tl__icon { width:38px; height:38px; border-radius:50%; background:var(--surface-muted); color:var(--content-muted); display:grid; place-items:center; flex:0 0 auto; z-index:1; }
    .tl__row--done .tl__icon { background:var(--brand-50); color:var(--brand-600); }
    .tl__body { display:flex; flex-direction:column; gap:2px; padding-top:6px; }
    .tl__body strong { font:600 14px var(--font-sans); color:var(--content-fg); }
    .tl__row:not(.tl__row--done) .tl__body strong { color:var(--content-muted); }
    .tl__pending { font-style:italic; }
    .sv__grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
    .sv__grid app-card:nth-child(3) { grid-column:1 / -1; }
    .sv__name { margin:0; font:600 15px var(--font-sans); color:var(--content-fg); }
    .sv__sub { margin:4px 0 0; font:400 13px var(--font-sans); color:var(--content-muted); }
    .kv { display:grid; grid-template-columns:160px 1fr; gap:10px 16px; margin:0; }
    .kv dt { font:500 13px var(--font-sans); color:var(--content-muted); }
    .kv dd { font:600 14px var(--font-sans); color:var(--content-fg); margin:0; }
    .mono { font-family:var(--font-mono, ui-monospace); }
    .sv__items { display:flex; flex-direction:column; gap:6px; }
    .sv__item { display:flex; justify-content:space-between; font:400 13px var(--font-sans); color:var(--content-fg);
      padding:6px 0; border-bottom:1px solid var(--surface-border); }
    .tag { font:600 10px var(--font-sans); padding:2px 6px; border-radius:6px; background:var(--warning-bg); color:var(--warning); margin-left:6px; }
    .tag--d { background:var(--danger-bg); color:var(--danger); }
    .sv__remarks { margin:0; font:400 14px var(--font-sans); color:var(--content-fg); }
    .sv__pod { display:block; max-width:100%; max-height:360px; border-radius:var(--r-field); border:1px solid var(--surface-border); }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:24px; }
    .commission { display:flex; flex-direction:column; gap:10px; }
    .commission__row { display:flex; align-items:center; justify-content:space-between; font:400 14px var(--font-sans); color:var(--content-fg); }
    .commission__row strong { font-weight:600; }
    .commission__row--total { border-top:1px solid var(--surface-border); padding-top:10px; margin-top:2px; }
    .commission__row--total strong { color:var(--brand-600); }
    .eway-missing { color:var(--danger); }
    .eway-actions { display:flex; gap:10px; margin-top:14px; flex-wrap:wrap; }
    @media (max-width:860px){ .sv__grid-parties { grid-template-columns:1fr; } .sv__grid { grid-template-columns:1fr; } .sv__grid app-card:nth-child(3) { grid-column:auto; } .sv__grid2 { grid-template-columns:1fr; } }
  `]
})
export class ShipmentView implements OnInit {
  private readonly service = inject(ShipmentService);
  private readonly ewayBillService = inject(EwayBillService);
  private readonly auth = inject(AuthService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly confirmDialog = inject(DialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly shipment = signal<ShipmentResponse | null>(null);
  readonly loadingDetails = signal(true);
  readonly steps = signal<TimelineStep[]>([]);
  readonly charge = signal<ShipmentCharge | null>(null);
  readonly ewayBillBusy = signal(false);

  private readonly branchOptions = signal<SelectOption[]>([]);
  private readonly serviceTypeOptions = signal<SelectOption[]>([]);
  private readonly packageTypeOptions = signal<SelectOption[]>([]);
  private readonly paymentModeOptions = signal<SelectOption[]>([]);

  readonly can = computed(() => ({
    update: this.perms.canAccess({ roles: WRITERS, permissions: ['SHIPMENT_UPDATE'] }),
    cancel: this.perms.canAccess({ roles: WRITERS, permissions: ['SHIPMENT_CANCEL'] })
  }));
  readonly cancellable = computed(() => !!this.shipment() && CANCELLABLE_STATUSES.includes(this.shipment()!.status));

  id = '';

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.masters.options('service-types').subscribe((o) => this.serviceTypeOptions.set(o));
    this.masters.options('package-types').subscribe((o) => this.packageTypeOptions.set(o));
    this.masters.options('payment-modes').subscribe((o) => this.paymentModeOptions.set(o));
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.get(this.id).subscribe({
      next: (s) => {
        this.shipment.set(s);
        this.breadcrumb.set([{ label: 'Shipments', route: '/shipments' }, { label: s.shipmentNumber }]);
        this.loading.set(false);
        this.loadDetails();
      },
      error: () => { this.shipment.set(null); this.loading.set(false); }
    });
  }

  private loadDetails(): void {
    this.loadingDetails.set(true);
    forkJoin({
      steps: this.service.timeline(this.id),
      charge: this.service.charges(this.id).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ steps, charge }) => {
        this.steps.set(steps);
        this.charge.set(charge);
        this.loadingDetails.set(false);
      },
      error: () => this.loadingDetails.set(false)
    });
  }

  timelineIcon(status: string): string { return TIMELINE_ICONS[status] ?? 'circle'; }

  branchLabel(id: string): string { return this.branchOptions().find((o) => o.value === id)?.label ?? id; }
  serviceTypeLabel(id: string): string { return this.serviceTypeOptions().find((o) => o.value === id)?.label ?? id; }
  packageTypeLabel(id: string): string { return this.packageTypeOptions().find((o) => o.value === id)?.label ?? id; }
  paymentModeLabel(id: string): string { return this.paymentModeOptions().find((o) => o.value === id)?.label ?? id; }

  edit(): void { this.router.navigate(['/shipments', this.id, 'edit']); }

  print(c: ShipmentCharge): void {
    const s = this.shipment()!;
    printConsignmentCopies({
      companyName: this.auth.companyName() ?? 'Courier SaaS',
      companyLogo: this.auth.companyLogo(),
      shipmentNumber: s.shipmentNumber, trackingNumber: s.trackingNumber, bookingDate: s.bookingDate,
      expectedDeliveryDate: s.expectedDeliveryDate ?? null,
      bookingBranchLabel: this.branchLabel(s.bookingBranchId), deliveryBranchLabel: this.branchLabel(s.deliveryBranchId),
      senderName: s.senderName, senderAddress: s.senderAddress, senderContact: s.senderContact,
      receiverName: s.receiverName, receiverAddress: s.receiverAddress, receiverContact: s.receiverContact,
      serviceTypeLabel: this.serviceTypeLabel(s.serviceTypeId), packageTypeLabel: this.packageTypeLabel(s.packageTypeId),
      paymentModeLabel: this.paymentModeLabel(s.paymentModeId),
      numberOfPackages: s.numberOfPackages, chargeableWeight: s.chargeableWeight,
      declaredValue: s.declaredValue ?? null,
      charges: {
        freight: c.freight, fuelCharge: c.fuelCharge, handlingCharge: c.handlingCharge, odaCharge: c.odaCharge,
        insuranceCharge: c.insuranceCharge, gstAmount: c.gstAmount, discount: c.discountAmount,
        roundOff: c.roundOff, netAmount: c.netAmount
      },
      otherCharges: c.otherCharges,
      remarks: s.remarks ?? null,
      createdByName: s.createdByName ?? null
    });
  }

  // ------------------------------------------------------------------- E-Way Bill

  validateEwayBill(id: string): void {
    this.ewayBillBusy.set(true);
    this.ewayBillService.validate(id).subscribe({
      next: () => { this.ewayBillBusy.set(false); this.notify.success('E-Way Bill validated.'); this.load(); },
      error: (e) => { this.ewayBillBusy.set(false); this.notify.error(e?.error?.message ?? 'Could not validate the E-Way Bill.'); }
    });
  }

  onEwayBillFile(event: Event, ewayBillId: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.ewayBillBusy.set(true);
    this.ewayBillService.upload(ewayBillId, file).subscribe({
      next: () => { this.ewayBillBusy.set(false); this.notify.success('E-Way Bill document uploaded.'); this.load(); },
      error: (e) => { this.ewayBillBusy.set(false); this.notify.error(e?.error?.message ?? 'Could not upload the document.'); }
    });
  }

  cancelEwayBill(id: string): void {
    this.confirmDialog.prompt({
      title: 'Cancel E-Way Bill',
      message: 'This E-Way Bill will be cancelled and cannot be edited, validated or documented again.',
      label: 'Reason (optional)', placeholder: 'Amended, reissued…',
      confirmLabel: 'Cancel E-Way Bill', danger: true
    }).subscribe((reason) => {
      if (reason === undefined) return;
      this.ewayBillBusy.set(true);
      this.ewayBillService.cancel(id, reason).subscribe({
        next: () => { this.ewayBillBusy.set(false); this.notify.success('E-Way Bill cancelled.'); this.load(); },
        error: (e) => { this.ewayBillBusy.set(false); this.notify.error(e?.error?.message ?? 'Could not cancel the E-Way Bill.'); }
      });
    });
  }

  cancel(): void {
    this.confirmDialog.prompt({
      title: 'Cancel shipment',
      message: `"${this.shipment()!.shipmentNumber}" will be cancelled. This cannot be undone once it has left the branch.`,
      label: 'Reason (optional)', placeholder: 'Booked in error, customer request…',
      confirmLabel: 'Cancel Shipment', danger: true
    }).subscribe((reason) => {
      if (reason === undefined) return;
      this.service.cancel(this.id, reason).subscribe({
        next: () => { this.notify.success('Shipment cancelled.'); this.load(); },
        error: (e) => this.notify.error(e?.error?.message ?? 'Could not cancel the shipment.')
      });
    });
  }
}
