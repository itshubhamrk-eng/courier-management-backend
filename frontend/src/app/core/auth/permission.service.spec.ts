import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { PermissionService } from './permission.service';

describe('PermissionService.canListUsers', () => {
  function svc(roles: string[], permissions: string[]): PermissionService {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { roles: () => roles, permissions: () => permissions } }]
    });
    return TestBed.inject(PermissionService);
  }

  it('is true for a built-in role, which the backend already admits on role', () => {
    expect(svc(['BRANCH_MANAGER'], []).canListUsers()).toBe(true);
  });

  it('is false for a custom-role user without USER_READ, so pages skip the staff lookup', () => {
    expect(svc(['CRM'], ['NOTIFICATION_READ']).canListUsers()).toBe(false);
  });

  it('is true for a custom-role user who holds USER_READ', () => {
    expect(svc(['CRM'], ['USER_READ']).canListUsers()).toBe(true);
  });
});
