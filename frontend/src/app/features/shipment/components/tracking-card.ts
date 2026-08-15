import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ShipmentStatusBadge } from './shipment-status-badge';
import { ShipmentStatus } from '@core/models/shipment.model';

/** The identity banner every shipment screen leads with: shipment number, AWB, status and
 *  the two dates that matter at a glance. Used on the detail page and the booking
 *  confirmation step. */
@Component({
  selector: 'app-tracking-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ShipmentStatusBadge],
  template: `
    <div class="tc">
      <div class="tc__ids">
        <div class="tc__id">
          <span class="tc__label">Shipment No.</span>
          <span class="tc__value mono">{{ shipmentNumber() }}</span>
        </div>
        <div class="tc__id">
          <span class="tc__label">Tracking No. (AWB)</span>
          <span class="tc__value mono awb">{{ trackingNumber() }}</span>
        </div>
      </div>
      <div class="tc__meta">
        <app-shipment-status-badge [status]="status()" />
        <span class="tc__date">Booked {{ bookingDate() | date: 'mediumDate' }}</span>
        @if (expectedDeliveryDate()) {
          <span class="tc__date">Expected {{ expectedDeliveryDate() | date: 'mediumDate' }}</span>
        }
      </div>
    </div>
  `,
  styles: [`
    .tc { display:flex; align-items:center; justify-content:space-between; gap:16px; flex-wrap:wrap;
      padding:22px 26px; border:0; border-radius:var(--r-card-lg); background:var(--surface); box-shadow:var(--shadow-clay); }
    .tc__ids { display:flex; gap:32px; flex-wrap:wrap; }
    .tc__id { display:flex; flex-direction:column; gap:4px; }
    .tc__label { font:500 12px var(--font-sans); color:var(--content-muted); }
    .tc__value { font:700 17px var(--font-mono, ui-monospace); color:var(--content-fg); }
    .tc__value.awb { color:var(--brand-600); }
    .tc__meta { display:flex; align-items:center; gap:14px; flex-wrap:wrap; }
    .tc__meta app-shipment-status-badge ::ng-deep .badge { height:28px; padding:0 14px; font-size:13px; }
    .tc__date { font:400 13px var(--font-sans); color:var(--content-muted); }
    .mono { font-family:var(--font-mono, ui-monospace); }
  `]
})
export class TrackingCard {
  readonly shipmentNumber = input.required<string>();
  readonly trackingNumber = input.required<string>();
  readonly status = input.required<ShipmentStatus>();
  readonly bookingDate = input.required<string>();
  readonly expectedDeliveryDate = input<string | null>(null);
}
