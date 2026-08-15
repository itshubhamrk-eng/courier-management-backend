import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { ShipmentStatusBadge } from '@features/shipment/components/shipment-status-badge';
import { ShipmentService } from '@features/shipment/shipment.service';
import { ShipmentMovementService } from './shipment-movement.service';
import { Shipment, ShipmentStatus } from '@core/models/shipment.model';
import { PinIllustration } from '@shared/components/illustrations/pin-illustration';
import { MasterDataService } from '@features/masters/master-data.service';
import { MASTER_DEFINITIONS } from '@features/masters/master.config';
import { PaymentMode } from '@core/models/master.model';

type StatusFilter = 'ALL' | 'IN_SCAN' | 'OUT_FOR_DELIVERY' | 'DELIVERED';
const LIST_STATUSES: ShipmentStatus[] = ['IN_SCAN', 'OUT_FOR_DELIVERY', 'DELIVERED'];

/** Delivery — a filterable worklist of this branch's IN_SCAN / OUT_FOR_DELIVERY / DELIVERED
 *  orders (status + text search over tracking/shipment number/receiver), then the Delivery
 *  Form (Receiver Name + Remarks required; OTP optional; Signature/Photo captured via a real
 *  file picker that uploads to S3 through ShipmentMovementService.uploadPodFile and fills the
 *  URL field with the returned link — no camera/signature-pad hardware capture, a file picker
 *  is what "capture" means here). Only an OUT_FOR_DELIVERY row opens the form — IN_SCAN/
 *  DELIVERED rows are shown for context, not actionable here. */
