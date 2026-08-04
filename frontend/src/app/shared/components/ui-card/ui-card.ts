import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Rounded surface card with optional title/subtitle and an actions slot. */
@Component({
  selector: 'app-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="app-card ac">
      @if (title() || subtitle()) {
        <header class="ac__head">
          <div>
            <h3 class="text-h3">{{ title() }}</h3>
            @if (subtitle()) { <p class="text-caption">{{ subtitle() }}</p> }
          </div>
          <div class="ac__actions"><ng-content select="[card-actions]" /></div>
        </header>
      }
      <div class="ac__body"><ng-content /></div>
    </section>
  `,
  styles: [`
    .ac { overflow:hidden; }
    .ac__head { display:flex; align-items:flex-start; justify-content:space-between;
      padding:16px 20px; border-bottom:1px solid var(--surface-border); }
    .ac__body { padding:20px; }
    .ac__actions { display:flex; gap:8px; }
  `]
})
export class UiCard {
  readonly title = input<string>('');
  readonly subtitle = input<string>('');
}
