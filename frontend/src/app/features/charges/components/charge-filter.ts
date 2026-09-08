import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { ChargeSearchRequest, ChargeStatus } from '@core/models/charge.model';

const STATUSES: SelectOption[] = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];

/** Advanced filter for the charge list. Emits a ChargeSearchRequest; parent merges it in.
 *  Service Type options are passed in by the list, already loaded from the master picker. */
@Component({
  selector: 'app-charge-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="cf">
      <app-select [control]="c('serviceTypeId')" label="Service Type" [options]="serviceTypeOptions()" [multiple]="true" placeholder="Any service type" />
      <app-select [control]="c('status')" label="Status" [options]="statuses" [multiple]="true" placeholder="Any status" />

      <div class="cf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`.cf { display:flex; flex-direction:column; gap:16px; } .cf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }`]
})
export class ChargeFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<ChargeSearchRequest>();

  readonly serviceTypeOptions = input<SelectOption[]>([]);

  protected readonly statuses = STATUSES;

  protected readonly form: FormGroup = this.fb.group({
    serviceTypeId: [[] as string[]], status: [[] as string[]]
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    this.changed.emit({
      serviceTypeId: v.serviceTypeId?.length ? v.serviceTypeId : undefined,
      status: v.status?.length ? (v.status as ChargeStatus[]) : undefined
    });
  }

  protected clear(): void {
    this.form.reset({ serviceTypeId: [], status: [] });
    this.changed.emit({});
  }
}
