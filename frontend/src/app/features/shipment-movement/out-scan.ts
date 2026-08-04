import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentService } from '@features/shipment/shipment.service';
import { Manifest, Shipment } from '@core/models/shipment.model';
import { ManifestService } from '@features/manifest/manifest.service';
import { ManifestCard } from './components/manifest-card';

/**
 * Out Scan — creating a manifest already is "out scan created" (V20, on direct
 * request: "manifest created as outscan created" — one milestone, not two, so there is
 * no separate scan action here any more). The brief's own page asked for "Search
 * Manifest / Display Shipments / Scan Tracking Number / Bulk Scan / Show Scan Count",
 * written when it assumed a manifest module already existed and scanning was its own
 * step; since neither was true (see MEMORY/modules/shipment-movement.md), this page
 * carries the "Create Manifest" form that makes one, then lists every open (not yet
 * dispatched) manifest as its own card — heading is the manifest number, its lane,
 * total weight and total parcel count, with the LR table underneath. See ManifestCard.
 */
@Component({
  selector: 'app-out-scan',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiLoader, UiButton, UiSelect, ManifestCard],
  template: `
    <div class="page">
      <header class="page__head" data-tour="out-scan-head">
        <div><h1 class="text-h1">Out Scan</h1>
          <p class="text-caption">Manifests ready to dispatch — creating one already counts as out scan created.</p></div>
        <app-button variant="stroked" icon="refresh" (pressed)="loadOpenManifests()">Refresh</app-button>
      </header>

      <app-card title="Create Manifest" subtitle="Group booked shipments travelling this branch pair.">
        <form [formGroup]="createForm" (ngSubmit)="createManifest()" class="df">
          <app-select [control]="c('deliveryBranchId')" label="Delivery Branch" [options]="branchOptions()" placeholder="Select branch" />
          @if (!loadingBranches() && !branchOptions().length) {
            <p class="empty">No branch has a BOOKED shipment from your branch right now.</p>
          }
          @if (c('deliveryBranchId').value) {
            @if (bookedShipments().length) {
              <div>
                <span class="text-caption">Booked shipments on this lane — select the ones to manifest</span>
                <div class="tbl__wrap">
                  <table class="tbl">
                    <thead>
                      <tr><th></th><th>Tracking No.</th><th>Sender → Receiver</th><th class="tbl--right">Weight</th></tr>
                    </thead>
                    <tbody>
                      @for (s of bookedShipments(); track s.id) {
                        <tr (click)="toggleShipment(s.id)">
                          <td><input type="checkbox" [checked]="isSelected(s.id)" (click)="$event.stopPropagation()" (change)="toggleShipment(s.id)" /></td>
                          <td>{{ s.trackingNumber }}</td>
                          <td>{{ s.senderName }} → {{ s.receiverName }}</td>
                          <td class="tbl--right">{{ s.chargeableWeight }} kg</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </div>
            } @else {
              <p class="empty">No BOOKED shipments on this lane yet.</p>
            }
          }
          <div class="df__bar">
            <app-button type="submit" icon="add_box" [loading]="creating()" [disabled]="!myBranchId">Create Manifest</app-button>
          </div>
        </form>
      </app-card>

      <div class="ml-head">
        <h2 class="text-h2 section-title">Open Manifests</h2>
        <div class="ml-filters">
          <app-select [control]="filterBranchControl" [options]="allBranchOptions()" placeholder="All lanes" />
          <app-select [control]="sortControl" [options]="sortOptions" />
        </div>
      </div>

      @if (loadingManifests()) {
        <app-loader [minHeight]="120" caption="Loading…" />
      } @else if (!openManifests().length) {
        <app-card><p class="empty">No open manifests — create one above.</p></app-card>
      } @else {
        @for (m of openManifests(); track m.id) {
          <app-manifest-card [manifest]="m" [branchNames]="branchNames()"
            [showDispatchAction]="true" (dispatch)="goToDispatch($event)" />
        }
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .ml-head { display:flex; justify-content:space-between; align-items:flex-end; flex-wrap:wrap; gap:12px; }
    .ml-filters { display:flex; gap:10px; min-width:220px; }
    .ml-filters app-select { min-width:170px; }
    .df { display:flex; flex-direction:column; gap:16px; }
    .df__bar { display:flex; justify-content:flex-end; gap:10px; }
    .section-title { margin:8px 0 0; }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .tbl__wrap { margin-top:8px; overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); max-height:320px; overflow-y:auto; }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; position:sticky; top:0; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl tbody tr { cursor:pointer; }
    .tbl tbody tr:hover { background:var(--surface-muted); }
    .tbl--right { text-align:right; }
  `]
})
export class OutScan implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly masterData = inject(MasterDataService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly manifestService = inject(ManifestService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly creating = signal(false);
  readonly bookedShipments = signal<Shipment[]>([]);
  readonly branchOptions = signal<SelectOption[]>([]);
  readonly loadingBranches = signal(true);
  readonly branchNames = signal<Map<string, string>>(new Map());
  readonly openManifests = signal<Manifest[]>([]);
  readonly loadingManifests = signal(true);

  protected readonly allBranchOptions = computed<SelectOption[]>(() =>
    [...this.branchNames().entries()]
      .map(([id, label]) => ({ value: id, label }))
      .sort((a, b) => a.label.localeCompare(b.label)));

  readonly sortOptions: SelectOption[] = [
    { value: 'createdAt,desc', label: 'Newest First' },
    { value: 'createdAt,asc', label: 'Oldest First' },
    { value: 'manifestNumber,asc', label: 'Manifest No. (A–Z)' },
    { value: 'manifestNumber,desc', label: 'Manifest No. (Z–A)' }
  ];
  readonly filterBranchControl = new FormControl<string | null>(null);
  readonly sortControl = new FormControl<string>('createdAt,desc');

  readonly createForm: FormGroup = this.fb.group({
    deliveryBranchId: [null as string | null, Validators.required],
    shipmentIds: [[] as string[], Validators.required]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Out Scan' }]);
    this.masterData.branchDirectory().subscribe((list) => {
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`])));
      this.loadEligibleDeliveryBranches();
    });
    this.createForm.get('deliveryBranchId')!.valueChanges.subscribe((id) => this.loadBooked(id));
    this.filterBranchControl.valueChanges.subscribe(() => this.loadOpenManifests());
    this.sortControl.valueChanges.subscribe(() => this.loadOpenManifests());
    this.loadOpenManifests();
  }

  goToDispatch(m: Manifest): void {
    this.router.navigate(['/movement/dispatch'], { queryParams: { manifestNumber: m.manifestNumber } });
  }

  protected c(name: string): FormControl { return this.createForm.get(name) as FormControl; }

  protected isSelected(shipmentId: string): boolean {
    return (this.c('shipmentIds').value as string[]).includes(shipmentId);
  }

  protected toggleShipment(shipmentId: string): void {
    const control = this.c('shipmentIds');
    const current = control.value as string[];
    control.setValue(
      current.includes(shipmentId) ? current.filter((id) => id !== shipmentId) : [...current, shipmentId]);
  }

  /**
   * Delivery Branch only ever lists branches that actually have a BOOKED shipment
   * waiting on this lane right now — never the signed-in user's own (booking) branch,
   * and never a branch whose bookings have already all been manifested (nothing left
   * to pick). Derived from the BOOKED shipments themselves, not the full branch
   * directory — a branch with zero eligible bookings simply doesn't appear.
   */
  private loadEligibleDeliveryBranches(): void {
    if (!this.myBranchId) { this.loadingBranches.set(false); return; }
    this.loadingBranches.set(true);
    this.shipmentService.list({
      page: 0, size: 200, bookingBranchId: this.myBranchId, status: 'BOOKED'
    }).subscribe({
      next: (p) => {
        const names = this.branchNames();
        const eligible = new Set(
          p.content.map((s) => s.deliveryBranchId).filter((id) => id !== this.myBranchId));
        const options = [...eligible]
          .map((id) => ({ value: id, label: names.get(id) ?? id }))
          .sort((a, b) => a.label.localeCompare(b.label));
        this.branchOptions.set(options);
        this.loadingBranches.set(false);
      },
      error: () => { this.branchOptions.set([]); this.loadingBranches.set(false); }
    });
  }

  loadOpenManifests(): void {
    this.loadingManifests.set(true);
    this.manifestService.list({
      page: 0, size: 50, status: 'CREATED', sort: this.sortControl.value || 'createdAt,desc',
      deliveryBranchId: this.filterBranchControl.value ?? undefined
    }).subscribe({
      next: (p) => { this.openManifests.set(p.content); this.loadingManifests.set(false); },
      error: () => { this.openManifests.set([]); this.loadingManifests.set(false); }
    });
  }

  private loadBooked(deliveryBranchId: string | null): void {
    this.bookedShipments.set([]);
    this.createForm.get('shipmentIds')!.setValue([]);
    if (!deliveryBranchId || !this.myBranchId) return;
    this.shipmentService.list({
      page: 0, size: 100, bookingBranchId: this.myBranchId, deliveryBranchId, status: 'BOOKED'
    }).subscribe({
      next: (p) => this.bookedShipments.set(p.content),
      error: () => this.bookedShipments.set([])
    });
  }

  createManifest(): void {
    if (this.createForm.invalid || !this.myBranchId) { this.createForm.markAllAsTouched(); return; }
    const v = this.createForm.getRawValue();
    this.creating.set(true);
    this.manifestService.create({
      bookingBranchId: this.myBranchId, deliveryBranchId: v.deliveryBranchId, shipmentIds: v.shipmentIds
    }).subscribe({
      next: (m) => {
        this.creating.set(false);
        this.notify.success(`Manifest ${m.manifestNumber} created.`);
        this.createForm.reset({ deliveryBranchId: null, shipmentIds: [] });
        this.loadOpenManifests();
        this.loadEligibleDeliveryBranches();
      },
      error: (e: HttpErrorResponse) => { this.creating.set(false); this.notify.error(e.error?.message ?? 'Could not create the manifest.'); }
    });
  }
}
