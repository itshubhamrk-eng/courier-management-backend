import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PermissionService } from '@core/auth/permission.service';
import { NotificationService } from '@core/services/notification.service';
import { IncidentDetail } from './incident-detail';
import { SecurityIncidentService } from './security-incident.service';
import { SecurityIncident } from './security-incident.model';
import { INCIDENT_RULE_CODES, ruleDescription, ruleLabel } from './incident-labels';

const incident = (over: Partial<SecurityIncident> = {}): SecurityIncident => ({
  id: 'i-1', companyId: 'c', rule: 'REFRESH_TOKEN_REUSE', severity: 'CRITICAL', status: 'OPEN', autoAction: 'SESSIONS_REVOKED',
  detectedAt: '2026-11-04T10:00:00Z', userId: 'u-1', userDisplayName: 'Asha Rao', userEmail: 'asha@example.com',
  userRoles: ['BRANCH_MANAGER'], branchName: 'Pune', evidence: { ip: '1.2.3.4', hits: { a: 1 }, list: [1, 2] },
  evidenceUnreadable: false, resolvedBy: null, resolvedByName: null, resolvedAt: null, resolutionNote: null, ...over
});

describe('IncidentDetail', () => {
  const svc = { get: vi.fn(), ban: vi.fn(), dismiss: vi.fn(), unban: vi.fn() };
  const dialog = { open: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let canUpdate = true;

  function create(inc: SecurityIncident) {
    svc.get.mockReturnValue(of(inc));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SecurityIncidentService, useValue: svc },
        { provide: MatDialog, useValue: dialog },
        { provide: NotificationService, useValue: notify },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'i-1' } } } },
        { provide: PermissionService, useValue: {
          hasRole: () => false, hasPermission: (c: string) => canUpdate && c === 'SECURITY_INCIDENT_UPDATE' } }
      ]
    });
    const f = TestBed.createComponent(IncidentDetail);
    f.detectChanges();
    return f;
  }
  const buttons = (f: { nativeElement: HTMLElement }) =>
    Array.from(f.nativeElement.querySelectorAll('button')).map((b) => (b.querySelector('span')?.textContent ?? '').trim());

  beforeEach(() => {
    Object.values(svc).forEach((m) => m.mockReset());
    dialog.open.mockReset();
    notify.success.mockReset();
    notify.error.mockReset();
    canUpdate = true;
  });

  it('OPEN with a user shows Dismiss and Ban, not Unban', () => {
    const b = buttons(create(incident({ status: 'OPEN' })));
    expect(b).toContain('Dismiss');
    expect(b).toContain('Ban user');
    expect(b).not.toContain('Unban user');
  });

  it('DISMISSED shows Ban only; BANNED shows Unban only; UNBANNED shows none', () => {
    let b = buttons(create(incident({ status: 'DISMISSED' })));
    expect(b).toContain('Ban user');
    expect(b).not.toContain('Dismiss');
    TestBed.resetTestingModule();
    b = buttons(create(incident({ status: 'BANNED' })));
    expect(b).toContain('Unban user');
    expect(b).not.toContain('Ban user');
    TestBed.resetTestingModule();
    b = buttons(create(incident({ status: 'UNBANNED' })));
    expect(b.filter((t) => ['Ban user', 'Unban user', 'Dismiss'].includes(t))).toEqual([]);
  });

  it('hides Ban when the incident has no user (IP-level)', () => {
    const b = buttons(create(incident({ userId: null, userDisplayName: null, userEmail: null })));
    expect(b).toContain('Dismiss');
    expect(b).not.toContain('Ban user');
  });

  it('hides every action button without SECURITY_INCIDENT_UPDATE but keeps print', () => {
    canUpdate = false;
    const b = buttons(create(incident()));
    expect(b.filter((t) => ['Ban user', 'Unban user', 'Dismiss'].includes(t))).toEqual([]);
    expect(b).toContain('Print / Save as PDF');
  });

  it('clicking Ban confirms, calls the service with the note, then reloads', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of({ note: 'stolen token' }) });
    svc.ban.mockReturnValue(of(incident({ status: 'BANNED' })));
    const f = create(incident());
    const ban = Array.from(f.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((b) => b.textContent?.trim() === 'Ban user')!;
    ban.click();
    expect(dialog.open.mock.calls[0][1].data.title).toBe('Ban this user?');
    expect(svc.ban).toHaveBeenCalledWith('i-1', 'stolen token');
    expect(svc.get).toHaveBeenCalledTimes(2);
    expect(notify.success).toHaveBeenCalledWith('User banned.');
  });

  it('cancelling the dialog does nothing; unban copy says access is restored', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });
    const f = create(incident({ status: 'BANNED' }));
    f.componentInstance.act('unban');
    expect(dialog.open.mock.calls[0][1].data.message).toContain('gets their access back');
    expect(svc.unban).not.toHaveBeenCalled();
    expect(svc.get).toHaveBeenCalledTimes(1);
  });

  it('a rejected transition (422) is announced and the incident reloaded', () => {
    dialog.open.mockReturnValue({ afterClosed: () => of({ note: null }) });
    svc.dismiss.mockReturnValue(throwError(() => ({ status: 422, error: { message: 'Not allowed' } })));
    const f = create(incident());
    f.componentInstance.act('dismiss');
    expect(notify.error).toHaveBeenCalledWith('Not allowed');
    expect(svc.get).toHaveBeenCalledTimes(2);
  });

  it('renders evidence as text rows, nested values as JSON, never as HTML', () => {
    const f = create(incident({ evidence: { ip: '1.2.3.4', hits: { a: 1 }, html: '<img src=x onerror=alert(1)>' } }));
    const el = f.nativeElement as HTMLElement;
    expect(el.textContent).toContain('1.2.3.4');
    expect(el.textContent).toContain('{"a":1}');
    expect(el.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(el.querySelector('img')).toBeNull();
  });

  it('shows a notice when evidence is unreadable', () => {
    const f = create(incident({ evidence: {}, evidenceUnreadable: true }));
    expect(f.nativeElement.querySelector('[role="status"]')?.textContent).toContain('could not be read');
  });

  it('Print / Save as PDF calls window.print', () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const f = create(incident());
    f.componentInstance.print();
    expect(print).toHaveBeenCalled();
    print.mockRestore();
  });
});

describe('incident labels', () => {
  it('every rule has a distinct label and a one-sentence description', () => {
    expect(INCIDENT_RULE_CODES.length).toBe(10);
    expect(new Set(INCIDENT_RULE_CODES.map(ruleLabel)).size).toBe(10);
    for (const r of INCIDENT_RULE_CODES) expect(ruleDescription(r).length).toBeGreaterThan(20);
  });
});
