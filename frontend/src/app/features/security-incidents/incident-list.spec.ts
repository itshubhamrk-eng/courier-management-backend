import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { emptyPage } from '@core/models/page.model';
import { IncidentList } from './incident-list';
import { SecurityIncidentService } from './security-incident.service';
import { SecurityIncident } from './security-incident.model';

const incident = (over: Partial<SecurityIncident> = {}): SecurityIncident => ({
  id: 'i-1', companyId: 'c', rule: 'CREDENTIAL_ATTACK', severity: 'HIGH', status: 'OPEN', autoAction: 'ACCOUNT_LOCKED',
  detectedAt: '2026-11-04T10:00:00Z', userId: 'u-1', userDisplayName: 'Asha Rao', userEmail: 'asha@example.com',
  userRoles: [], branchName: null, evidence: {}, evidenceUnreadable: false, resolvedBy: null, resolvedByName: null,
  resolvedAt: null, resolutionNote: null, ...over
});
const pageOf = (rows: SecurityIncident[]) => ({ ...emptyPage<SecurityIncident>(), content: rows, totalElements: rows.length, totalPages: 1 });

describe('IncidentList', () => {
  const list = vi.fn();

  function create() {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SecurityIncidentService, useValue: { list } }]
    });
    const fixture = TestBed.createComponent(IncidentList);
    fixture.detectChanges();
    return fixture;
  }
  const text = (f: { nativeElement: HTMLElement }) => f.nativeElement.textContent ?? '';

  beforeEach(() => list.mockReset());

  it('renders a row per incident with human labels, user and "—" for IP-level incidents', async () => {
    list.mockReturnValue(of(pageOf([incident(), incident({ id: 'i-2', userId: null, userDisplayName: null, userEmail: null, rule: 'FOREIGN_LOGIN_ATTEMPT' })])));
    const f = create();
    await f.whenStable();
    f.detectChanges();
    const rows = f.nativeElement.querySelectorAll('tr.tbl__row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Asha Rao');
    expect(rows[0].textContent).toContain('asha@example.com');
    expect(rows[0].textContent).toContain('Credential attack');
    expect(rows[1].textContent).toContain('Foreign sign-in attempt');
    expect(rows[1].textContent).toContain('—');
  });

  it('filters by status: reloads from page 0 with the chosen status', async () => {
    list.mockReturnValue(of(pageOf([incident()])));
    const f = create();
    expect(list).toHaveBeenLastCalledWith({ page: 0, size: 20 });
    f.detectChanges();
    const select = f.nativeElement.querySelector('#filter-status') as HTMLSelectElement;
    const idx = Array.from(select.options).findIndex((o) => o.value === 'BANNED');
    expect(idx).toBeGreaterThan(0);
    select.selectedIndex = idx;
    select.dispatchEvent(new Event('change'));
    expect(list).toHaveBeenLastCalledWith({ page: 0, size: 20, status: 'BANNED' });
  });

  it('shows an empty state when nothing matches', async () => {
    list.mockReturnValue(of(pageOf([])));
    const f = create();
    f.detectChanges();
    expect(text(f)).toContain('No incidents');
  });

  it('shows an error message when loading fails, and Refresh retries', () => {
    list.mockReturnValueOnce(throwError(() => new Error('boom')));
    const f = create();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Could not load');
    list.mockReturnValue(of(pageOf([incident()])));
    (f.componentInstance as IncidentList).load();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(f.nativeElement.querySelectorAll('tr.tbl__row').length).toBe(1);
  });

  it('opens the report when a row is clicked', () => {
    list.mockReturnValue(of(pageOf([incident()])));
    const f = create();
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    f.detectChanges();
    (f.nativeElement.querySelector('tr.tbl__row') as HTMLElement).click();
    expect(nav).toHaveBeenCalledWith(['/security/incidents', 'i-1']);
  });
});
