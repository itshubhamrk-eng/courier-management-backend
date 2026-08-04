import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { Manifest, DispatchManifestResponse } from '@core/models/shipment.model';
import { ManifestService } from '@features/manifest/manifest.service';
import { VehicleService } from '@features/manifest/vehicle.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentMovementService } from './shipment-movement.service';
import { ManifestCard } from './components/manifest-card';

/** Dispatch — a worklist of every open ("out scan created") manifest, each carrying its
 *  own Dispatch action; picking one opens Assign Vehicle & Driver below. Requires the
 *  manifest to already carry at least one MANIFEST_CREATED ("out scan created")
 *  shipment (enforced server-side). */
@Component({
  selector: 'app-dispatch',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiLoader, UiButton, UiSelect, UiInput, ManifestCard],
  template: `
    <div class="page">
      <header class="page__head" data-tour="dispatch-head">
        <div><h1 class="text-h1">Dispatch</h1>
          <p class="text-caption">Assign a vehicle and driver, then send the manifest out.</p></div>
        <app-button variant="stroked" icon="refresh" (pressed)="loadOpenManifests()">Refresh</app-button>
      </header>

      @if (!manifest()) {
        <app-card title="Search Manifest">
          <div class="row">
            <app-input [control]="searchControl" label="Manifest Number" placeholder="MFT-260101-1234" (keydown.enter)="search()" />
            <app-button icon="search" [loading]="searching()" (pressed)="search()">Search</app-button>
          </div>
        </app-card>

        <h2 class="text-h2 section-title">Out Scan Created</h2>
        @if (loadingManifests()) {
          <app-loader [minHeight]="120" caption="Loading…" />
        } @else if (!openManifests().length) {
          <app-card><p class="empty">No manifest is ready to dispatch.</p></app-card>
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
            <app-button variant="stroked" icon="close" (pressed)="reset()">Change Manifest</app-button>
          </div>
        </app-card>

        @if (m.status === 'CREATED') {
          <app-card title="Assign Vehicle & Driver">
            <form [formGroup]="form" (ngSubmit)="dispatch()" class="df">
              <div class="grid2">
                <app-select [control]="c('vehicleId')" label="Vehicle" [options]="vehicleOptions()" placeholder="Select vehicle" />
                <app-select [control]="c('driverUserId')" label="Driver" [options]="driverOptions()" placeholder="Select driver" />
              </div>
              @if (!vehicleOptions().length) { <p class="empty">No active vehicles — add one first.</p> }
              <div class="df__bar">
                <app-button type="submit" icon="outbound" [loading]="dispatching()">Dispatch</app-button>
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
    .section-title { margin:8px 0 0; }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    @media (max-width:760px){ .grid2 { grid-template-columns:1fr; } }
  `]
})
export class Dispatch implements OnInit {
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
  readonly vehicleOptions = signal<SelectOption[]>([]);
  readonly driverOptions = signal<SelectOption[]>([]);
  readonly openManifests = signal<Manifest[]>([]);
  readonly loadingManifests = signal(true);
  readonly branchNames = signal<Map<string, string>>(new Map());

  readonly searchControl = new FormControl('');
  readonly form: FormGroup = this.fb.group({
    vehicleId: [null as string | null, Validators.required],
    driverUserId: [null as string | null, Validators.required]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Dispatch' }]);
    this.vehicleService.list(true).subscribe((v) =>
      this.vehicleOptions.set(v.map((x) => ({ value: x.id, label: x.vehicleNumber }))));
    this.movementService.userOptions().subscribe((u) =>
      this.driverOptions.set(u.map((x) => ({ value: x.id, label: x.label }))));
    this.masterData.branchDirectory().subscribe((list) =>
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`]))));
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
      },
      error: (e: HttpErrorResponse) => { this.searching.set(false); this.notify.error(e.error?.message ?? 'Search failed.'); }
    });
  }

  reset(): void {
    this.manifest.set(null);
    this.result.set(null);
    this.form.reset();
  }

  dispatch(): void {
    const manifest = this.manifest();
    if (!manifest || this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.dispatching.set(true);
    this.movementService.dispatch({
      manifestId: manifest.id, vehicleId: v.vehicleId, driverUserId: v.driverUserId
    }).subscribe({
      next: (r) => {
        this.dispatching.set(false);
        this.result.set(r);
        this.manifest.set({ ...manifest, status: r.status, vehicleId: r.vehicleId, driverUserId: r.driverUserId, dispatchedAt: r.dispatchedAt });
        this.notify.success(`Manifest ${r.manifestNumber} dispatched.`);
        this.loadOpenManifests();
      },
      error: (e: HttpErrorResponse) => { this.dispatching.set(false); this.notify.error(e.error?.message ?? 'Could not dispatch the manifest.'); }
    });
  }
}
