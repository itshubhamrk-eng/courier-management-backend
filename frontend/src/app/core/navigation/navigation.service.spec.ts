import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { PermissionService } from '../auth/permission.service';
import { PartnerContextService } from '../auth/partner-context.service';
import { MenuLockService } from './menu-lock.service';
import { MenuGrantService } from './menu-grant.service';
import { NavigationService } from './navigation.service';
import { NavNode } from './navigation.model';

const TREE: NavNode[] = [
  { id: 'dashboard', title: 'Dashboard', icon: 'dashboard', route: '/dashboard', order: 1 },
  {
    id: 'support', title: 'Support', icon: 'support_agent', order: 2,
    children: [
      { id: 'tickets', title: 'Tickets', icon: 't', route: '/support/tickets', roles: ['COMPANY_ADMIN'] },
      { id: 'logs', title: 'Logs', icon: 'l', route: '/communication/logs', roles: ['COMPANY_ADMIN'] }
    ]
  }
];

interface Setup {
  customOnly: boolean;
  allowed?: string[];
  denied?: string[];
  loaded?: boolean;
  canAccess?: (id: string) => boolean;
}

function leaves(nav: NavigationService): string[] {
  const out: string[] = [];
  const walk = (nodes: NavNode[]) => nodes.forEach((n) => (n.children?.length ? walk(n.children) : out.push(n.id)));
  walk(nav.menu());
  return out;
}

function build(s: Setup): NavigationService {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: PermissionService, useValue: {
        isCustomOnly: () => s.customOnly,
        canAccess: (req: { roles?: string[] }) => (s.canAccess ? s.canAccess(String(req.roles)) : true)
      } },
      { provide: PartnerContextService, useValue: { isPartner: () => false } },
      { provide: MenuLockService, useValue: { locked: () => new Set<string>() } },
      { provide: MenuGrantService, useValue: {
        denied: () => new Set(s.denied ?? []),
        allowed: () => new Set(s.allowed ?? []),
        loaded: signal(s.loaded ?? true)
      } }
    ]
  });
  const nav = TestBed.inject(NavigationService);
  nav.setMenuFromApi(flat(TREE));
  return nav;
}

/** setMenuFromApi takes a flat parentId-linked list. */
function flat(tree: NavNode[]): NavNode[] {
  const out: NavNode[] = [];
  const walk = (nodes: NavNode[], parentId?: string) => nodes.forEach((n) => {
    const { children, ...rest } = n;
    out.push({ ...rest, parentId });
    if (children) walk(children, n.id);
  });
  walk(tree);
  return out;
}

describe('NavigationService role-menu visibility', () => {
  it('shows a custom-role user only the allowed leaves', () => {
    expect(leaves(build({ customOnly: true, allowed: ['tickets'] }))).toEqual(['dashboard', 'tickets']);
  });

  it('shows a custom-role user with no allowed menus only the public nodes', () => {
    expect(leaves(build({ customOnly: true, allowed: [] }))).toEqual(['dashboard']);
  });

  it('falls back to the permission rule when the menus fetch has not succeeded', () => {
    expect(leaves(build({ customOnly: true, allowed: [], loaded: false }))).toEqual(['dashboard', 'tickets', 'logs']);
  });

  it('leaves a built-in-role user unaffected by allowed', () => {
    expect(leaves(build({ customOnly: false, allowed: [], canAccess: () => true }))).toEqual(['dashboard', 'tickets', 'logs']);
  });

  it('keeps a user with any built-in role on the permission rule, ignoring allowed', () => {
    const nav = build({ customOnly: false, allowed: ['logs'], canAccess: () => false });
    expect(leaves(nav)).toEqual(['dashboard']);
  });

  it('hides a menu denied for this user even when their role allows it', () => {
    expect(leaves(build({ customOnly: true, allowed: ['tickets', 'logs'], denied: ['logs'] }))).toEqual(['dashboard', 'tickets']);
  });
});
