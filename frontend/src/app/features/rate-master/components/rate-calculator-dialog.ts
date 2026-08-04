import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { RateCalculatorForm } from './rate-calculator-form';

/** Quick "what would this cost" lookup, launched from the rate list without leaving it. */
@Component({
  selector: 'app-rate-calculator-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, UiButton, RateCalculatorForm],
  template: `
    <div class="rcd">
      <h2 class="text-h2">Calculate Rate</h2>
      <app-rate-calculator-form />
      <div class="rcd__actions">
        <app-button variant="stroked" (pressed)="ref.close()">Close</app-button>
      </div>
    </div>
  `,
  styles: [`
    .rcd { padding:24px; width:640px; max-width:92vw; max-height:85vh; overflow-y:auto; display:flex; flex-direction:column; gap:16px; }
    .rcd__actions { display:flex; justify-content:flex-end; }
  `]
})
export class RateCalculatorDialog {
  readonly ref = inject(MatDialogRef<RateCalculatorDialog>);
}
