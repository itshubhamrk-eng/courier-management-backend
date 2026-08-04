import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { CustomerStatus } from '@core/models/customer.model';

/** Customer state pill — ACTIVE / INACTIVE, mapped to the shared badge tones. */
@Component({
  selector: 'app-customer-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="status()" [label]="pretty()" />`
})
export class CustomerStatusBadge {
  readonly status = input.required<CustomerStatus>();
  pretty(): string { const v = this.status(); return v.charAt(0) + v.slice(1).toLowerCase(); }
}
