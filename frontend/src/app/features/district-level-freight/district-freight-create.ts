import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateDistrictLevelFreightRequest, UpdateDistrictLevelFreightRequest } from '@core/models/district-level-freight.model';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import { DistrictFreightForm } from './components/district-freight-form';
import { DistrictLevelFreightService } from './district-level-freight.service';

/** Create — wraps DistrictFreightForm in create mode and posts. */
@Component({
  selector: 'app-district-freight-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DistrictFreightForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New District Level Freight Rate</h1><p class="text-caption">Set the six weight-slab rates and ODA for one From Station + District.</p></div>
      </header>
      <app-district-freight-form mode="create" [saving]="saving()"
        [branchOptions]="branchOptions()" [districtOptions]="districtOptions()"
        (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class DistrictFreightCreate {
  private readonly service = inject(DistrictLevelFreightService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);
  readonly branchOptions = signal<SelectOption[]>([]);
  readonly districtOptions = signal<SelectOption[]>([]);

  constructor() {
    this.breadcrumb.set([{ label: 'District Level Freight', route: '/district-level-freight' }, { label: 'New' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.masters.options('districts').subscribe((o) => this.districtOptions.set(o));
  }

  save(body: CreateDistrictLevelFreightRequest | UpdateDistrictLevelFreightRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateDistrictLevelFreightRequest).subscribe({
      next: (r) => {
        this.saving.set(false);
        this.notify.success('District Level Freight rate created.');
        this.router.navigate(['/district-level-freight', r.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'This From Station + District combination already exists.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the rate.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/district-level-freight']); }
}
