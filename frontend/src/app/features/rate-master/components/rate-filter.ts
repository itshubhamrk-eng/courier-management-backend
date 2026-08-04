import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { RateSearchRequest, RateStatus } from '@core/models/rate.model';

const STATUSES: SelectOption[] = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];

/** Advanced filter for the rate list. Emits a RateSearchRequest; parent merges it in.
 *  Route/service/package/payment-mode options are passed in by the list, already loaded
 *  from the master pickers. */
@Component({
  selector: 'app-rate-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="cf">
      <app-select [control]="c('routeId')" label="Route" [options]="routeOptions()" [multiple]="true" placeholder="Any route" />
      <app-select [control]="c('serviceTypeId')" label="Service Type" [options]="serviceTypeOptions()" [multiple]="true" placeholder="Any service type" />
      <app-select [control]="c('packageTypeId')" label="Package Type" [options]="packageTypeOptions()" [multiple]="true" placeholder="Any package type" />
      <app-select [control]="c('paymentModeId')" label="Payment Mode" [options]="paymentModeOptions()" [multiple]="true" placeholder="Any payment mode" />
      <app-select [control]="c('status')" label="Status" [options]="statuses" [multiple]="true" placeholder="Any status" />

      <div class="cf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`.cf { display:flex; flex-direction:column; gap:16px; } .cf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }`]
})
export class RateFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<RateSearchRequest>();

  readonly routeOptions = input<SelectOption[]>([]);
  readonly serviceTypeOptions = input<SelectOption[]>([]);
  readonly packageTypeOptions = input<SelectOption[]>([]);
  readonly paymentModeOptions = input<SelectOption[]>([]);

  protected readonly statuses = STATUSES;

  protected readonly form: FormGroup = this.fb.group({
    routeId: [[] as string[]], serviceTypeId: [[] as string[]], packageTypeId: [[] as string[]],
    paymentModeId: [[] as string[]], status: [[] as string[]]
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    this.changed.emit({
      routeId: v.routeId?.length ? v.routeId : undefined,
      serviceTypeId: v.serviceTypeId?.length ? v.serviceTypeId : undefined,
      packageTypeId: v.packageTypeId?.length ? v.packageTypeId : undefined,
      paymentModeId: v.paymentModeId?.length ? v.paymentModeId : undefined,
      status: v.status?.length ? (v.status as RateStatus[]) : undefined
    });
  }

  protected clear(): void {
    this.form.reset({ routeId: [], serviceTypeId: [], packageTypeId: [], paymentModeId: [], status: [] });
    this.changed.emit({});
  }
}
