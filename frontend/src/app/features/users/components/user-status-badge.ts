import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { UserStatus } from '@core/models/user.model';

/**
 * User state pill. An admin hard-lock trumps the lifecycle status, so a LOCKED user reads
 * "Locked" regardless of the underlying ACTIVE/DISABLED state (mirrors the backend gate).
 */
@Component({
  selector: 'app-user-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="locked() ? 'LOCKED' : status()" [label]="pretty()" />`
})
export class UserStatusBadge {
  readonly status = input.required<UserStatus>();
  readonly locked = input(false);

  pretty(): string {
    const v = this.locked() ? 'LOCKED' : this.status();
    return v.charAt(0) + v.slice(1).toLowerCase();
  }
}
