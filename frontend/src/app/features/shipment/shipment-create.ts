import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, Subject, catchError, debounceTime, merge, of, switchMap } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiAutocomplete } from '@shared/components/ui-autocomplete/ui-autocomplete';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import {
  ShipmentItemRequest, CreateShipmentRequest, PricingResponse
} from '@core/models/shipment.model';
import { ItemEntryGrid } from './components/item-entry-grid';
import { ChargeSummary } from './components/charge-summary';
import { ShipmentService } from './shipment.service';
import { printConsignmentCopies } from './consignment-print.util';

/** `yyyy-MM-dd` in the local timezone — a native `<input type="date">` value, and what
 *  `bookingDate` defaults to on the server if omitted; setting it explicitly here just
 *  makes today's date visible without waiting on a round trip. */
function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type PriceOutcome = { ok: true; data: PricingResponse } | { ok: false; message: string | null };

/**
 * Create Shipment — a single page, not a step wizard: Booking Details, Parties,
 * and the Item grid on the left, a sticky live "Booking Summary"
 * sidebar on the right that prices the booking as soon as every required field is
 * filled in (debounced) — the layout the user asked to book from directly, adapted
 * from a supplied reference mockup onto the app's own design system.
 *
 * <p>Consignor/Consignee are plain typed fields (name/address/contact number) — no
 * Customer module lookup, matching the reference screen's own Parties block. Pickup/
 * delivery pincode are typed alongside the branches, feeding the Pricing Engine
 * directly, since there is no longer an address record to resolve one from.
 */
