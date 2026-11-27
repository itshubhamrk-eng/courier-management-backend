import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { MasterDataService } from '@features/masters/master-data.service';
import { NotificationService } from '@core/services/notification.service';
import { DistrictLevelFreightService } from '../district-level-freight.service';
import { DistrictFreightImportDialog } from './district-freight-import-dialog';

describe('DistrictFreightImportDialog — branches picked before importing', () => {
  let fixture: ComponentFixture<DistrictFreightImportDialog>;
  let dialog: DistrictFreightImportDialog;
  const summary = { dryRun: true, totalDataRows: 1, succeeded: 2, failed: 0, rows: [] };
  const previewImport = vi.fn();
  const commitImport = vi.fn();
  const file = new File(['x'], 'rates.xlsx');

  beforeEach(() => {
    previewImport.mockReset().mockReturnValue(of(summary));
    commitImport.mockReset().mockReturnValue(of({ ...summary, dryRun: false }));
    TestBed.configureTestingModule({
      imports: [DistrictFreightImportDialog],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: DistrictLevelFreightService, useValue: { previewImport, commitImport } },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } },
        { provide: MasterDataService, useValue: { options: () => of([
          { value: 'a', label: 'A' }, { value: 'b', label: 'B' }]) } }
      ]
    });
    fixture = TestBed.createComponent(DistrictFreightImportDialog);
    dialog = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('cannot preview until a file and at least one branch are chosen', () => {
    expect(dialog.canPreview()).toBe(false);
    dialog.file.set(file);
    expect(dialog.canPreview()).toBe(false);
    dialog.branches.setValue(['a']);
    expect(dialog.canPreview()).toBe(true);
  });

  it('previews with the picked branch ids', () => {
    dialog.file.set(file);
    dialog.branches.setValue(['a', 'b']);
    dialog.preview();
    expect(previewImport).toHaveBeenCalledWith(file, ['a', 'b']);
  });

  it('commits with the same picked branch ids', () => {
    dialog.file.set(file);
    dialog.branches.setValue(['a', 'b']);
    dialog.preview();
    dialog.commit();
    expect(commitImport).toHaveBeenCalledWith(file, ['a', 'b']);
  });
});
