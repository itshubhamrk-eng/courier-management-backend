import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { BranchStatus } from '@core/models/branch.model';

/** Branch state pill — ACTIVE / INACTIVE, mapped to the shared badge tones. */
@Component({
  selector: 'app-branch-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="status()" [label]="pretty()" />`
})
export class BranchStatusBadge {
  readonly status = input.required<BranchStatus>();
  pretty(): string { const v = this.status(); return v.charAt(0) + v.slice(1).toLowerCase(); }
}
