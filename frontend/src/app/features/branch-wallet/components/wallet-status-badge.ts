import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { WalletStatus } from '@core/models/wallet.model';

/** Wallet state pill — ACTIVE / INACTIVE / SUSPENDED / CLOSED, mapped to shared badge tones. */
@Component({
  selector: 'app-wallet-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusBadge],
  template: `<app-status-badge [value]="status()" [label]="label()" [tone]="tone()" />`
})
export class WalletStatusBadge {
  readonly status = input.required<WalletStatus>();
  readonly label = computed(() => { const v = this.status(); return v.charAt(0) + v.slice(1).toLowerCase(); });
  readonly tone = computed<'success' | 'warning' | 'danger' | 'neutral'>(() => {
    switch (this.status()) {
      case 'ACTIVE': return 'success';
      case 'SUSPENDED': return 'warning';
      case 'CLOSED': return 'danger';
      default: return 'neutral';
    }
  });
}
