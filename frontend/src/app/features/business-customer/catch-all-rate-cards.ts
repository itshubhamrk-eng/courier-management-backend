import { ChangeDetectionStrategy, Component, viewChildren } from '@angular/core';
import { FallbackScope, SaveRateRequest } from '@core/models/pricing-zone.model';
import { QuickRateCard } from './quick-rate-card';

/**
 * The two catch-all rate cards on business-customer creation: "Rest of Maharashtra" and
 * "Out of Maharashtra". A card with no per-kg rate is skipped; the parent creates the zones
 * and saves the rates once the customer exists.
 */
@Component({
  selector: 'app-catch-all-rate-cards',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuickRateCard],
  template: `
    <div class="cards">
      <app-quick-rate-card title="Rest of Maharashtra" subtitle="Rate for every Maharashtra destination that is not in one of this customer's own zones." />
      <app-quick-rate-card title="Out of Maharashtra" subtitle="Rate for every destination outside Maharashtra." />
    </div>
  `,
  styles: [`
    .cards { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
    @media (max-width:1100px){ .cards { grid-template-columns:1fr; } }
  `]
})
export class CatchAllRateCards {
  private readonly cards = viewChildren(QuickRateCard);
  private static readonly SCOPES: FallbackScope[] = ['REST_OF_STATE', 'OUT_OF_STATE'];

  validate(): boolean { return this.cards().map((c) => c.validate()).every(Boolean); }

  /** The rate to save per scope; scopes with no per-kg rate are left out. */
  values(): Partial<Record<FallbackScope, SaveRateRequest>> {
    const out: Partial<Record<FallbackScope, SaveRateRequest>> = {};
    this.cards().forEach((card, i) => { const v = card.value(); if (v) out[CatchAllRateCards.SCOPES[i]] = v; });
    return out;
  }
}
