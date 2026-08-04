import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { CustomerSearchRequest, CustomerStatus, CustomerType, CUSTOMER_TYPES } from '@core/models/customer.model';

const TYPES: SelectOption[] = CUSTOMER_TYPES.map((t) => ({
  value: t, label: t.charAt(0) + t.slice(1).toLowerCase()
}));
const STATUSES: SelectOption[] = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];

/** Advanced filter for the customer list. Emits a CustomerSearchRequest; parent merges it in. */
@Component({
  selector: 'app-customer-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="cf">
      <app-select [control]="c('customerType')" label="Type" [options]="types" [multiple]="true" placeholder="Any type" />
      <app-select [control]="c('status')" label="Status" [options]="statuses" [multiple]="true" placeholder="Any status" />

      <div class="cf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`.cf { display:flex; flex-direction:column; gap:16px; } .cf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }`]
})
export class CustomerFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<CustomerSearchRequest>();

  protected readonly types = TYPES;
  protected readonly statuses = STATUSES;

  protected readonly form: FormGroup = this.fb.group({
    customerType: [[] as string[]], status: [[] as string[]]
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    this.changed.emit({
      customerType: v.customerType?.length ? (v.customerType as CustomerType[]) : undefined,
      status: v.status?.length ? (v.status as CustomerStatus[]) : undefined
    });
  }

  protected clear(): void {
    this.form.reset({ customerType: [], status: [] });
    this.changed.emit({});
  }
}
