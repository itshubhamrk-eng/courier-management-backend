import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ILLUSTRATION_STYLES, nextIllustrationUid } from './illustration-base';

/** Clay-style route/path glyph — routes, distance, freight factor pages. */
@Component({
  selector: 'app-route-illustration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ill" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 120 120" fill="none" aria-hidden="true">
      <path d="M22 86 C 30 60, 46 66, 52 48 S 78 22, 96 30" class="ill__line" stroke="var(--brand-500)" stroke-width="6" stroke-dasharray="2 12" />
      <circle cx="22" cy="86" r="9" class="ill__base-deep" />
      <circle cx="22" cy="86" r="4" class="ill__surface" />
      <circle cx="96" cy="30" r="9" class="ill__base-deep" />
      <circle cx="96" cy="30" r="4" class="ill__surface" />
      <circle cx="52" cy="48" r="5" class="ill__base-soft" />
    </svg>
  `,
  styles: [ILLUSTRATION_STYLES]
})
export class RouteIllustration {
  readonly size = input(120);
  protected readonly uid = nextIllustrationUid();
}
