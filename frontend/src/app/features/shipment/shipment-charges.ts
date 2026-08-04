import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { ShipmentCharge } from '@core/models/shipment.model';
import { ChargeSummary } from './components/charge-summary';
import { ShipmentService } from './shipment.service';

/** Shipment Charges — the Pricing Engine's own breakup, persisted at booking time and
 *  never recomputed from a later rate/config change. GET /shipments/{id}/charges. */
@Component({
  selector: 'app-shipment-charges',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, UiCard, UiLoader, ChargeSummary],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Shipment Charges</h1>
          <p class="text-caption"><a [routerLink]="['/shipments', id]">← Back to shipment</a></p></div>
      </header>

      @if (loading()) {
        <app-loader [minHeight]="240" caption="Loading…" />
      } @else if (!charges()) {
        <app-card><p class="empty">No charge breakup on file for this shipment.</p></app-card>
      } @else {
        <app-card title="Charge Breakup"
                  [subtitle]="(charges()!.matchedRouteCode ? 'Route ' + charges()!.matchedRouteCode + ' · ' : '') + (charges()!.matchedRateCode ? 'Rate ' + charges()!.matchedRateCode : '')">
          <app-charge-summary [charges]="{
            freight: charges()!.freight, fuelCharge: charges()!.fuelCharge, handlingCharge: charges()!.handlingCharge,
            odaCharge: charges()!.odaCharge, insuranceCharge: charges()!.insuranceCharge, gstAmount: charges()!.gstAmount,
            discountAmount: charges()!.discountAmount, roundOff: charges()!.roundOff, netAmount: charges()!.netAmount
          }" />
        </app-card>
      }
    </div>
  `,
  styles: [`.empty{ font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:24px; }`]
})
export class ShipmentCharges implements OnInit {
  private readonly service = inject(ShipmentService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(true);
  readonly charges = signal<ShipmentCharge | null>(null);
  id = '';

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.breadcrumb.set([{ label: 'Shipments', route: '/shipments' }, { label: 'Charges' }]);
    this.service.charges(this.id).subscribe({
      next: (c) => { this.charges.set(c); this.loading.set(false); },
      error: () => { this.charges.set(null); this.loading.set(false); }
    });
  }
}
