import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import {
  ShipmentResponse, ShipmentItemRequest, ShipmentType, SHIPMENT_TYPES, UpdateShipmentRequest
} from '@core/models/shipment.model';
import { ItemEntryGrid } from './components/item-entry-grid';
import { ShipmentService } from './shipment.service';

const TYPE_OPTIONS: SelectOption[] = SHIPMENT_TYPES.map((t) => ({ value: t, label: t.replace('_', ' ') }));

/**
 * Edit Shipment — full replacement, only while the shipment is still `BOOKED` (the
 * backend refuses otherwise). Booking Branch is immutable once booked, shown read-only;
 * everything else UpdateShipmentRequest carries is editable, including the plain-text
 * Consignor/Consignee fields (no Customer module lookup). Saving re-prices the booking
 * server-side — this page does not run its own preview, unlike Create's wizard.
 */
@Component({
  selector: 'app-shipment-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiSelect, UiButton, UiCard, UiLoader, ItemEntryGrid],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Edit Shipment</h1><p class="text-caption">Only while the shipment is still BOOKED.</p></div>
      </header>

      @if (loading()) {
        <app-loader [minHeight]="280" caption="Loading…" />
      } @else if (!shipment()) {
        <app-card><p class="empty">Shipment not found, outside your scope, or no longer editable.</p></app-card>
      } @else {
        <div class="ef">
          <app-card title="Booking Branch">
            <div class="grid2">
              <div class="stat"><span class="stat__l">Booking Branch</span><span class="stat__v">{{ branchLabel(shipment()!.bookingBranchId) }}</span><span class="stat__h">Immutable once booked</span></div>
              <app-select [control]="c('deliveryBranchId')" label="Delivery Branch" [options]="deliveryBranchOptions()" placeholder="Select the delivery branch" />
              <label class="fld"><span class="fld__l">From Pincode</span>
                <input class="fld__i" [formControl]="c('pickupPincode')" placeholder="e.g. 411001" /></label>
              <label class="fld"><span class="fld__l">To Pincode</span>
                <input class="fld__i" [formControl]="c('deliveryPincode')" placeholder="e.g. 400008" /></label>
            </div>
          </app-card>

          <div class="parties">
            <div class="party party--sender">
              <div class="party__title">Consignor (Sender)</div>
              <label class="fld"><span class="fld__l">Name</span>
                <input class="fld__i" [formControl]="c('senderName')" placeholder="Sender's full name" /></label>
              <div class="spacer"></div>
              <label class="fld"><span class="fld__l">Address</span>
                <textarea class="ta" rows="2" [formControl]="c('senderAddress')" placeholder="Pickup address"></textarea></label>
              <div class="spacer"></div>
              <label class="fld"><span class="fld__l">Contact Number</span>
                <input class="fld__i" type="tel" [formControl]="c('senderContact')" placeholder="10-digit mobile number" /></label>
            </div>

            <div class="party party--receiver">
              <div class="party__title">Consignee (Receiver)</div>
              <label class="fld"><span class="fld__l">Name</span>
                <input class="fld__i" [formControl]="c('receiverName')" placeholder="Receiver's full name" /></label>
              <div class="spacer"></div>
              <label class="fld"><span class="fld__l">Address</span>
                <textarea class="ta" rows="2" [formControl]="c('receiverAddress')" placeholder="Delivery address"></textarea></label>
              <div class="spacer"></div>
              <label class="fld"><span class="fld__l">Contact Number</span>
                <input class="fld__i" type="tel" [formControl]="c('receiverContact')" placeholder="10-digit mobile number" /></label>
            </div>
          </div>

          <app-card title="Package Details">
            <div class="grid3">
              <app-select [control]="c('serviceTypeId')" label="Service Type" [options]="serviceTypeOptions()" placeholder="Select a service type" />
              <app-select [control]="c('packageTypeId')" label="Package Type" [options]="packageTypeOptions()" placeholder="Select a package type" />
              <app-select [control]="c('paymentModeId')" label="Payment Mode" [options]="paymentModeOptions()" placeholder="Select a payment mode" />
              <app-select [control]="c('shipmentType')" label="Shipment Type" [options]="typeOptions" />
              <label class="fld"><span class="fld__l">Booking Date</span>
                <input class="fld__i" type="date" [formControl]="c('bookingDate')" /></label>
              <label class="fld"><span class="fld__l">Number of Packages</span>
                <input class="fld__i" type="number" min="1" [formControl]="c('numberOfPackages')" /></label>
              <label class="fld"><span class="fld__l">Declared Value</span>
                <input class="fld__i" type="number" min="0" step="0.01" [formControl]="c('declaredValue')" /></label>
            </div>
          </app-card>

          <app-card title="Items">
            <app-item-entry-grid [initial]="hydrateItems()" (itemsChange)="items.set($event)" (weightChange)="weight.set($event)" />
          </app-card>

          <app-card title="Remarks">
            <textarea class="ta" rows="2" [formControl]="c('remarks')"></textarea>
          </app-card>

          <div class="ef__bar">
            <app-button variant="stroked" (pressed)="cancel()">Cancel</app-button>
            <app-button icon="save" [loading]="saving()" [disabled]="!canSave()" (pressed)="save()">Save Changes</app-button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .ef { display:flex; flex-direction:column; gap:16px; }
    .grid2 { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px 20px; }
    .grid3 { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px 20px; }
    .parties { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
    .party { background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); padding:16px; }
    .party--sender { border-top:3px solid var(--info); }
    .party--receiver { border-top:3px solid var(--brand-600); }
    .party__title { font:700 11px var(--font-sans); letter-spacing:.06em; text-transform:uppercase; margin-bottom:12px; }
    .party--sender .party__title { color:var(--info); }
    .party--receiver .party__title { color:var(--brand-600); }
    @media (max-width:700px){ .parties { grid-template-columns:1fr; } }
    .fld { display:flex; flex-direction:column; gap:6px; }
    .fld__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .fld__i { height:42px; padding:0 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); }
    .ta { width:100%; padding:10px 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); resize:vertical; }
    .stat { display:flex; flex-direction:column; gap:6px; justify-content:center; }
    .stat__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .stat__v { font:600 14px var(--font-sans); color:var(--content-fg); }
    .stat__h { font:400 12px var(--font-sans); color:var(--content-muted); }
    .spacer { height:14px; }
    .ef__bar { position:sticky; bottom:0; display:flex; align-items:center; justify-content:flex-end;
      gap:10px; padding:14px 16px; background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:24px; }
    @media (max-width:760px){ .grid2, .grid3 { grid-template-columns:1fr; } }
  `]
})
export class ShipmentEdit implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ShipmentService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly typeOptions = TYPE_OPTIONS;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly shipment = signal<ShipmentResponse | null>(null);

  readonly branchOptions = signal<SelectOption[]>([]);
  /** Delivery Branch picker options — excludes the (immutable) Booking Branch, same rule
   *  as `ShipmentCreate`: a shipment cannot be booked and delivered from the same branch. */
  readonly deliveryBranchOptions = computed(() =>
    this.branchOptions().filter((o) => o.value !== this.shipment()?.bookingBranchId));
  readonly serviceTypeOptions = signal<SelectOption[]>([]);
  readonly packageTypeOptions = signal<SelectOption[]>([]);
  readonly paymentModeOptions = signal<SelectOption[]>([]);

  /** Hydrates ItemEntryGrid exactly once when the shipment loads — a separate signal from
   *  `items` (which the grid writes back into) so the two-way flow can't loop. */
  readonly hydrateItems = signal<ShipmentItemRequest[] | null>(null);
  readonly items = signal<ShipmentItemRequest[]>([]);
  readonly weight = signal({ actual: 0, volumetric: 0, chargeable: 0 });

  private id = '';

  protected readonly form: FormGroup = this.fb.group({
    deliveryBranchId: [null as string | null, Validators.required],
    pickupPincode: [''],
    deliveryPincode: [''],
    senderName: ['', Validators.required],
    senderAddress: ['', Validators.required],
    senderContact: ['', Validators.required],
    receiverName: ['', Validators.required],
    receiverAddress: ['', Validators.required],
    receiverContact: ['', Validators.required],
    serviceTypeId: [null as string | null, Validators.required],
    packageTypeId: [null as string | null, Validators.required],
    paymentModeId: [null as string | null, Validators.required],
    shipmentType: ['NON_DOCUMENT' as ShipmentType],
    bookingDate: [''],
    numberOfPackages: [1],
    declaredValue: [null as number | null],
    remarks: ['']
  });

  /**
   * A plain method, not `computed()` — it reads `FormControl.value`, which a `computed()`
   * cannot track. Called from the template on every change detection run, so it never
   * goes stale; see `ShipmentCreate.readyToPrice` for the same reasoning.
   */
  canSave(): boolean {
    const v = this.form.getRawValue();
    return !!(
      v.deliveryBranchId && v.serviceTypeId && v.packageTypeId && v.paymentModeId
      && v.senderName && v.senderAddress && v.senderContact
      && v.receiverName && v.receiverAddress && v.receiverContact
      && this.items().length > 0 && this.weight().chargeable > 0
    );
  }

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.masters.options('service-types').subscribe((o) => this.serviceTypeOptions.set(o));
    this.masters.options('package-types').subscribe((o) => this.packageTypeOptions.set(o));
    this.masters.options('payment-modes').subscribe((o) => this.paymentModeOptions.set(o));
    this.load();
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }
  branchLabel(id: string): string { return this.branchOptions().find((o) => o.value === id)?.label ?? id; }

  private load(): void {
    this.loading.set(true);
    this.service.get(this.id).subscribe({
      next: (s) => {
        this.shipment.set(s);
        this.breadcrumb.set([{ label: 'Shipments', route: '/shipments' }, { label: s.shipmentNumber, route: `/shipments/${this.id}` }, { label: 'Edit' }]);
        this.form.patchValue({
          deliveryBranchId: s.deliveryBranchId, pickupPincode: s.pickupPincode, deliveryPincode: s.deliveryPincode,
          senderName: s.senderName, senderAddress: s.senderAddress, senderContact: s.senderContact,
          receiverName: s.receiverName, receiverAddress: s.receiverAddress, receiverContact: s.receiverContact,
          serviceTypeId: s.serviceTypeId, packageTypeId: s.packageTypeId,
          paymentModeId: s.paymentModeId, shipmentType: s.shipmentType, bookingDate: s.bookingDate,
          numberOfPackages: s.numberOfPackages, declaredValue: s.declaredValue, remarks: s.remarks ?? ''
        });
        this.hydrateItems.set(s.items.map((i) => ({
          itemName: i.itemName, quantity: i.quantity, weight: i.weight, lengthCm: i.lengthCm,
          widthCm: i.widthCm, heightCm: i.heightCm, declaredValue: i.declaredValue,
          fragile: i.fragile, dangerousGoods: i.dangerousGoods
        })));
        this.loading.set(false);
      },
      error: () => { this.shipment.set(null); this.loading.set(false); }
    });
  }

  save(): void {
    const s = this.shipment();
    if (!s || !this.canSave()) return;
    const v = this.form.getRawValue();

    const body: UpdateShipmentRequest = {
      version: s.version, deliveryBranchId: v.deliveryBranchId,
      pickupPincode: v.pickupPincode, deliveryPincode: v.deliveryPincode,
      senderName: v.senderName, senderAddress: v.senderAddress, senderContact: v.senderContact,
      receiverName: v.receiverName, receiverAddress: v.receiverAddress, receiverContact: v.receiverContact,
      serviceTypeId: v.serviceTypeId, packageTypeId: v.packageTypeId, paymentModeId: v.paymentModeId,
      shipmentType: v.shipmentType, bookingDate: v.bookingDate || null,
      declaredValue: v.declaredValue || null, numberOfPackages: v.numberOfPackages || 1,
      remarks: v.remarks || null, items: this.items()
    };

    this.saving.set(true);
    this.service.update(this.id, body).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Shipment updated.'); this.router.navigate(['/shipments', this.id]); },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) { this.notify.error('This shipment changed since you opened it. Reloading the latest version.'); this.load(); }
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not update the shipment.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/shipments', this.id]); }
}
