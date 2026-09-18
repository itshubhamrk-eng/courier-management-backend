/**
 * Menu + Permission Management models — mirror the backend `menu_items` hierarchy and
 * the per-user permission matrix one-to-one (see MEMORY/modules/permission.md). No mock
 * shapes: every field here is returned by, or accepted by, an actual endpoint.
 */

/** The four actions a menu leaf's checkboxes offer. Not every module has all four —
 *  see {@link MenuPermissionNode.roleDefault} (a missing key means "not offered", not
 *  "denied"). */
export type CrudAction = 'CREATE' | 'READ' | 'UPDATE' | 'DELETE';
export const CRUD_ACTIONS: CrudAction[] = ['CREATE', 'READ', 'UPDATE', 'DELETE'];

/** The editor's own live state for one leaf — always all four keys, unlike the
 *  server's `roleDefault`/`effective` maps which omit an action the module lacks. */
export type ActionState = Record<CrudAction, boolean>;

/** One node of the plain menu hierarchy — `GET /menu-items/tree`. */
export interface MenuItemNode {
  id: string;
  parentId: string | null;
  code: string;
  title: string;
  icon: string | null;
  route: string | null;
  /** Null for a grouping node (not itself grantable). */
  permissionModule: string | null;
  displayOrder: number;
  active: boolean;
  children: MenuItemNode[];
}

/**
 * One node of a user's annotated menu tree — `GET /users/{id}/menu-permissions`.
 * `roleDefault`/`effective`/`overridden` are keyed by {@link CrudAction}; a key absent
 * from `roleDefault` means the module has no such action at all (checkbox not offered,
 * not merely unchecked). `overridden[action] === true` means the admin explicitly set
 * this user's value away from the role default.
 */
export interface MenuPermissionNode {
  id: string;
  code: string;
  title: string;
  icon: string | null;
  route: string | null;
  module: string | null;
  displayOrder: number;
  roleDefault: Partial<Record<CrudAction, boolean>>;
  effective: Partial<Record<CrudAction, boolean>>;
  overridden: Partial<Record<CrudAction, boolean>>;
  children: MenuPermissionNode[];
}

/** Body of `PUT /users/{id}/menu-permissions`. */
export interface UserMenuPermissionUpdateRequest {
  items: UserMenuPermissionItem[];
}

export interface UserMenuPermissionItem {
  menuItemId: string;
  create: boolean;
  read: boolean;
  update: boolean;
  delete: boolean;
}

/** Result of saving a user's menu permissions — mirrors backend
 *  `UserPermissionUpdateResponse`. */
export interface UserPermissionUpdateResult {
  granted: string[];
  revoked: string[];
  effectivePermissions: string[];
}

/** Read a node's value for one action, `false` when the module does not offer it. */
export function actionValue(map: Partial<Record<CrudAction, boolean>>, action: CrudAction): boolean {
  return map[action] === true;
}

/** Whether this node offers a given action's checkbox at all. */
export function hasAction(map: Partial<Record<CrudAction, boolean>>, action: CrudAction): boolean {
  return action in map;
}

/** Every grantable leaf (a node naming a module) under this node, depth-first —
 *  a group node contributes nothing of its own, only its descendants. */
export function flattenLeaves(node: MenuPermissionNode): MenuPermissionNode[] {
  const own = node.module ? [node] : [];
  return own.concat(node.children.flatMap(flattenLeaves));
}
