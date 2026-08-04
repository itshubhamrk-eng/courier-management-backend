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
import { ShipmentMovementService } from './shipment-movement.service';
import { Shipment, MovementOutcome } from '@core/models/shipment.model';

/** Out For Delivery — Search Shipment, Assign Delivery User, Bulk Assign. Lists this
 *  branch's own IN_SCAN shipments (delivery branch = my branch), the same "search the
 *  worklist, not the whole company" shape In Scan's receiving-branch default uses. */
@Component({
  selector: 'app-out-for-delivery',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiLoader, UiButton, UiSelect],
  template: `
    <div class="page">
      <header class="page__head" data-tour="out-for-delivery-head">
        <div><h1 class="text-h1">Out For Delivery</h1>
          <p class="text-caption">Assign received shipments to a delivery user.</p></div>
        <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
      </header>

      @if (!myBranchId) {
        <app-card><p class="empty">No branch assigned — ask an admin.</p></app-card>
      } @else {
        <app-card title="Search Shipment" subtitle="IN_SCAN shipments waiting to go out for delivery at your branch.">
          @if (loading()) {
            <app-loader [minHeight]="120" caption="Loading…" />
          } @else if (!shipments().length) {
            <p class="empty">Nothing waiting — every received shipment is already out for delivery.</p>
          } @else {
            <form [formGroup]="form" (ngSubmit)="assign()" class="df">
              <app-select [control]="c('shipmentIds')" [multiple]="true" [options]="shipmentOptions()" placeholder="Select shipments" />
              <app-select [control]="c('deliveryUserId')" label="Delivery User" [options]="userOptions()" placeholder="Select delivery user" />
              <div class="df__bar">
                <app-button type="submit" icon="directions_run" [loading]="assigning()">Bulk Assign</app-button>
              </div>
            </form>
          }
        </app-card>

        @if (outcomes().length) {
          <app-card [title]="'Result (' + successCount() + ' of ' + outcomes().length + ' assigned)'">
            <div class="ol">
              @for (o of outcomes(); track o.reference) {
                <div class="ol__row" [class.ol__row--fail]="!o.success">
                  <span>{{ o.reference }}</span>
                  @if (o.message) { <span class="text-caption">— {{ o.message }}</span> }
                </div>
              }
            </div>
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
  `]
})
export class OutForDelivery implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly movementService = inject(ShipmentMovementService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly loading = signal(true);
  readonly assigning = signal(false);
  readonly shipments = signal<Shipment[]>([]);
  readonly userOptions = signal<SelectOption[]>([]);
  readonly outcomes = signal<MovementOutcome[]>([]);
  readonly successCount = computed(() => this.outcomes().filter((o) => o.success).length);
  protected readonly shipmentOptions = computed<SelectOption[]>(() =>
    this.shipments().map((s) => ({ value: s.id, label: `${s.trackingNumber} — ${s.receiverName}` })));

  readonly form: FormGroup = this.fb.group({
    shipmentIds: [[] as string[], Validators.required],
    deliveryUserId: [null as string | null, Validators.required]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Out For Delivery' }]);
    this.movementService.userOptions().subscribe((u) =>
      this.userOptions.set(u.map((x) => ({ value: x.id, label: x.label }))));
    this.load();
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  load(): void {
    if (!this.myBranchId) return;
    this.loading.set(true);
    this.shipmentService.list({
      page: 0, size: 100, deliveryBranchId: this.myBranchId, status: 'IN_SCAN'
    }).subscribe({
      next: (p) => { this.shipments.set(p.content); this.loading.set(false); },
      error: () => { this.shipments.set([]); this.loading.set(false); }
    });
  }

  assign(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.assigning.set(true);
    this.movementService.outForDelivery({ shipmentIds: v.shipmentIds, deliveryUserId: v.deliveryUserId }).subscribe({
      next: (r) => {
        this.assigning.set(false);
        this.outcomes.set(r.results);
        if (r.failureCount) this.notify.error(`${r.failureCount} of ${r.results.length} could not be assigned.`);
        else this.notify.success(`${r.successCount} shipment(s) assigned.`);
        this.form.reset({ shipmentIds: [], deliveryUserId: v.deliveryUserId });
        this.load();
      },
      error: (e: HttpErrorResponse) => { this.assigning.set(false); this.notify.error(e.error?.message ?? 'Could not assign.'); }
    });
  }
}
