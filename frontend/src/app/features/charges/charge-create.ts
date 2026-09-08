import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateChargeRequest, UpdateChargeRequest } from '@core/models/charge.model';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import { ChargeForm } from './components/charge-form';
import { ChargeService } from './charge.service';

/** Create Charge — wraps ChargeForm in create mode and posts. Settings are added next,
 *  on the detail page, once the charge has a real id. */
@Component({
  selector: 'app-charge-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ChargeForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New Charge</h1><p class="text-caption">Name it and pick a service type. Add its charge settings next.</p></div>
      </header>
      <app-charge-form mode="create" [saving]="saving()" [serviceTypeOptions]="serviceTypeOptions()"
                       (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class ChargeCreate {
  private readonly service = inject(ChargeService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);
  readonly serviceTypeOptions = signal<SelectOption[]>([]);

  constructor() {
    this.breadcrumb.set([{ label: 'Charges', route: '/charges' }, { label: 'New' }]);
    this.masters.options('service-types').subscribe((o) => this.serviceTypeOptions.set(o));
  }

  save(body: CreateChargeRequest | UpdateChargeRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateChargeRequest).subscribe({
      next: (c) => {
        this.saving.set(false);
        this.notify.success(`Charge "${c.chargeName}" created.`);
        this.router.navigate(['/charges', c.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'That charge name is already in use for this service type.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the charge.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/charges']); }
}
