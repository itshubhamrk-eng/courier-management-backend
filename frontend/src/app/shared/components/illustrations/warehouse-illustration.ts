import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style warehouse/hub glyph — branches, manifest, loading sheet pages. */
@Component({
  selector: 'app-warehouse-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'whA' + uid" x1="16" y1="40" x2="104" y2="94" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <ellipse cx="60" cy="98" rx="46" ry="6" class="ill__muted" opacity=".6" />
      <path d="M60 20 L106 46 V46 L60 34 L14 46 Z" class="ill__base-deep" />
      <rect x="18" y="46" width="84" height="46" rx="8" [attr.fill]="'url(#whA' + uid + ')'" />
      <rect x="44" y="62" width="32" height="30" rx="4" class="ill__surface" opacity=".92" />
      <rect x="24" y="56" width="14" height="14" rx="3" class="ill__base-soft" opacity=".85" />
      <rect x="82" y="56" width="14" height="14" rx="3" class="ill__base-soft" opacity=".85" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class WarehouseIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
