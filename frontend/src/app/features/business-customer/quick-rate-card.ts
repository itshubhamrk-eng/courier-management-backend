import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { SaveRateRequest } from '@core/models/pricing-zone.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiInput } from '@shared/components/ui-input/ui-input';

/**
 * Simple rate entry: one flat per-kg rate plus the zone charges. Used where a zone is created
 * (the caller saves the rate once the zone exists). Blank per-kg rate = no rate. Per-kg slabs
 * can be refined later on the Rate page.
 */
@Component({
  selector: 'app-quick-rate-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiInput],
  template: `
    <app-card [title]="title()" [subtitle]="subtitle()">
      <div class="grid">
        <app-input [control]="c('ratePerKg')" label="Rate per kg" type="number" [min]="0" [step]="0.5" placeholder="Blank = add rate later" />
        <app-input [control]="c('minChargeableWeight')" label="Min Chargeable Weight (kg)" type="number" [min]="0" [step]="0.5" />
        <app-input [control]="c('docketCharge')" label="Docket Charge" type="number" [min]="0" />
        <app-input [control]="c('mathadiCharge')" label="Mathadi Charge" type="number" [min]="0" />
        <app-input [control]="c('packCharge')" label="Pack Charge" type="number" [min]="0" />
        <app-input [control]="c('odaCharge')" label="ODA Charge" type="number" [min]="0" />
        <app-input [control]="c('fuelSurchargePercent')" label="Fuel Surcharge %" type="number" [min]="0" [max]="100" [step]="0.5" />
        <app-input [control]="c('fovPercent')" label="FOV %" type="number" [min]="0" [max]="100" [step]="0.1" />
      </div>
    </app-card>
  `,
  styles: [`
    .grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px 20px; align-items:end; }
    @media (max-width:560px){ .grid { grid-template-columns:1fr; } }
  `]
})
export class QuickRateCard {
  private readonly fb = inject(FormBuilder);

  readonly title = input('Rate');
  readonly subtitle = input('');

  protected readonly group: FormGroup = this.fb.group({
    ratePerKg: [null as number | null, [Validators.min(0)]],
    minChargeableWeight: [null as number | null, [Validators.min(0)]],
    docketCharge: [null as number | null, [Validators.min(0)]],
    mathadiCharge: [null as number | null, [Validators.min(0)]],
    packCharge: [null as number | null, [Validators.min(0)]],
    odaCharge: [null as number | null, [Validators.min(0)]],
    fuelSurchargePercent: [null as number | null, [Validators.min(0), Validators.max(100)]],
    fovPercent: [null as number | null, [Validators.min(0), Validators.max(100)]]
  });

  protected c(name: string): FormControl { return this.group.get(name) as FormControl; }

  /** False (fields marked) when a number is out of range. */
  validate(): boolean { this.group.markAllAsTouched(); return this.group.valid; }

  /** The rate to save, or null when no per-kg rate was entered. */
  value(): SaveRateRequest | null {
    const v = this.group.getRawValue();
    if (v.ratePerKg === null || v.ratePerKg === undefined || v.ratePerKg === '') return null;
    const num = (x: unknown) => (x === null || x === undefined || x === '' ? 0 : Number(x));
    return {
      minChargeableWeight: num(v.minChargeableWeight), docketCharge: num(v.docketCharge),
      mathadiCharge: num(v.mathadiCharge), packCharge: num(v.packCharge), odaCharge: num(v.odaCharge),
      fuelSurchargePercent: num(v.fuelSurchargePercent), fovPercent: num(v.fovPercent),
      slabs: [{ minWeight: 0, maxWeight: null, ratePerKg: Number(v.ratePerKg) }]
    };
  }
}
