import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style location pin glyph — addresses, branches, tracking destination. */
@Component({
  selector: 'app-pin-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'pinA' + uid" x1="30" y1="14" x2="90" y2="90" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <ellipse cx="60" cy="102" rx="22" ry="6" class="ill__muted" opacity=".6" />
      <path d="M60 14 C82 14 98 30 98 52 C98 78 60 106 60 106 C60 106 22 78 22 52 C22 30 38 14 60 14 Z"
            [attr.fill]="'url(#pinA' + uid + ')'" />
      <circle cx="60" cy="52" r="20" class="ill__surface" />
      <circle cx="60" cy="52" r="11" class="ill__base-deep" opacity=".85" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class PinIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
