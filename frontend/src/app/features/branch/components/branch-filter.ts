import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { BranchSearchRequest, BranchStatus, BranchType, BRANCH_TYPES } from '@core/models/branch.model';

const TYPES: SelectOption[] = BRANCH_TYPES.map((t) => ({
  value: t, label: t.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}));
const STATUSES: SelectOption[] = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];
const CAP: SelectOption[] = [{ value: 'true', label: 'Enabled' }, { value: 'false', label: 'Disabled' }];

/** Advanced filter for the branch list. Emits a BranchSearchRequest; parent merges it in. */
@Component({
  selector: 'app-branch-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="bf">
      <app-select [control]="c('branchType')" label="Type" [options]="types" [multiple]="true" placeholder="Any type" />
      <app-select [control]="c('status')" label="Status" [options]="statuses" [multiple]="true" placeholder="Any status" />
      <app-input [control]="c('city')" label="City" placeholder="e.g. Pune" [maxLength]="100" />
      <app-input [control]="c('state')" label="State" placeholder="e.g. Maharashtra" [maxLength]="100" />
      <app-input [control]="c('postalCode')" label="Pincode" placeholder="e.g. 411005" [maxLength]="20" />
      <app-select [control]="c('allowBooking')" label="Booking" [options]="cap" [allowEmpty]="true" emptyLabel="Any" />
      <app-select [control]="c('allowDelivery')" label="Delivery" [options]="cap" [allowEmpty]="true" emptyLabel="Any" />
      <app-select [control]="c('allowPickup')" label="Pickup" [options]="cap" [allowEmpty]="true" emptyLabel="Any" />

      <div class="bf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`.bf { display:flex; flex-direction:column; gap:16px; } .bf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }`]
})
export class BranchFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<BranchSearchRequest>();

  protected readonly types = TYPES;
  protected readonly statuses = STATUSES;
  protected readonly cap = CAP;

  protected readonly form: FormGroup = this.fb.group({
    branchType: [[] as string[]], status: [[] as string[]],
    city: [''], state: [''], postalCode: [''],
    allowBooking: [null as string | null], allowDelivery: [null as string | null], allowPickup: [null as string | null]
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    const tri = (x: string | null) => (x == null ? undefined : x === 'true');
    const s = (x: string) => (x && x.trim() ? x.trim() : undefined);
    this.changed.emit({
      branchType: v.branchType?.length ? (v.branchType as BranchType[]) : undefined,
      status: v.status?.length ? (v.status as BranchStatus[]) : undefined,
      city: s(v.city), state: s(v.state), postalCode: s(v.postalCode),
      allowBooking: tri(v.allowBooking), allowDelivery: tri(v.allowDelivery), allowPickup: tri(v.allowPickup)
    });
  }

  protected clear(): void {
    this.form.reset({ branchType: [], status: [], city: '', state: '', postalCode: '', allowBooking: null, allowDelivery: null, allowPickup: null });
    this.changed.emit({});
  }
}
