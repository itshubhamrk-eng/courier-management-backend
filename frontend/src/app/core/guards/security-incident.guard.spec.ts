import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { PermissionService } from '../auth/permission.service';
import { securityIncidentGuard } from './security-incident.guard';

describe('securityIncidentGuard', () => {
  function run(opts: { authed?: boolean; roles?: string[]; perms?: string[] }) {
    const roles = opts.roles ?? [];
    const perms = opts.perms ?? [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => opts.authed ?? true } },
        { provide: PermissionService, useValue: {
          hasRole: (r: string) => roles.includes(r),
          hasAnyPermission: (c: string[]) => c.some((x) => perms.includes(x)) } }
      ]
    });
    const route = { data: { permissions: ['SECURITY_INCIDENT_READ'] } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => securityIncidentGuard(route, { url: '/security/incidents' } as RouterStateSnapshot));
  }
  const target = (r: unknown) => TestBed.inject(Router).serializeUrl(r as UrlTree);

  it('admits PLATFORM_ADMIN on the role alone', () => {
    expect(run({ roles: ['PLATFORM_ADMIN'] })).toBe(true);
  });

  it('admits COMPANY_ADMIN holding the read code', () => {
    expect(run({ roles: ['COMPANY_ADMIN'], perms: ['SECURITY_INCIDENT_READ'] })).toBe(true);
  });

  it('refuses COMPANY_ADMIN without the code', () => {
    expect(target(run({ roles: ['COMPANY_ADMIN'] }))).toBe('/unauthorized');
  });

  it('refuses another role even if it holds the code', () => {
    expect(target(run({ roles: ['BRANCH_MANAGER'], perms: ['SECURITY_INCIDENT_READ'] }))).toBe('/unauthorized');
  });

  it('sends a signed-out user to login with the return url', () => {
    expect(target(run({ authed: false }))).toBe('/login?returnUrl=%2Fsecurity%2Fincidents');
  });
});
