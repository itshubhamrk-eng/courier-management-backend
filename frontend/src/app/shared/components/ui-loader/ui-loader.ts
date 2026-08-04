import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

/** Centred spinner for card/page loading states, plus an optional caption. */
@Component({
  selector: 'app-loader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="loader" [style.min-height.px]="minHeight()">
      <mat-progress-spinner [diameter]="size()" mode="indeterminate" />
      @if (caption()) { <p class="text-caption">{{ caption() }}</p> }
    </div>
  `,
  styles: [`.loader { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; }`]
})
export class UiLoader {
  readonly size = input(36);
  readonly minHeight = input(160);
  readonly caption = input<string>('');
}
