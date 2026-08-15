let uidCounter = 0;

/** Unique id suffix so two instances of the same illustration on one page don't collide
 *  on their SVG `<defs>` gradient/filter ids. */
export function nextIllustrationUid(): string {
  return `ill-${++uidCounter}`;
}

/** Shared CSS for every illustration component's inline SVG — one clay palette (brand
 *  gradient fills + a soft highlight + a matching drop-shadow) so the whole set reads as
 *  one family regardless of motif. Each component's `styles` array includes this string. */
export const ILLUSTRATION_STYLES = `
  .ill { display:block; filter:drop-shadow(0 10px 18px rgba(99,102,241,.22)); }
  .ill__base { fill:var(--brand-500); }
  .ill__base-deep { fill:var(--brand-700); }
  .ill__base-soft { fill:var(--brand-300); }
  .ill__surface { fill:var(--surface); }
  .ill__muted { fill:var(--surface-muted); }
  .ill__line { stroke:var(--brand-700); stroke-linecap:round; stroke-linejoin:round; fill:none; }
  .ill__accent-success { fill:var(--success); }
  .ill__accent-warning { fill:var(--warning); }
  .ill__accent-info { fill:var(--info); }
`;
