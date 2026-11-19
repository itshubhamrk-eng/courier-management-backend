# Role-owned menus (`role_menu`) — design

Date: 2026-11-19. Status: awaiting approval.

## Intent and why

An admin assigns menus to a role (e.g. CRM) and expects every user with that role to see exactly
those menus. Today menus are never stored on a role: the role screen ticks *permission codes* and
the menu is inferred from `menu_items.permission_module`. 102 menu leaves share only 32 modules,
so ticking Tickets also ticks Communication (both `NOTIFICATION`), Follow-ups also ticks
Customers (both `CUSTOMER`), and the 14 reports are all `REPORT`. Custom-role users also lose
role-only nav nodes (Vehicles, Follow-ups, Support, Communication were hidden for CRM user Rohit).

Goal: a role carries its own explicit menu list; users follow it live.

## Goals

- New `role_menu` table: which menu items a role holds, with can_create/read/update/delete flags
  (same shape as `user_menu_grants`).
- Role screen assigns menus per menu item, independent of shared permission modules.
- A user's effective menus = union of `role_menu` across their roles, minus per-user denials.
  Changing a role updates every holder (next token refresh / `/menu-grants/me` fetch). No copy at
  user creation.
- Sidebar and route guards for custom-role users are decided by that effective set.
- Backfill so no existing role or user loses a menu on deploy.

## Non-goals

- Backend API enforcement stays module-permission based (`@PreAuthorize`). A user without the
  Communication menu who holds `NOTIFICATION_*` can still call the Communication API. Splitting
  permission modules per menu is a separate, larger project (rejected as "option 1").
- No per-user *additive* menus. Extra menus come by assigning another role. (A `user_menu_grants`
  row stays deny-only, as today; widening it would turn existing tick-snapshot rows into grants.)
- Built-in roles' nav behaviour (the `roles:` arrays in `navigation.config.ts`) is unchanged.

## Anti-goals

- No user can end up with a menu their role does not hold.
- No existing user loses a menu at deploy.
- No fan-out write to every user when a role changes.

## Constraints

- Flyway: next free is V149 (V148 is latest). Commit migrations in version order; prod crash-looped
  once on out-of-order (see memory prod-deploy-flyway-order).
- JWT is ~7.5 KB for admins (Tomcat/nginx 8 KB limit): do NOT put menu codes in the token. Fetch
  via `/menu-grants/me`.
- A new table needs company_id and the BaseEntity columns used elsewhere (deleted, version, ...).
- Menu identity: `menu_items.code` equals the frontend nav node `id`.

## Approaches considered

1. **Live from role, user rows are overrides only (chosen).** Effective = role_menu union minus
   user denials. No copy, no drift, one write per role change.
2. Copy to `user_menu_grants` at creation and re-sync on every role edit. Matches the first
   wording, but fan-out writes and individual edits get overwritten. Rejected.
3. Live from role with no per-user table. Drops the existing User Permissions menu screen. Rejected.

User picked 1 (called "A") on 2026-11-19.

## Design

**Schema (V149).** `role_menu(id, company_id, role_id, menu_item_id, can_create, can_read,
can_update, can_delete, + base cols)`, unique `(role_id, menu_item_id)`, FK to `company_roles(id)`
(the `id` column, not `company_id`, per the companies dual-id gotcha) and `menu_items(id)`.

**Backfill (same migration).** For each company role and each leaf menu: insert a row when the
role holds the READ permission of the menu's `permission_module` (this reproduces today's
inferred visibility); copy the other three flags from CREATE/UPDATE/DELETE the same way. Group
(parent) rows are not stored; a parent shows when any child does. Covers built-in roles too so the
role screen shows them.

**Backend.**
- `RoleMenuService`: `get(roleId)`, `replace(roleId, items)` (guard: same as role-permission
  assign, `ROLE_UPDATE`).
- `GET /api/v1/roles/{id}/menus`, `PUT /api/v1/roles/{id}/menus`.
- `GET /api/v1/menu-grants/me` returns `{ deniedMenus, allowedMenus }`. `allowedMenus` = codes of
  menus with `can_read` in any of the caller's active roles' `role_menu`, minus `deniedMenus`.
- On `PUT`, also keep API access working: for each ticked menu, the role gets the module
  permissions matching its ticked actions (existing `RolePermissionService.assign`). Un-ticking a
  menu removes a module permission only if no other ticked menu of that role uses the same module
  action. *(My call; the alternative is menu-only and APIs 403.)*
- COMPANY_ADMIN backfill for new menu items follows the existing "new module needs a backfill
  migration" rule.

**Frontend.**
- `MenuGrantService` also stores `allowed`.
- `NavigationService.canSee`: built-in-role users unchanged. A user whose roles are all custom is
  decided by `allowed.has(node.id)`; mixed users: either passes.
- Revert the 11 `permission:` additions made to `navigation.config.ts` on 2026-11-19 (Vehicles,
  Follow-ups, Support, Communication). They are superseded by `allowed` and re-couple menus.
- Role edit screen: a menu tree (reuse the User Permissions menu tree component) with per-menu
  CRUD checkboxes, saving to `PUT /roles/{id}/menus`.
- Route guard `role.guard` already admits custom-only users under a visible leaf; no change.

**Prod CRM role.** Backfill gives CRM its current menus. Afterwards admin untick Communication
(or whatever CRM should not have) on the role screen.

## Left to the builder

- Exact DTO names, component reuse, and test layout.
- Whether `allowedMenus` is cached per request.
- Audit-log event name for role-menu changes (follow `USER_PERMISSIONS_UPDATED`).

## Verification (before claiming done)

On :8082/:4300 with a fresh CRM role and user: tick Tickets only, confirm Communication is not
shown; tick Communication on the role, confirm an existing user gains it without re-creation;
untick for one user via the user screen, confirm only that user loses it. Check backfill on a
clone of the dev DB before prod.
