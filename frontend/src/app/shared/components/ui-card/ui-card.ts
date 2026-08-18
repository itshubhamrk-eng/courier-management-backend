import { ChangeDetectionStrategy, Component, input } from '@angular/core';

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'none';

/** Rounded surface card with optional title/subtitle and an actions slot. Optional
 *  `tone` paints the same top-accent-bar + wash treatment `app-statistic-card` uses,
 *  for cards that should read as color-coded at a glance rather than uniformly gray. */
@Component({
  selector: 'app-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="app-card ac" [attr.data-tone]="tone() === 'none' ? null : tone()">
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
    .ac { overflow:hidden; position:relative; }
    .ac__head { display:flex; align-items:flex-start; justify-content:space-between;
      padding:20px 24px 16px; border-bottom:1px solid var(--surface-border); }
    .ac__body { padding:24px; }
    .ac__actions { display:flex; gap:8px; }

    .ac[data-tone] { border-top:3px solid transparent; }
    .ac[data-tone]::before { content:''; position:absolute; inset:0; opacity:.45; pointer-events:none; z-index:0; }
    .ac[data-tone] .ac__head, .ac[data-tone] .ac__body { position:relative; z-index:1; }
    .ac[data-tone="brand"]   { border-top-color:var(--brand-500); }
    .ac[data-tone="success"] { border-top-color:var(--success); }
    .ac[data-tone="warning"] { border-top-color:var(--warning); }
    .ac[data-tone="danger"]  { border-top-color:var(--danger); }
    .ac[data-tone="info"]    { border-top-color:var(--info); }
    .ac[data-tone="brand"]::before   { background:linear-gradient(135deg, var(--brand-50), transparent 55%); }
    .ac[data-tone="success"]::before { background:linear-gradient(135deg, var(--success-bg), transparent 55%); }
    .ac[data-tone="warning"]::before { background:linear-gradient(135deg, var(--warning-bg), transparent 55%); }
    .ac[data-tone="danger"]::before  { background:linear-gradient(135deg, var(--danger-bg), transparent 55%); }
    .ac[data-tone="info"]::before    { background:linear-gradient(135deg, var(--info-bg), transparent 55%); }
  `]
})
export class UiCard {
  readonly title = input<string>('');
  readonly subtitle = input<string>('');
  readonly tone = input<Tone>('none');
}
