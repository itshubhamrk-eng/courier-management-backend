import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import {
  PermissionSearchRequest, PermissionStatus, PermissionAction,
  PERMISSION_MODULES, PERMISSION_ACTIONS, prettyToken
} from '@core/models/permission.model';

const opts = (values: string[]): SelectOption[] => values.map((v) => ({ value: v, label: prettyToken(v) }));

const STATUSES: SelectOption[] = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];
const KIND: SelectOption[] = [{ value: 'true', label: 'System' }, { value: 'false', label: 'Custom' }];
const GATED: SelectOption[] = [{ value: 'true', label: 'Plan-gated only' }];

/** Advanced filter for the permission catalogue. Emits a PermissionSearchRequest. */
@Component({
  selector: 'app-permission-filter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="apply()" class="pf">
      <app-select [control]="c('module')" label="Module" [options]="modules" [multiple]="true" placeholder="Any module" />
      <app-select [control]="c('action')" label="Action" [options]="actions" [multiple]="true" placeholder="Any action" />
      <app-select [control]="c('status')" label="Status" [options]="statuses" [allowEmpty]="true" emptyLabel="Any status" />
      <app-select [control]="c('isSystemPermission')" label="Kind" [options]="kinds" [allowEmpty]="true" emptyLabel="Any kind" />
      <app-select [control]="c('planGatedOnly')" label="Plan gating" [options]="gated" [allowEmpty]="true" emptyLabel="Any" />
      <app-input [control]="c('resource')" label="Resource" placeholder="e.g. shipments, rate-master" [maxLength]="100" />

      <div class="pf__bar">
        <app-button variant="text" (pressed)="clear()">Clear all</app-button>
        <app-button type="submit" icon="filter_list">Apply filters</app-button>
      </div>
    </form>
  `,
  styles: [`.pf { display:flex; flex-direction:column; gap:16px; } .pf__bar { display:flex; justify-content:space-between; gap:10px; margin-top:8px; }`]
})
export class PermissionFilter {
  private readonly fb = inject(FormBuilder);
  readonly changed = output<PermissionSearchRequest>();

  protected readonly modules = opts(PERMISSION_MODULES);
  protected readonly actions = opts(PERMISSION_ACTIONS);
  protected readonly statuses = STATUSES;
  protected readonly kinds = KIND;
  protected readonly gated = GATED;

  protected readonly form: FormGroup = this.fb.group({
    module: [[] as string[]], action: [[] as string[]],
    status: [null as string | null], isSystemPermission: [null as string | null],
    planGatedOnly: [null as string | null], resource: ['']
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected apply(): void {
    const v = this.form.getRawValue();
    const tri = (x: string | null) => (x == null ? undefined : x === 'true');
    const s = (x: string) => (x && x.trim() ? x.trim() : undefined);
    this.changed.emit({
      module: v.module?.length ? (v.module as string[]) : undefined,
      action: v.action?.length ? (v.action as PermissionAction[]) : undefined,
      status: (v.status as PermissionStatus) ?? undefined,
      isSystemPermission: tri(v.isSystemPermission),
      planGatedOnly: tri(v.planGatedOnly) || undefined,
      resource: s(v.resource)
    });
  }

  protected clear(): void {
    this.form.reset({ module: [], action: [], status: null, isSystemPermission: null, planGatedOnly: null, resource: '' });
    this.changed.emit({});
  }
}
