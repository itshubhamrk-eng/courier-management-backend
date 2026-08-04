import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { RoleStatus } from '@core/models/role.model';

/** Role state pill — ACTIVE / INACTIVE, mapped to the shared badge tones. */
@Component({
  selector: 'app-role-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="status()" [label]="pretty()" />`
})
export class RoleStatusBadge {
  readonly status = input.required<RoleStatus>();
  pretty(): string { const v = this.status(); return v.charAt(0) + v.slice(1).toLowerCase(); }
}
