import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style wallet glyph — branch wallet, payments, finance pages. */
@Component({
  selector: 'app-wallet-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'walA' + uid" x1="16" y1="34" x2="96" y2="90" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <ellipse cx="58" cy="98" rx="40" ry="6" class="ill__muted" opacity=".6" />
      <rect x="16" y="34" width="80" height="56" rx="14" [attr.fill]="'url(#walA' + uid + ')'" />
      <path d="M16 52 H96" class="ill__line" stroke="var(--surface)" stroke-width="3" opacity=".5" />
      <rect x="66" y="56" width="30" height="22" rx="8" class="ill__surface" opacity=".95" />
      <circle cx="81" cy="67" r="5" class="ill__accent-warning" />
      <rect x="26" y="70" width="26" height="6" rx="3" class="ill__surface" opacity=".5" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class WalletIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
