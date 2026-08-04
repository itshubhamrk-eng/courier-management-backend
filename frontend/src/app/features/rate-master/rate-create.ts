import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateRateRequest, UpdateRateRequest } from '@core/models/rate.model';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import { RateForm } from './components/rate-form';
import { RateService } from './rate.service';

/** Create Rate — wraps RateForm in create mode and posts. */
@Component({
  selector: 'app-rate-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RateForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New Rate</h1><p class="text-caption">Price one weight slab for a Route + Service Type + Package Type + Payment Mode.</p></div>
      </header>
      <app-rate-form mode="create" [saving]="saving()"
                    [routeOptions]="routeOptions()" [serviceTypeOptions]="serviceTypeOptions()"
                    [packageTypeOptions]="packageTypeOptions()" [paymentModeOptions]="paymentModeOptions()"
                    (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class RateCreate {
  private readonly service = inject(RateService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);
  readonly routeOptions = signal<SelectOption[]>([]);
  readonly serviceTypeOptions = signal<SelectOption[]>([]);
  readonly packageTypeOptions = signal<SelectOption[]>([]);
  readonly paymentModeOptions = signal<SelectOption[]>([]);

  constructor() {
    this.breadcrumb.set([{ label: 'Rate Master', route: '/rates' }, { label: 'New' }]);
    this.masters.options('routes').subscribe((o) => this.routeOptions.set(o));
    this.masters.options('service-types').subscribe((o) => this.serviceTypeOptions.set(o));
    this.masters.options('package-types').subscribe((o) => this.packageTypeOptions.set(o));
    this.masters.options('payment-modes').subscribe((o) => this.paymentModeOptions.set(o));
  }

  save(body: CreateRateRequest | UpdateRateRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateRateRequest).subscribe({
      next: (r) => {
        this.saving.set(false);
        this.notify.success(`Rate ${r.rateCode} created.`);
        this.router.navigate(['/rates', r.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'Rate code already in use.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the rate.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/rates']); }
}
