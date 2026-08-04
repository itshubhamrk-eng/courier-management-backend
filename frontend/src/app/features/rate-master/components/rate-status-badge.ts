import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { RateStatus } from '@core/models/rate.model';

/** Rate state pill — ACTIVE / INACTIVE, mapped to the shared badge tones. */
@Component({
  selector: 'app-rate-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="status()" [label]="pretty()" />`
})
export class RateStatusBadge {
  readonly status = input.required<RateStatus>();
  pretty(): string { const v = this.status(); return v.charAt(0) + v.slice(1).toLowerCase(); }
}
