import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NotificationService } from '@core/services/notification.service';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { LegacyPreviewResult, LegacyPreviewRow } from '@core/models/legacy-import.model';
import { LegacyImportService } from './legacy-import.service';
import { LegacyImportPage } from './legacy-import-page';

const row = (rowNo: number, outcome: LegacyPreviewRow['outcome'], differences: string[] = [], origin = 'BHOSARI'): LegacyPreviewRow =>
  ({ rowNo, consignmentNo: `C${rowNo}`, outcome, reason: outcome === 'INVALID' ? 'bad' : null, differences, origin });

const preview = (overrides: Partial<LegacyPreviewResult> = {}): LegacyPreviewResult => ({
  fileHash: 'h', total: 4, newCount: 2, existingCount: 1, differsCount: 1, invalidCount: 1,
  origins: [{ originKey: 'BHOSARI', newRows: 2, mappedBranchId: null }],
  rows: [row(1, 'NEW'), row(2, 'NEW'), row(3, 'EXISTING', ['weight: ours 9 / file 3']), row(4, 'INVALID')],
  ...overrides
});

describe('LegacyImportPage', () => {
  let fixture: ComponentFixture<LegacyImportPage>;
  let page: LegacyImportPage;
  const service = { preview: vi.fn(), commit: vi.fn(), mapOrigin: vi.fn(), batches: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
    service.mapOrigin.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [LegacyImportPage],
      providers: [
        provideNoopAnimations(),
        { provide: LegacyImportService, useValue: service },
        { provide: NotificationService, useValue: notify },
        { provide: BreadcrumbService, useValue: { set: vi.fn() } },
        { provide: MasterDataService, useValue: { options: () => of([{ value: 'b-1', label: 'Pune' }]) } }
      ]
    });
    fixture = TestBed.createComponent(LegacyImportPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  const load = (p: LegacyPreviewResult) => {
    service.preview.mockReturnValue(of(p));
    page.file.set(new File(['x'], 'c.xls'));
    page.preview();
    fixture.detectChanges();
  };

  it('keeps commit disabled until every origin is mapped', () => {
    load(preview());
    expect(page.canCommit()).toBe(false);
    expect(page.unmapped()).toBe(1);

    page.controlFor('BHOSARI').setValue('b-1');

    expect(service.mapOrigin).toHaveBeenCalledWith('BHOSARI', 'b-1');
    expect(page.unmapped()).toBe(0);
    expect(page.canCommit()).toBe(true);
  });

  it('starts enabled when the server already remembers every origin', () => {
    load(preview({ origins: [{ originKey: 'BHOSARI', newRows: 2, mappedBranchId: 'b-1' }] }));
    expect(page.canCommit()).toBe(true);
  });

  it('never enables commit when there is nothing new', () => {
    load(preview({ newCount: 0, origins: [] }));
    expect(page.canCommit()).toBe(false);
  });

  it('shows the server message and stays on the review step when commit is refused', () => {
    load(preview({ origins: [{ originKey: 'BHOSARI', newRows: 2, mappedBranchId: 'b-1' }] }));
    service.commit.mockReturnValue(throwError(() => ({ error: { message: 'This company has no PAID payment mode' } })));

    page.commit();

    expect(notify.error).toHaveBeenCalledWith('This company has no PAID payment mode');
    expect(page.stage()).toBe('ready');
  });

  it('un-maps an origin again when saving the mapping fails', () => {
    load(preview());
    service.mapOrigin.mockReturnValue(throwError(() => ({ error: { message: 'Branch HB-X is not an active booking branch.' } })));

    page.controlFor('BHOSARI').setValue('b-1');

    expect(page.unmapped()).toBe(1);
    expect(notify.error).toHaveBeenCalled();
  });

  it('filters rows by outcome and by "differs"', () => {
    load(preview());
    page.filter.set('NEW');
    expect(page.filteredRows().map((r) => r.rowNo)).toEqual([1, 2]);
    page.filter.set('DIFFERS');
    expect(page.filteredRows().map((r) => r.rowNo)).toEqual([3]);
    page.filter.set('INVALID');
    expect(page.filteredRows().map((r) => r.rowNo)).toEqual([4]);
  });

  it('shows the commit outcomes instead of the preview guesses once done', () => {
    load(preview({ origins: [{ originKey: 'BHOSARI', newRows: 2, mappedBranchId: 'b-1' }] }));
    service.commit.mockReturnValue(of({
      batchId: 'bt', created: 1, skippedExisting: 1, invalid: 2,
      rows: [row(1, 'NEW'), row(2, 'INVALID'), row(3, 'EXISTING'), row(4, 'INVALID')]
    }));

    page.commit();

    expect(page.stage()).toBe('done');
    expect(page.rows().filter((r) => r.outcome === 'INVALID')).toHaveLength(2);
    expect(notify.success).toHaveBeenCalled();
  });
});
