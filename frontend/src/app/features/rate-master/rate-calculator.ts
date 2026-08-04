import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { RateCalculatorForm } from './components/rate-calculator-form';

/** Rate Calculator — a standalone page wrapping the same form the quick-lookup dialog uses,
 *  for a booking desk that wants to price a shipment without opening the rate list. */
@Component({
  selector: 'app-rate-calculator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiCard, RateCalculatorForm],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Rate Calculator</h1><p class="text-caption">Price a shipment without booking it.</p></div>
      </header>
      <app-card>
        <app-rate-calculator-form />
      </app-card>
    </div>
  `
})
export class RateCalculator {
  private readonly breadcrumb = inject(BreadcrumbService);
  constructor() { this.breadcrumb.set([{ label: 'Rate Master', route: '/rates' }, { label: 'Calculator' }]); }
}
