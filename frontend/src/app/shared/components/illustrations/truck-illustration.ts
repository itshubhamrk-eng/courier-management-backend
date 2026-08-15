import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style delivery truck/van glyph — auth hero, dispatch/movement pages. */
@Component({
  selector: 'app-truck-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'trkA' + uid" x1="10" y1="40" x2="90" y2="80" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <ellipse cx="60" cy="98" rx="46" ry="6" class="ill__muted" opacity=".6" />
      <rect x="12" y="42" width="56" height="34" rx="10" [attr.fill]="'url(#trkA' + uid + ')'" />
      <path d="M68 52 H92 L104 66 V76 H68 Z" class="ill__base-soft" />
      <rect x="76" y="58" width="18" height="12" rx="3" class="ill__surface" opacity=".9" />
      <rect x="20" y="50" width="28" height="10" rx="4" class="ill__surface" opacity=".8" />
      <circle cx="34" cy="80" r="10" class="ill__base-deep" />
      <circle cx="34" cy="80" r="4" class="ill__surface" />
      <circle cx="86" cy="80" r="10" class="ill__base-deep" />
      <circle cx="86" cy="80" r="4" class="ill__surface" />
      <path d="M8 76 H108" class="ill__line" stroke="var(--brand-700)" stroke-width="2" opacity=".25" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class TruckIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
