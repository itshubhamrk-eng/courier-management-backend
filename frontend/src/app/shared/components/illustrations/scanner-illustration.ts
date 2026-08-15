import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style barcode-scanner glyph — in-scan/out-scan, loading sheet pages. */
@Component({
  selector: 'app-scanner-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <defs>
        <linearGradient [attr.id]="'scnA' + uid" x1="24" y1="30" x2="90" y2="90" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="var(--brand-400)" />
          <stop offset="1" stop-color="var(--brand-600)" />
        </linearGradient>
      </defs>
      <ellipse cx="58" cy="100" rx="30" ry="6" class="ill__muted" opacity=".6" />
      <rect x="30" y="26" width="40" height="66" rx="12" [attr.fill]="'url(#scnA' + uid + ')'" />
      <rect x="38" y="36" width="24" height="30" rx="4" class="ill__surface" opacity=".92" />
      <rect x="42" y="72" width="16" height="4" rx="2" class="ill__surface" opacity=".7" />
      <path d="M38 51 H62" class="ill__line" stroke="var(--danger)" stroke-width="2.5" opacity=".9" />
      <rect x="72" y="46" width="20" height="8" rx="4" class="ill__base-soft" transform="rotate(-18 72 46)" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class ScannerIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