@Component({
  selector: 'app-delivery',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, MatIconModule, UiCard, UiLoader, UiButton, UiInput, UiSelect, ShipmentStatusBadge, PinIllustration],
  template: `
    <div class="page">
      <header class="page__head" data-tour="delivery-head">
        <div class="page__head-row">
          <app-pin-illustration class="page__head-ill" [size]="52" />
          <div><h1 class="text-h1">Delivery</h1>
          <p class="text-caption">Close a delivery against the consignee.</p></div>
        </div>
        @if (!shipment()) {
          <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
        }
      </header>

      @if (!myBranchId) {
        <app-card><p class="empty">No branch assigned — ask an admin.</p></app-card>
      } @else if (!shipment()) {
        <app-card>
          <div class="filters">
            <app-input [control]="searchControl" label="Search" placeholder="Tracking / Shipment No. / Receiver" />
            <app-select [control]="statusControl" label="Status" [options]="statusOptions" />
          </div>
        </app-card>

        @if (loading()) {
          <app-loader [minHeight]="120" caption="Loading…" />
        } @else if (!filteredShipments().length) {
          <app-card><p class="empty">Nothing matches at your branch.</p></app-card>
        } @else {
          <app-card>
            <div class="tbl__wrap">
              <table class="tbl">
                <thead>
                  <tr><th>#</th><th>Tracking No.</th><th>Receiver</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  @for (s of filteredShipments(); track s.id; let i = $index) {
                    <tr [class.tbl__row--actionable]="s.status === 'OUT_FOR_DELIVERY'" (click)="selectShipment(s)">
                      <td>{{ i + 1 }}</td>
                      <td>{{ s.trackingNumber }}</td>
                      <td>{{ s.receiverName }}</td>
                      <td><app-shipment-status-badge [status]="s.status" /></td>
                      <td class="tbl--right">
                        @if (s.status === 'OUT_FOR_DELIVERY') {
                          <app-button icon="task_alt" (pressed)="selectShipment(s)">Deliver</app-button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </app-card>
        }
      }

      @if (shipment(); as s) {
        <app-card>
          <div class="sh">
            <div><strong>{{ s.trackingNumber }}</strong>
              <span class="text-caption">{{ s.senderName }} → {{ s.receiverName }}, {{ s.receiverContact }}</span></div>
            <app-button variant="stroked" icon="close" (pressed)="reset()">Back to List</app-button>
          </div>
          @if (paymentMode(); as pm) {
            <div class="pay">
              @if (pm.collectAtBooking) {
                <span class="pay__badge pay__badge--paid"><mat-icon>check_circle</mat-icon>{{ pm.name }}</span>
              } @else {
                <span class="pay__badge">{{ pm.name }}</span>
                @if (pm.collectAtDelivery && s.netAmount != null) {
                  <span class="pay__amount">Collect ₹{{ s.netAmount | number: '1.2-2' }}</span>
                }
              }
            </div>
          }
        </app-card>

        <app-card title="Delivery Form">
          <form [formGroup]="form" (ngSubmit)="deliver()" class="df">
            <div class="grid2">
              <app-input [control]="c('receiverName')" label="Receiver Name" [required]="true" />
              <app-input [control]="c('otp')" label="OTP" placeholder="Optional" />
            </div>
            <app-input [control]="c('remarks')" label="Remarks" placeholder="Optional" />
            <div class="pod">
              <app-input [control]="c('photoUrl')" label="Photo" placeholder="Optional — upload or paste a URL" />
              <button type="button" class="pod__btn" (click)="photoFile.click()" [disabled]="uploadingPhoto()">
                <mat-icon>upload</mat-icon> {{ uploadingPhoto() ? 'Uploading…' : 'Choose file' }}
              </button>
              <input #photoFile type="file" accept="image/*,video/*,application/pdf" hidden
                     (change)="onPodFile($event)" />
            </div>
            <div class="df__bar">
              <app-button type="submit" icon="task_alt" [loading]="delivering()">Mark Delivered</app-button>
            </div>
          </form>
        </app-card>
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .filters { display:flex; gap:16px; flex-wrap:wrap; }
    .filters app-input { flex:1; min-width:220px; }
    .filters app-select { min-width:200px; }
    .df { display:flex; flex-direction:column; gap:16px; }
    .df__bar { display:flex; justify-content:flex-end; gap:10px; }
    .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:16px 20px; }
    .pod { display:flex; flex-direction:column; gap:8px; }
    .pod__btn { display:inline-flex; align-items:center; gap:6px; align-self:flex-start;
      border:1px solid var(--surface-border); background:var(--surface); border-radius:var(--r-field);
      padding:6px 12px; font:600 12px var(--font-sans); color:var(--content-fg); cursor:pointer; }
    .pod__btn:disabled { opacity:.6; cursor:default; }
    .pod__btn mat-icon { font-size:16px; width:16px; height:16px; }
    .sh { display:flex; justify-content:space-between; align-items:center; gap:12px; }
    .sh strong { display:block; font:600 15px var(--font-sans); }
    .pay { display:flex; align-items:center; gap:10px; margin-top:12px; flex-wrap:wrap; }
    .pay__badge { display:inline-flex; align-items:center; gap:4px; padding:4px 10px; border-radius:999px;
      font:600 12px var(--font-sans); background:var(--surface-muted); color:var(--content-fg); }
    .pay__badge--paid { background:var(--success-bg, #e6f7ec); color:var(--success-fg, #1a7f3c); }
    .pay__badge--paid mat-icon { font-size:15px; width:15px; height:15px; }
    .pay__amount { font:600 13px var(--font-sans); color:var(--content-fg); }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .tbl__wrap { overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl--right { text-align:right; }
    .tbl__row--actionable { cursor:pointer; }
    .tbl__row--actionable:hover { background:var(--surface-muted); }
    @media (max-width:760px){ .grid2 { grid-template-columns:1fr; } }
  `]
})
export class Delivery implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly movementService = inject(ShipmentMovementService);
  private readonly masters = inject(MasterDataService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly shipment = signal<Shipment | null>(null);
  readonly paymentMode = signal<PaymentMode | null>(null);
  readonly delivering = signal(false);
  readonly uploadingPhoto = signal(false);

  readonly shipments = signal<Shipment[]>([]);
  readonly loading = signal(true);
  readonly searchControl = new FormControl('');
  readonly statusControl = new FormControl<StatusFilter>('ALL');
  readonly statusOptions: SelectOption[] = [
    { value: 'ALL', label: 'In Scan + DRS + Delivered' },
    { value: 'IN_SCAN', label: 'In Scan' },
    { value: 'OUT_FOR_DELIVERY', label: 'DRS' },
    { value: 'DELIVERED', label: 'Delivered' }
  ];
  protected readonly filteredShipments = computed(() => {
    const q = (this.searchControl.value ?? '').trim().toLowerCase();
    if (!q) return this.shipments();
    return this.shipments().filter((s) =>
      s.trackingNumber.toLowerCase().includes(q) ||
      s.shipmentNumber.toLowerCase().includes(q) ||
      s.receiverName.toLowerCase().includes(q));
  });

  readonly form: FormGroup = this.fb.group({
    receiverName: ['', [Validators.required, Validators.maxLength(150)]],
    remarks: ['', Validators.maxLength(500)],
    otp: ['', Validators.maxLength(10)],
    photoUrl: ['', Validators.maxLength(1000)]
  });

  private pendingTrackingNumber: string | null = null;

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Delivery' }]);
    this.statusControl.valueChanges.subscribe(() => this.load());
    this.pendingTrackingNumber = this.route.snapshot.queryParamMap.get('trackingNumber');
    if (this.pendingTrackingNumber) this.searchControl.setValue(this.pendingTrackingNumber);
    this.load();
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  load(): void {
    if (!this.myBranchId) return;
    this.loading.set(true);
    const filter = this.statusControl.value ?? 'ALL';
    const statuses = filter === 'ALL' ? LIST_STATUSES : [filter as ShipmentStatus];
    forkJoin(statuses.map((status) =>
      this.shipmentService.list({ page: 0, size: 100, deliveryBranchId: this.myBranchId!, status })
    )).subscribe({
      next: (pages) => {
        const list = pages.flatMap((p) => p.content);
        this.shipments.set(list);
        this.loading.set(false);
        if (this.pendingTrackingNumber) {
          const match = list.find((s) => s.trackingNumber === this.pendingTrackingNumber);
          this.pendingTrackingNumber = null;
          if (match) this.selectShipment(match);
        }
      },
      error: () => { this.shipments.set([]); this.loading.set(false); }
    });
  }

  selectShipment(s: Shipment): void {
    if (s.status !== 'OUT_FOR_DELIVERY') {
      this.notify.error(`Shipment is ${s.status}, not OUT_FOR_DELIVERY.`);
      return;
    }
    this.shipment.set(s);
    this.paymentMode.set(null);
    this.form.patchValue({ receiverName: s.receiverName });
    this.masters.get(MASTER_DEFINITIONS['payment-modes'], s.paymentModeId)
      .subscribe((pm) => this.paymentMode.set(pm as PaymentMode));
  }

  reset(): void {
    this.shipment.set(null);
    this.paymentMode.set(null);
    this.form.reset();
    this.load();
  }

  onPodFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const shipment = this.shipment();
    if (!file || !shipment) return;

    this.uploadingPhoto.set(true);
    this.movementService.uploadPodFile(shipment.id, file, 'PHOTO').subscribe({
      next: ({ url }) => { this.uploadingPhoto.set(false); this.c('photoUrl').setValue(url); },
      error: (e: HttpErrorResponse) => {
        this.uploadingPhoto.set(false);
        this.notify.error(e.error?.message ?? 'Could not upload the file.');
      }
    });
  }

  deliver(): void {
    const shipment = this.shipment();
    if (!shipment || this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.delivering.set(true);
    this.movementService.deliver({
      shipmentId: shipment.id, receiverName: v.receiverName.trim(),
      remarks: v.remarks?.trim() || null, otp: v.otp?.trim() || null,
      signatureUrl: null, photoUrl: v.photoUrl?.trim() || null
    }).subscribe({
      next: () => {
        this.delivering.set(false);
        this.notify.success(`Shipment ${shipment.trackingNumber} delivered.`);
        this.reset();
      },
      error: (e: HttpErrorResponse) => { this.delivering.set(false); this.notify.error(e.error?.message ?? 'Could not close the delivery.'); }
    });
  }
}
