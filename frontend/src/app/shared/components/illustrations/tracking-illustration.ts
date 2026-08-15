import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style radar/tracking glyph — track shipment, empty tracking states. */
@Component({
  selector: 'app-tracking-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'trkgA' + uid" x1="20" y1="20" x2="100" y2="100" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <circle cx="60" cy="60" r="46" class="ill__muted" opacity=".55" />
      <circle cx="60" cy="60" r="34" fill="none" stroke="var(--brand-300)" stroke-width="2" stroke-dasharray="3 8" opacity=".8" />
      <path d="M60 32 C74 32 84 42 84 55 C84 72 60 92 60 92 C60 92 36 72 36 55 C36 42 46 32 60 32 Z"
            [attr.fill]="'url(#trkgA' + uid + ')'" />
      <circle cx="60" cy="55" r="11" class="ill__surface" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class TrackingIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
