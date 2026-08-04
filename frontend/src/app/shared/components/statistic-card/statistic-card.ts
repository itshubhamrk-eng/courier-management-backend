import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'info';

/** KPI tile for the dashboard: icon, label, big number, optional delta. */
@Component({
  selector: 'app-statistic-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <div class="app-card stat">
      <div class="stat__icon" [attr.data-tone]="tone()"><mat-icon>{{ icon() }}</mat-icon></div>
      <div class="stat__body">
        <p class="text-caption">{{ label() }}</p>
        @if (loading()) {
          <div class="stat__skeleton"></div>
        } @else {
          <p class="stat__value num-tabular">{{ prefix() }}{{ value() }}</p>
        }
        @if (delta() !== null) {
          <p class="stat__delta" [class.down]="(delta() ?? 0) < 0">
            <mat-icon>{{ (delta() ?? 0) < 0 ? 'trending_down' : 'trending_up' }}</mat-icon>
            {{ deltaLabel() }}
          </p>
        }
      </div>
    </div>
  `,
  styles: [`
    .stat { display:flex; align-items:center; gap:16px; padding:18px 20px; }
    .stat__icon { display:grid; place-items:center; width:48px; height:48px; border-radius:12px; }
    .stat__icon mat-icon { font-size:24px; }
    .stat__icon[data-tone="brand"]   { background:var(--brand-50);  color:var(--brand-600); }
    .stat__icon[data-tone="success"] { background:var(--success-bg); color:var(--success); }
    .stat__icon[data-tone="warning"] { background:var(--warning-bg); color:var(--warning); }
    .stat__icon[data-tone="danger"]  { background:var(--danger-bg);  color:var(--danger); }
    .stat__icon[data-tone="info"]    { background:var(--info-bg);    color:var(--info); }
    .stat__value { font:700 24px/1.1 var(--font-sans); margin:2px 0 0; letter-spacing:-.02em; }
    .stat__skeleton { width:70px; height:24px; margin-top:4px; border-radius:6px;
      background:linear-gradient(90deg,var(--surface-muted),var(--surface-border),var(--surface-muted));
      background-size:200% 100%; animation:sh 1.2s infinite; }
    .stat__delta { display:flex; align-items:center; gap:4px; margin:6px 0 0; font:600 12px var(--font-sans); color:var(--success); }
    .stat__delta.down { color:var(--danger); }
    .stat__delta mat-icon { font-size:16px; width:16px; height:16px; }
    @keyframes sh { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
  `]
})
export class StatisticCard {
  readonly label = input('');
  readonly value = input<string | number>('—');
  readonly prefix = input('');
  readonly icon = input('insights');
  readonly tone = input<Tone>('brand');
  readonly loading = input(false);
  readonly delta = input<number | null>(null);
  readonly deltaLabel = input('');
}
