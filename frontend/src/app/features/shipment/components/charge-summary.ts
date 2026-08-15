import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';

/** Normalised shape both callers adapt to: the Pricing Engine's live `ChargeBreakup`
 *  (Step 3 preview) and the persisted `ShipmentCharge` (GET /shipments/{id}/charges) —
 *  same nine lines, `discountAmount` naming the one field that otherwise differs. */
export interface ChargeSummaryData {
  freight: number;
  fuelCharge: number;
  handlingCharge: number;
  odaCharge: number;
  insuranceCharge: number;
  gstAmount: number;
  discountAmount: number;
  roundOff: number;
  /** Manual, typed at booking time — not part of the Pricing Engine's own rate-driven
   *  lines above; added on top of the engine's net amount by the caller. */
  otherCharges: number;
  netAmount: number;
}

/** Freight through Net Amount — the Pricing Engine's own charge breakup, shown identically
 *  whether it is a live preview or what actually got booked. Other Charges sits above GST
 *  (not calculation order) since `gstAmount` already includes tax on it — see
 *  `ShipmentServiceImpl.copyCharge`. */
@Component({
  selector: 'app-charge-summary',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    <dl class="kv">
      @if (charges().freight) {
        <dt>Freight</dt><dd class="mono">{{ charges().freight | number: '1.2-2' }}</dd>
      }
      @if (charges().fuelCharge) {
        <dt>Fuel Surcharge</dt><dd class="mono">{{ charges().fuelCharge | number: '1.2-2' }}</dd>
      }
      @if (charges().handlingCharge) {
        <dt>Handling</dt><dd class="mono">{{ charges().handlingCharge | number: '1.2-2' }}</dd>
      }
      @if (charges().odaCharge) {
        <dt>ODA</dt><dd class="mono">{{ charges().odaCharge | number: '1.2-2' }}</dd>
      }
      @if (charges().insuranceCharge) {
        <dt>Insurance</dt><dd class="mono">{{ charges().insuranceCharge | number: '1.2-2' }}</dd>
      }
      <dt>Other Charges</dt>
      @if (editable()) {
        <dd class="mono">
          <input class="net-input" type="number" step="0.01" min="0"
                 [value]="charges().otherCharges" (input)="onOtherChargesInput($event)" />
        </dd>
      } @else {
        <dd class="mono">{{ charges().otherCharges | number: '1.2-2' }}</dd>
      }
      @if (charges().gstAmount) {
        <dt>GST</dt><dd class="mono">{{ charges().gstAmount | number: '1.2-2' }}</dd>
      }
      @if (charges().discountAmount) {
        <dt>Discount</dt><dd class="mono discount">-{{ charges().discountAmount | number: '1.2-2' }}</dd>
      }
      @if (charges().roundOff) {
        <dt>Round Off</dt><dd class="mono">{{ charges().roundOff | number: '1.2-2' }}</dd>
      }
      <dt class="total">Net Amount</dt>
      @if (editable()) {
        <dd class="mono total">
          <input class="net-input" type="number" step="0.01" min="0"
                 [value]="charges().netAmount" (input)="onNetAmountInput($event)" />
        </dd>
      } @else {
        <dd class="mono total">{{ charges().netAmount | number: '1.2-2' }}</dd>
      }
    </dl>
  `,
  styles: [`
    .kv { display:grid; grid-template-columns:1fr auto; gap:8px 16px; margin:0; }
    .kv dt { font:500 13px var(--font-sans); color:var(--content-muted); }
    .kv dd { font:600 14px var(--font-sans); color:var(--content-fg); margin:0; text-align:right; }
    .discount { color:var(--success); }
    .kv .total { font-weight:700; font-size:16px; color:var(--brand-600); border-top:1px solid var(--surface-border); padding-top:8px; margin-top:4px; }
    .mono { font-family:var(--font-mono, ui-monospace); }
    .net-input { width:100px; text-align:right; font:700 16px var(--font-mono, ui-monospace); color:var(--brand-600);
      background:transparent; border:1px solid var(--surface-border); border-radius:6px; padding:2px 6px; }
    .net-input:focus { outline:0; border-color:var(--brand-500); }
  `]
})
export class ChargeSummary {
  readonly charges = input.required<ChargeSummaryData>();
  /** When true, Net Amount becomes a typeable override — display only, never sent to the
   *  server; the caller decides whether/how to use the edited value. */
  readonly editable = input<boolean>(false);
  readonly netAmountChange = output<number>();
  /** Emits the typed Other Charges amount — unlike {@link netAmountChange}, this one IS
   *  sent to the server (see `ShipmentCreate.otherCharges` form control). */
  readonly otherChargesChange = output<number>();

  protected onNetAmountInput(e: Event): void {
    const v = Number((e.target as HTMLInputElement).value);
    if (!Number.isNaN(v)) this.netAmountChange.emit(v);
  }

  protected onOtherChargesInput(e: Event): void {
    const v = Number((e.target as HTMLInputElement).value);
    if (!Number.isNaN(v)) this.otherChargesChange.emit(v);
  }
}
