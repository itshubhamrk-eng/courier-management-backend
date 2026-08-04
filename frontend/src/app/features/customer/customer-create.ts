import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateCustomerRequest, UpdateCustomerRequest } from '@core/models/customer.model';
import { CustomerForm } from './components/customer-form';
import { CustomerService } from './customer.service';

/** Create Customer — wraps CustomerForm in create mode and posts. */
@Component({
  selector: 'app-customer-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CustomerForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">New Customer</h1><p class="text-caption">Register reusable customer master data for your company.</p></div>
      </header>
      <app-customer-form mode="create" [saving]="saving()" (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class CustomerCreate {
  private readonly service = inject(CustomerService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);

  constructor() {
    this.breadcrumb.set([{ label: 'Customers', route: '/customers' }, { label: 'New' }]);
  }

  save(body: CreateCustomerRequest | UpdateCustomerRequest): void {
    this.saving.set(true);
    this.service.create(body as CreateCustomerRequest).subscribe({
      next: (c) => {
        this.saving.set(false);
        this.notify.success(`Customer ${c.customerCode} created.`);
        this.router.navigate(['/customers', c.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'Customer code or mobile already in use.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the customer.');
      }
    });
  }

  cancel(): void { this.router.navigate(['/customers']); }
}
