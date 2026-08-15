import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { CreateVehicleRequest, UpdateVehicleRequest } from '@core/models/shipment.model';
import { VehicleForm } from './components/vehicle-form';
import { VehicleService } from './vehicle.service';

/** Create Vehicle — a routed page, not a dialog (see `VehicleForm`'s own note). */
@Component({
  selector: 'app-vehicle-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [VehicleForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Add Vehicle</h1><p class="text-caption">Register a fleet vehicle.</p></div>
      </header>
      <app-vehicle-form mode="create" [branchOptions]="branchOptions()" [saving]="saving()"
                        (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class VehicleCreate implements OnInit {
  private readonly service = inject(VehicleService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);
  readonly branchOptions = signal<SelectOption[]>([]);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Vehicles', route: '/masters/vehicles' }, { label: 'New' }]);
    this.masters.branchDirectory().subscribe((branches) =>
      this.branchOptions.set(branches.map((b) => ({ value: b.id, label: `${b.branchName} (${b.branchCode})` }))));
  }

  save(body: CreateVehicleRequest | UpdateVehicleRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateVehicleRequest).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Vehicle added.'); this.router.navigate(['/masters/vehicles']); },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'Vehicle number already in use.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not add the vehicle.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/masters/vehicles']); }
}
