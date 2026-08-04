import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { RoleSearchRequest, RoleStatus, RoleType, ROLE_TYPES } from '@core/models/role.model';

const STATUSES: SelectOption[] = [
  { value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }
];
const TYPES: SelectOption[] = ROLE_TYPES.map((t) => ({
  value: t, label: t.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}));
const KIND: SelectOption[] = [{ value: 'true', label: 'System roles' }, { value: 'false', label: 'Custom roles' }];
const DEFAULT: SelectOption[] = [{ value: 'true', label: 'Default only' }, { value: 'false', label: 'Non-default' }];

/** Advanced filter for the role list. Emits a RoleSearchRequest; parent merges into the query. */
@Component({
  selector: 'app-role-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="rf">
      <app-select [control]="c('status')" label="Status" [options]="statuses" [allowEmpty]="true" emptyLabel="Any status" />
      <app-select [control]="c('roleType')" label="Type" [options]="types" [multiple]="true" placeholder="Any type" />
      <app-select [control]="c('isSystemRole')" label="Kind" [options]="kinds" [allowEmpty]="true" emptyLabel="Any kind" />
      <app-select [control]="c('isDefault')" label="Default role" [options]="defaults" [allowEmpty]="true" emptyLabel="Any" />
      <app-input [control]="c('permissionCode')" label="Grants permission" placeholder="e.g. SHIPMENT_DELETE" />

      <div class="rf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`.rf { display:flex; flex-direction:column; gap:16px; } .rf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }`]
})
export class RoleFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<RoleSearchRequest>();

  protected readonly statuses = STATUSES;
  protected readonly types = TYPES;
  protected readonly kinds = KIND;
  protected readonly defaults = DEFAULT;

  protected readonly form: FormGroup = this.fb.group({
    status: [null as string | null], roleType: [[] as string[]],
    isSystemRole: [null as string | null], isDefault: [null as string | null], permissionCode: ['']
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    const tri = (x: string | null) => (x == null ? undefined : x === 'true');
    const s = (x: string) => (x && x.trim() ? x.trim() : undefined);
    this.changed.emit({
      status: (v.status as RoleStatus) ?? undefined,
      roleType: v.roleType?.length ? (v.roleType as RoleType[]) : undefined,
      isSystemRole: tri(v.isSystemRole), isDefault: tri(v.isDefault),
      permissionCode: s(v.permissionCode)
    });
  }

  protected clear(): void {
    this.form.reset({ status: null, roleType: [], isSystemRole: null, isDefault: null, permissionCode: '' });
    this.changed.emit({});
  }
}
