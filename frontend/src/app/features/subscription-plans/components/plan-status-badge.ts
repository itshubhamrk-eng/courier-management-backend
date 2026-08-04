import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';

/** Plan state pill — Active / Inactive, mapped to the shared badge tones. */
@Component({
  selector: 'app-plan-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="status() ? 'ACTIVE' : 'INACTIVE'" [label]="status() ? 'Active' : 'Inactive'" />`
})
export class PlanStatusBadge {
  readonly status = input.required<boolean>();
}
