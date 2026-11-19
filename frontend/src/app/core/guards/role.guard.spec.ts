import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, isObservable, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { MenuGrantService } from '../navigation/menu-grant.service';
import { NavigationService } from '../navigation/navigation.service';
import { NavNode } from '../navigation/navigation.model';
import { roleGuard } from './role.guard';

const ROUTE = '/support/tickets';
const MENU_WITH_ROUTE: NavNode[] = [{ id: 'tickets', title: 'Tickets', icon: 't', route: ROUTE }];

describe('roleGuard for custom-role users', () => {
  function setup(opts: { roles: string[]; settled: boolean; menu: () => NavNode[] }) {
    const settled = signal(opts.settled);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: {
          roles: () => opts.roles,
          hasAnyRole: (req: string[]) => req.length === 0 || req.some((r) => opts.roles.includes(r)) } },
        { provide: NavigationService, useValue: { menu: opts.menu } },
        { provide: MenuGrantService, useValue: { settled } }
      ]
    });
    const route = { data: { roles: ['COMPANY_ADMIN'] } } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => roleGuard(route, { url: ROUTE } as RouterStateSnapshot));
    return { result, settled };
  }
  const target = (r: unknown) => TestBed.inject(Router).serializeUrl(r as UrlTree);

  it('waits for the menus fetch instead of bouncing a cold page load to /unauthorized', async () => {
    let menu: NavNode[] = [];
    const { result, settled } = setup({ roles: ['CRM'], settled: false, menu: () => menu });
    expect(isObservable(result)).toBe(true);
    menu = MENU_WITH_ROUTE;
    settled.set(true);
    expect(await firstValueFrom(result as Observable<boolean | UrlTree>)).toBe(true);
  });

  it('refuses a custom-role user once the menus are loaded and the route is not among them', async () => {
    const { result } = setup({ roles: ['CRM'], settled: true, menu: () => [] });
    const resolved = isObservable(result) ? await firstValueFrom(result) : result;
    expect(target(resolved)).toBe('/unauthorized');
  });

  it('admits a built-in role synchronously', () => {
    const { result } = setup({ roles: ['COMPANY_ADMIN'], settled: false, menu: () => [] });
    expect(result).toBe(true);
  });
});
