import {
  ActionState, CrudAction, MenuItemNode, RoleMenuEntry, applyMenuToggle, buildRoleMenuTreeByMenu, toRoleMenuEntries
} from './menu-permission.model';

const none = (): ActionState => ({ CREATE: false, READ: false, UPDATE: false, DELETE: false });

const TREE: MenuItemNode[] = [{
  id: 'g', parentId: null, code: 'support', title: 'Support', icon: null, route: null,
  permissionModule: null, displayOrder: 1, active: true,
  children: [
    { id: 'm1', parentId: 'g', code: 'support-tickets', title: 'Tickets', icon: null, route: '/t',
      permissionModule: 'NOTIFICATION', displayOrder: 1, active: true, children: [] },
    { id: 'm2', parentId: 'g', code: 'communication-logs', title: 'Logs', icon: null, route: '/l',
      permissionModule: 'NOTIFICATION', displayOrder: 2, active: true, children: [] }
  ]
}];
const ACTIONS = new Map<string, CrudAction[]>([['NOTIFICATION', ['CREATE', 'READ', 'UPDATE', 'DELETE']]]);

describe('role menu helpers', () => {
  it('ticking Tickets leaves Logs (same module) unticked', () => {
    const saved = new Map<string, ActionState>([['m1', { ...none(), READ: true }]]);
    const nodes = buildRoleMenuTreeByMenu(TREE, ACTIONS, saved);
    const [tickets, logs] = nodes[0].children;
    expect(tickets.effective.READ).toBe(true);
    expect(logs.effective.READ).toBe(false);
  });

  it('offers only the actions the module has', () => {
    const only = new Map<string, CrudAction[]>([['NOTIFICATION', ['READ']]]);
    const nodes = buildRoleMenuTreeByMenu(TREE, only, new Map());
    expect(Object.keys(nodes[0].children[0].roleDefault)).toEqual(['READ']);
  });

  it('ticking a write action also ticks READ so the menu shows', () => {
    const next = applyMenuToggle(none(), 'CREATE', true, ['CREATE', 'READ']);
    expect(next).toEqual({ ...none(), CREATE: true, READ: true });
  });

  it('unticking READ clears every other action', () => {
    const full: ActionState = { CREATE: true, READ: true, UPDATE: true, DELETE: true };
    expect(applyMenuToggle(full, 'READ', false, ['CREATE', 'READ', 'UPDATE', 'DELETE'])).toEqual(none());
  });

  it('toRoleMenuEntries sends only ticked menus', () => {
    const state = new Map<string, ActionState>([['m1', { ...none(), READ: true }], ['m2', none()]]);
    const entries: RoleMenuEntry[] = toRoleMenuEntries(state);
    expect(entries).toEqual([{ menuItemId: 'm1', canCreate: false, canRead: true, canUpdate: false, canDelete: false }]);
  });
});
