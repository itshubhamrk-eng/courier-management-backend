import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style parcel/package glyph — booking, shipments, delivered states. */
@Component({
  selector: 'app-package-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'pkgA' + uid" x1="20" y1="20" x2="100" y2="100" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <ellipse cx="60" cy="100" rx="34" ry="7" class="ill__muted" opacity=".6" />
      <path d="M60 22 L98 40 V80 L60 98 L22 80 V40 Z" [attr.fill]="'url(#pkgA' + uid + ')'" />
      <path d="M60 22 L98 40 L60 58 L22 40 Z" class="ill__base-soft" opacity=".85" />
      <path d="M60 58 V98 L22 80 V40 Z" class="ill__base-deep" opacity=".35" />
      <path d="M60 58 V98 M60 58 L98 40 M60 58 L22 40" class="ill__line" stroke="var(--surface)" stroke-width="3" opacity=".8" />
      <rect x="50" y="30" width="20" height="10" rx="4" class="ill__surface" opacity=".9" transform="rotate(-3 60 35)" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class PackageIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
