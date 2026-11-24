import { NavNode } from './navigation.model';
import { NAVIGATION } from './navigation.config';

export interface NavTrail { group?: NavNode; leaf?: NavNode; }

function walk(nodes: NavNode[], path: string, group: NavNode | undefined, best: { len: number; trail: NavTrail }): void {
  for (const n of nodes) {
    if (n.route && (path === n.route || path.startsWith(n.route + '/')) && n.route.length > best.len) {
      best.len = n.route.length;
      best.trail = { group, leaf: n };
    }
    if (n.children?.length) walk(n.children, path, group ?? n, best);
  }
}

/** The sidebar group (top-level section) and leaf whose route best matches `url`
 *  (longest prefix, so "/branches/new" resolves to Branches). Empty when nothing matches. */
export function navTrailForUrl(url: string): NavTrail {
  const path = url.split(/[?#]/)[0];
  const best = { len: -1, trail: {} as NavTrail };
  walk(NAVIGATION, path, undefined, best);
  return best.trail;
}
