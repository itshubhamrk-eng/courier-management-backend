import { NavNode } from './navigation.model';

export interface NavTarget { id: string; title: string; route: string; }

/** Flattens a (typically already permission-filtered) nav tree into just its leaves —
 *  the nodes that actually go somewhere. Used by the AI command bar so it only ever
 *  offers to navigate somewhere the signed-in user can already see in their own sidebar. */
export function flattenNavTargets(nodes: NavNode[]): NavTarget[] {
  const out: NavTarget[] = [];
  for (const node of nodes) {
    if (node.route) out.push({ id: node.id, title: node.title, route: node.route });
    if (node.children?.length) out.push(...flattenNavTargets(node.children));
  }
  return out;
}