@Component({
  selector: 'app-shipment-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, UiSelect, UiAutocomplete, UiButton, UiCard, ItemEntryGrid, ChargeSummary],
  template: `
    <div class="page">
      <header class="page__head" data-tour="booking-head">
        <div><h1 class="text-h1">New Shipment</h1><p class="text-caption">Fill in the booking — the summary prices it as you go.</p></div>
        <app-button variant="stroked" (pressed)="cancel()">Cancel</app-button>
      </header>

      <div class="lr">
        <div class="lr__main">
          <app-card title="Booking Details" [subtitle]="'Booking from ' + myBranchLabel()">
            <div class="grid3">
              <label class="fld"><span class="fld__l">Booking Date</span>
                <input class="fld__i" type="date" [formControl]="c('bookingDate')" /></label>
              <app-select [control]="c('deliveryBranchId')" label="Delivery Branch" [options]="branchOptions()" placeholder="Select the delivery branch" />
              <app-select [control]="c('serviceTypeId')" label="Service Type" [options]="serviceTypeOptions()" placeholder="Select a service type" />
            </div>
          </app-card>

          <app-card title="Items" subtitle="Add every package on this shipment; weight and dimensions drive the chargeable weight.">
            <app-item-entry-grid (itemsChange)="onItems($event)" (weightChange)="onWeight($event)" (packagesChange)="onPackages($event)">
              <app-autocomplete class="fld--sm" [control]="c('packageTypeId')" label="Package Type" [options]="packageTypeOptions()" placeholder="Search package type…" />
              <label class="fld fld--sm"><span class="fld__l">Number of Packages</span>
                <input class="fld__i" type="number" [value]="c('numberOfPackages').value" disabled /></label>
              <label class="fld fld--sm"><span class="fld__l">Declared Value</span>
                <input class="fld__i" type="number" min="0" step="0.01" [formControl]="c('declaredValue')" /></label>
            </app-item-entry-grid>
            <div class="spacer"></div>
            <label class="fld"><span class="fld__l">Remarks</span>
              <textarea class="ta" rows="2" placeholder="Handle with care, deliver before noon…" [formControl]="c('remarks')"></textarea></label>
          </app-card>

          <app-card title="Parties">
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
                <div class="spacer"></div>
                <label class="fld"><span class="fld__l">From Pincode</span>
                  <input class="fld__i" [formControl]="c('pickupPincode')" placeholder="e.g. 411001" /></label>
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
                <div class="spacer"></div>
                <label class="fld"><span class="fld__l">To Pincode</span>
                  <input class="fld__i" [formControl]="c('deliveryPincode')" placeholder="e.g. 400008" /></label>
              </div>
            </div>
          </app-card>
        </div>

        <aside class="lr__sum">
          <div class="sum">
            <h2 class="sum__title">Booking Summary</h2>

            <span class="sum__lbl">Route</span>
            <span class="sum__val">{{ myBranchLabel() }} → {{ branchLabel(c('deliveryBranchId').value) }}</span>

            <span class="sum__lbl">Load</span>
            <span class="sum__val">{{ c('numberOfPackages').value || 1 }} pkg · {{ weight().chargeable | number: '1.3-3' }} kg</span>

            <div class="sum__body">
              @if (!myBranchIdPresent()) {
                <p class="err">Your account has no branch assigned — ask an admin before booking.</p>
              } @else if (!readyToPrice()) {
                <p class="hint">Select a delivery branch and add at least one item's weight to see pricing.</p>
              } @else if (pricingLoading()) {
                <p class="hint">Pricing…</p>
              } @else if (pricingError()) {
                <p class="err">{{ pricingError() }}</p>
              } @else if (pricing(); as p) {
                <div class="matched">
                  @if (p.matchedRouteCode) { <span class="chip">Route {{ p.matchedRouteCode }}</span> }
                  @if (p.matchedRateCode) { <span class="chip">Rate {{ p.matchedRateCode }}</span> }
                </div>
                <app-charge-summary [charges]="{
                  freight: p.chargeBreakup.freight, fuelCharge: p.chargeBreakup.fuelCharge,
                  handlingCharge: p.chargeBreakup.handlingCharge, odaCharge: p.chargeBreakup.odaCharge,
                  insuranceCharge: p.chargeBreakup.insuranceCharge, gstAmount: p.chargeBreakup.gstAmount,
                  discountAmount: p.chargeBreakup.discount, roundOff: p.chargeBreakup.roundOff,
                  netAmount: manualNetAmount() ?? p.chargeBreakup.netAmount
                }" [editable]="true" (netAmountChange)="manualNetAmount.set($event)" />
              }
            </div>

            <div class="sum__paymode">
              <app-select [control]="c('paymentModeId')" label="Payment Mode" [options]="paymentModeOptions()" placeholder="Select a payment mode" />
            </div>

            <div class="sum__cta">
              <app-button icon="check" [loading]="submitting()" [disabled]="!pricing() || pricingLoading() || form.invalid" (pressed)="book()">Book Shipment</app-button>
            </div>
          </div>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .page__head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }
    .page__head { margin-bottom:4px; }
    .lr { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:16px; align-items:start; margin-top:10px; }
    .lr__main { display:flex; flex-direction:column; gap:10px; min-width:0; }
    .lr__main ::ng-deep .ac__head { padding:10px 16px; }
    .lr__main ::ng-deep .ac__body { padding:14px 16px; }
    .lr__sum { position:sticky; top:10px; }
    .sum { display:flex; flex-direction:column; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); padding:14px 16px; gap:2px; }
    .sum__title { margin:0 0 6px; font:700 15px var(--font-sans); color:var(--content-fg); }
    .sum__lbl { font:500 12px var(--font-sans); color:var(--content-muted); margin-top:8px; }
    .sum__val { font:600 14px var(--font-sans); color:var(--content-fg); }
    .sum__body { margin-top:12px; }
    .sum__paymode { margin-top:12px; }
    .sum__cta { margin-top:10px; }
    .sum__cta app-button, .sum__cta ::ng-deep .app-btn { width:100%; }
    .hint { font:400 13px var(--font-sans); color:var(--content-muted); margin:0; }
    .err { font:500 13px var(--font-sans); color:var(--danger); padding:10px 12px; background:var(--danger-bg); border-radius:var(--r-field); margin:0; }
    .matched { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:12px; }
    .chip { font:600 11px var(--font-sans); padding:4px 10px; border-radius:999px; background:var(--brand-50); color:var(--brand-700); }
    .grid2 { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px 16px; }
    .grid3 { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px 16px; }
    .parties { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
    .party { background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); padding:12px; }
    .party--sender { border-top:3px solid var(--info); }
    .party--receiver { border-top:3px solid var(--brand-600); }
    .party__title { font:700 11px var(--font-sans); letter-spacing:.06em; text-transform:uppercase; margin-bottom:8px; }
    .party--sender .party__title { color:var(--info); }
    .party--receiver .party__title { color:var(--brand-600); }
    .fld { display:flex; flex-direction:column; gap:4px; }
    .fld--sm { width:160px; }
    .fld__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .fld__i { height:38px; padding:0 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); }
    .ta { width:100%; padding:8px 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); resize:vertical; }
    .spacer { height:8px; }
    @media (max-width:960px){ .lr { grid-template-columns:1fr; } .lr__sum { position:static; } }
    @media (max-width:760px){ .grid2, .grid3, .parties { grid-template-columns:1fr; } }
  `]
})
export class ShipmentCreate implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ShipmentService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);

  /** The signed-in user's own branch — Booking Branch is not a picker here, a booking
   *  desk books from its own branch, not a chosen one. `null` for a user with no branch
   *  of their own (e.g. COMPANY_ADMIN), handled as a hard stop, not a silent fallback. */
  private readonly myBranchId = this.auth.user()?.branchId ?? null;

  /** Delivery Branch picker options — excludes the caller's own branch, see `ngOnInit`. */
  protected readonly branchOptions = signal<SelectOption[]>([]);
  /** The caller's own branch label, read from the unfiltered list before it's excluded
   *  above — `myBranchLabel()` needs it even though `branchOptions()` no longer carries it. */
  protected readonly myBranchName = signal<string | null>(null);
  protected readonly serviceTypeOptions = signal<SelectOption[]>([]);
  protected readonly packageTypeOptions = signal<SelectOption[]>([]);
  protected readonly paymentModeOptions = signal<SelectOption[]>([]);

  protected readonly items = signal<ShipmentItemRequest[]>([]);
  protected readonly weight = signal({ actual: 0, volumetric: 0, chargeable: 0 });

  protected readonly pricing = signal<PricingResponse | null>(null);
  protected readonly pricingLoading = signal(false);
  protected readonly pricingError = signal<string | null>(null);

  /** A manual override of the previewed Net Amount — display only, cleared whenever the
   *  underlying price is recomputed. Never sent to the server: the booking is always
   *  priced server-side from the actual booking fields, not from what was shown here. */
  protected readonly manualNetAmount = signal<number | null>(null);

  private readonly pricingTrigger$ = new Subject<void>();

  protected readonly form: FormGroup = this.fb.group({
    bookingBranchId: [this.myBranchId, Validators.required],
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
    bookingDate: [today()],
    numberOfPackages: [1],
    declaredValue: [null as number | null],
    remarks: ['']
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Shipments', route: '/shipments' }, { label: 'New' }]);
    // Delivery Branch excludes the caller's own booking branch — a shipment cannot be
    // booked and delivered from the same branch (no route covers that pair).
    this.masters.options('branches').subscribe((o) => {
      this.myBranchName.set(o.find((opt) => opt.value === this.myBranchId)?.label ?? null);
      this.branchOptions.set(o.filter((opt) => opt.value !== this.myBranchId));
    });
    // Pincodes default from the branches' own postal codes, so picking a delivery branch
    // is enough to price — typing an exact pincode is only needed to check a more precise
    // serviceability than "somewhere in this branch's area".
    this.masters.branchDirectory().subscribe((list) => {
      const mine = list.find((b) => b.id === this.myBranchId);
      if (mine?.postalCode && !this.form.get('pickupPincode')?.value) {
        this.form.get('pickupPincode')?.setValue(mine.postalCode);
      }
    });
    this.form.get('deliveryBranchId')?.valueChanges.subscribe((id) => {
      if (!id) return;
      this.masters.branchDirectory().subscribe((list) => {
        const branch = list.find((b) => b.id === id);
        if (branch?.postalCode) this.form.get('deliveryPincode')?.setValue(branch.postalCode);
      });
    });
    this.masters.options('service-types').subscribe((o) => {
      this.serviceTypeOptions.set(o);
      if (o.length && !this.form.get('serviceTypeId')?.value) this.form.get('serviceTypeId')?.setValue(o[0].value);
    });
    this.masters.options('package-types').subscribe((o) => {
      this.packageTypeOptions.set(o);
      if (o.length && !this.form.get('packageTypeId')?.value) this.form.get('packageTypeId')?.setValue(o[0].value);
    });
    this.masters.options('payment-modes').subscribe((o) => {
      this.paymentModeOptions.set(o);
      const paid = o.find((opt) => opt.label.endsWith('(PAID)'));
      if (paid && !this.form.get('paymentModeId')?.value) this.form.get('paymentModeId')?.setValue(paid.value);
    });

    // Only the fields that actually feed PricingCommand reschedule a price call — typing
    // in sender/receiver name, address, contact or pincode (or an item's own name, see
    // onItems) does not move the price, so it must not restart the debounce or spam
    // /pricing/calculate on every keystroke there.
    const PRICE_AFFECTING_CONTROLS = ['deliveryBranchId', 'serviceTypeId', 'packageTypeId',
      'paymentModeId', 'declaredValue', 'bookingDate'];
    merge(...PRICE_AFFECTING_CONTROLS.map((name) => this.form.get(name)!.valueChanges))
      .subscribe(() => this.schedulePricing());

    this.pricingTrigger$.pipe(
      debounceTime(500),
      switchMap(() => this.priceIt$()),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((outcome) => {
      this.pricingLoading.set(false);
      if (outcome.ok) { this.pricing.set(outcome.data); this.pricingError.set(null); }
      else { this.pricing.set(null); this.pricingError.set(outcome.message); }
    });
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }
  protected branchLabel(id: string | null): string { return this.labelOf(this.branchOptions(), id); }

  /** "—" while the branch list is still loading, then either the caller's own branch
   *  name or a hard "no branch assigned" notice — never a picker. */
  protected myBranchLabel(): string {
    if (!this.myBranchId) return 'no branch assigned to your account';
    return this.myBranchName() ?? '—';
  }

  /** Just keeps the booking payload's item list current — an item's name never affects
   *  price, and its weight/dimensions already reschedule pricing through {@link onWeight}. */
  protected onItems(items: ShipmentItemRequest[]): void {
    this.items.set(items);
  }

  protected onWeight(weight: { actual: number; volumetric: number; chargeable: number }): void {
    this.weight.set(weight);
    this.schedulePricing();
  }

  protected onPackages(count: number): void {
    this.form.get('numberOfPackages')?.setValue(count, { emitEvent: false });
  }

  protected cancel(): void { this.router.navigate(['/shipments']); }

  /**
   * A plain method, not `computed()` — see `MEMORY/modules/shipment-booking.md`'s note
   * on the identical `computed()`-reading-`FormControl.value` staleness bug this
   * project already hit once. Called from the template on every change detection run,
   * so it never goes stale.
   */
  protected readyToPrice(): boolean {
    const v = this.form.getRawValue();
    // Only the fields the Pricing Engine actually reads — sender/receiver identity plays
    // no part in a price, so the preview shouldn't wait on it (booking itself still does,
    // via the Book button's own `form.invalid` check).
    return !!(v.bookingBranchId && v.deliveryBranchId && v.serviceTypeId && v.packageTypeId
      && v.paymentModeId && this.weight().chargeable > 0);
  }

  /** See {@link readyToPrice} — same reason this is a plain method. */
  protected myBranchIdPresent(): boolean { return !!this.myBranchId; }

  private schedulePricing(): void {
    this.pricing.set(null);
    this.pricingError.set(null);
    this.manualNetAmount.set(null);
    this.pricingTrigger$.next();
  }

  private priceIt$(): Observable<PriceOutcome> {
    if (!this.readyToPrice()) return of({ ok: false, message: null } as PriceOutcome);

    this.pricingLoading.set(true);
    const v = this.form.getRawValue();

    return this.service.preview({
      bookingBranchId: v.bookingBranchId, deliveryBranchId: v.deliveryBranchId,
      pickupPincode: v.pickupPincode, deliveryPincode: v.deliveryPincode,
      serviceTypeId: v.serviceTypeId, packageTypeId: v.packageTypeId, paymentModeId: v.paymentModeId,
      actualWeight: this.weight().chargeable, declaredValue: v.declaredValue || null,
      bookingDate: v.bookingDate || null
    }).pipe(
      switchMap((data) => of({ ok: true, data }) as Observable<PriceOutcome>),
      catchError((e: HttpErrorResponse) =>
        of({ ok: false, message: e?.error?.message ?? 'Could not price this booking.' } as PriceOutcome))
    );
  }

  protected book(): void {
    const p = this.pricing();
    if (!p || this.form.invalid) return;
    const v = this.form.getRawValue();

    const body: CreateShipmentRequest = {
      bookingBranchId: v.bookingBranchId, deliveryBranchId: v.deliveryBranchId,
      pickupPincode: v.pickupPincode, deliveryPincode: v.deliveryPincode,
      senderName: v.senderName, senderAddress: v.senderAddress, senderContact: v.senderContact,
      receiverName: v.receiverName, receiverAddress: v.receiverAddress, receiverContact: v.receiverContact,
      serviceTypeId: v.serviceTypeId, packageTypeId: v.packageTypeId, paymentModeId: v.paymentModeId,
      bookingDate: v.bookingDate || null,
      declaredValue: v.declaredValue || null, numberOfPackages: v.numberOfPackages || 1,
      remarks: v.remarks || null, items: this.items()
    };

    this.submitting.set(true);
    this.service.create(body).subscribe({
      next: (s) => {
        this.submitting.set(false);
        this.notify.success(`Shipment ${s.shipmentNumber} booked — AWB ${s.trackingNumber}.`);
        printConsignmentCopies({
          companyName: this.auth.companyName() ?? 'Courier SaaS',
          shipmentNumber: s.shipmentNumber, trackingNumber: s.trackingNumber, bookingDate: s.bookingDate,
          bookingBranchLabel: this.myBranchLabel(), deliveryBranchLabel: this.branchLabel(v.deliveryBranchId),
          senderName: v.senderName, senderAddress: v.senderAddress, senderContact: v.senderContact,
          receiverName: v.receiverName, receiverAddress: v.receiverAddress, receiverContact: v.receiverContact,
          serviceTypeLabel: this.labelOf(this.serviceTypeOptions(), v.serviceTypeId),
          packageTypeLabel: this.labelOf(this.packageTypeOptions(), v.packageTypeId),
          paymentModeLabel: this.labelOf(this.paymentModeOptions(), v.paymentModeId),
          numberOfPackages: v.numberOfPackages || 1, chargeableWeight: this.weight().chargeable,
          declaredValue: v.declaredValue || null, charges: p.chargeBreakup
        });
        this.router.navigate(['/shipments', s.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // Any 4xx is the caller's to fix (bad input, a business rule, a stale reference) and
        // carries a specific message worth showing; 5xx/network failures get the generic one.
        const specific: string | null = err.status >= 400 && err.status < 500 ? err.error?.message : null;
        // ShipmentServiceImpl.requireSufficientBalance's own wording ("available X, required Y")
        // is accurate but not actionable — the fix is always one of two things, so say that
        // instead of the raw balance figures.
        if (specific?.startsWith('Insufficient wallet balance')) {
          this.notify.error('Insufficient wallet balance — recharge your wallet or choose a different payment mode.');
          return;
        }
        this.notify.error(specific ?? 'Could not book this shipment.');
      }
    });
  }

  private labelOf(options: SelectOption[], value: string | null): string {
    if (!value) return '—';
    return options.find((o) => o.value === value)?.label ?? '—';
  }
}
