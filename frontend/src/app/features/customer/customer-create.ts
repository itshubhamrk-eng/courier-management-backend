import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { CreateCustomerRequest, CustomerType, UpdateCustomerRequest } from '@core/models/customer.model';
import { BusinessCustomerService } from '@features/business-customer/business-customer.service';
import { FallbackScope } from '@core/models/pricing-zone.model';
import { Observable, concatMap, from, last, of, throwError } from 'rxjs';
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
      <app-customer-form #form mode="create" [initialType]="initialType" [saving]="saving()" (saved)="save($event)" (cancelled)="cancel()" />
    </div>
  `
})
export class CustomerCreate {
  private readonly service = inject(CustomerService);
  private readonly zones = inject(BusinessCustomerService);
  private readonly form = viewChild(CustomerForm);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);

  readonly saving = signal(false);
  protected readonly initialType: CustomerType =
    inject(ActivatedRoute).snapshot.queryParamMap.get('type') === 'BUSINESS' ? 'BUSINESS' : 'INDIVIDUAL';

  constructor() {
    this.breadcrumb.set([{ label: 'Customers', route: '/customers' }, { label: 'New' }]);
  }

  save(body: CreateCustomerRequest | UpdateCustomerRequest): void {
    this.saving.set(true);
    const rates = this.form()?.catchAllRates() ?? {};
    this.service.create(body as CreateCustomerRequest).subscribe({
      next: (c) => {
        // The two catch-all zones are created server-side with the customer; this only fills in the
        // rates typed on the form. A failure leaves the customer created, so say so and let the
        // admin finish on the Rate page.
        const scopes = Object.keys(rates) as FallbackScope[];
        (scopes.length ? this.zones.zones({ customerId: c.id }).pipe(
          concatMap((all) => from(scopes).pipe(
            concatMap((scope) => {
              const zone = all.find((z) => z.fallbackScope === scope);
              return zone ? this.zones.saveRate(zone.id, rates[scope]!) : throwError(() => new Error('missing zone'));
            }),
            last()))) as Observable<unknown> : of(null)).subscribe({
          next: () => this.done(c.customerCode, c.id, scopes.length),
          error: (e: HttpErrorResponse) => {
            this.notify.error(e.error?.message ?? 'The customer was created but a Maharashtra rate could not be saved. Add it under Rate.');
            this.done(c.customerCode, c.id, 0);
          }
        });
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) this.notify.error(err.error?.message ?? 'Customer code or mobile already in use.');
        else if (err.status === 400 || err.status === 422) this.notify.error(err.error?.message ?? 'Please correct the highlighted fields.');
        else this.notify.error('Could not create the customer.');
      }
    });
  }

  private done(code: string, id: string, rateZones: number): void {
    this.saving.set(false);
    this.notify.success(rateZones ? `Customer ${code} created with ${rateZones} catch-all rate${rateZones > 1 ? 's' : ''}.` : `Customer ${code} created.`);
    this.router.navigate(['/customers', id]);
  }

  cancel(): void { this.router.navigate(['/customers']); }
}
