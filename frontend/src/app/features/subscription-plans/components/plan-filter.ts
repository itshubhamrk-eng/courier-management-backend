import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { PlanType, PLAN_TYPES, SubscriptionPlanSearchRequest } from '@core/models/subscription-plan.model';

const STATUSES: SelectOption[] = [
  { value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }
];
const TYPES: SelectOption[] = PLAN_TYPES.map((t) => ({
  value: t, label: t.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}));

/** Advanced filter for the subscription-plan list. Emits a SubscriptionPlanSearchRequest. */
@Component({
  selector: 'app-plan-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="pf">
      <app-select [control]="c('isActive')" label="Status" [options]="statuses" [allowEmpty]="true" emptyLabel="Any status" />
      <app-select [control]="c('planType')" label="Tier" [options]="types" [allowEmpty]="true" emptyLabel="Any tier" />
      <app-input [control]="c('currency')" label="Currency" placeholder="e.g. INR" />
      <div class="pf__row">
        <app-input [control]="c('minPrice')" label="Min monthly price" placeholder="0" />
        <app-input [control]="c('maxPrice')" label="Max monthly price" placeholder="99999" />
      </div>

      <div class="pf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`
    .pf { display:flex; flex-direction:column; gap:16px; }
    .pf__row { display:grid; grid-template-columns:1fr 1fr; gap:16px; }
    .pf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }
    @media (max-width:400px) { .pf__row { grid-template-columns:1fr; } }
  `]
})
export class PlanFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<SubscriptionPlanSearchRequest>();

  protected readonly statuses = STATUSES;
  protected readonly types = TYPES;

  protected readonly form: FormGroup = this.fb.group({
    isActive: [null as string | null], planType: [null as string | null],
    currency: [''], minPrice: [''], maxPrice: ['']
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    const tri = (x: string | null) => (x == null ? undefined : x === 'true');
    const s = (x: string) => (x && x.trim() ? x.trim() : undefined);
    const n = (x: string) => (x && x.trim() ? Number(x) : undefined);
    this.changed.emit({
      isActive: tri(v.isActive), planType: (v.planType as PlanType) ?? undefined,
      currency: s(v.currency), minPrice: n(v.minPrice), maxPrice: n(v.maxPrice)
    });
  }

  protected clear(): void {
    this.form.reset({ isActive: null, planType: null, currency: '', minPrice: '', maxPrice: '' });
    this.changed.emit({});
  }
}
