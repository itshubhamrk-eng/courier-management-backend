/**
 * A single navigation node. The tree may be authored nested (via `children`) or arrive flat
 * from an API and be assembled with `parentId` + `order` — {@link NavigationService} supports
 * both. `visible` and `expanded` are runtime-computed and never authored.
 */
export interface NavNode {
  /** Stable identifier; also the key for expand/collapse memory. */
  id: string;
  title: string;
  icon?: string;
  /** Absolute route for a leaf. A group (has children) has no route. */
  route?: string;
  /** For flat/API trees: the parent's id (`null`/absent = top level). */
  parentId?: string | null;
  /** Sort order within its parent; lower first. */
  order?: number;

  /**
   * Permission code required to see this node (e.g. `USER_VIEW`). Visibility is decided by
   * the PermissionService against the codes returned after login — no role literals here.
   */
  permission?: string;

  /**
   * Interim authority bridge: the roles that may see this node until the backend authorises
   * on permission codes (today the JWT carries roles, `permissions` is empty). This is
   * *declarative data*, consumed by PermissionService.canAccess — not a hardcoded role check
   * in a component. Drop it once permission codes ship.
   */
  roles?: string[];

  children?: NavNode[];
  badge?: string;

  /** Runtime: passed the permission filter. */
  visible?: boolean;
  /** Runtime: group is open. */
  expanded?: boolean;
}
