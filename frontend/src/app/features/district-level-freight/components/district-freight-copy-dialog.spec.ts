import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { MasterDataService } from '@features/masters/master-data.service';
import { DistrictLevelFreightService } from '../district-level-freight.service';
import { DistrictFreightCopyDialog } from './district-freight-copy-dialog';

describe('DistrictFreightCopyDialog', () => {
  let fixture: ComponentFixture<DistrictFreightCopyDialog>;
  let dialog: DistrictFreightCopyDialog;
  const copy = vi.fn();

  beforeEach(() => {
    copy.mockReset().mockReturnValue(of({ targets: [{ branchId: 'b', branchCode: 'B', copied: 2, skipped: 1 }] }));
    TestBed.configureTestingModule({
      imports: [DistrictFreightCopyDialog],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: DistrictLevelFreightService, useValue: { copy } },
        { provide: MasterDataService, useValue: { options: () => of([
          { value: 'a', label: 'A' }, { value: 'b', label: 'B' }, { value: 'c', label: 'C' }]) } }
      ]
    });
    fixture = TestBed.createComponent(DistrictFreightCopyDialog);
    dialog = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('cannot submit until a source and at least one target are chosen', () => {
    expect(dialog.canSubmit()).toBe(false);
    dialog.source.setValue('a');
    expect(dialog.canSubmit()).toBe(false);
    dialog.targets.setValue(['b']);
    expect(dialog.canSubmit()).toBe(true);
  });

  it('never offers the source as a target and drops it if already picked', () => {
    dialog.targets.setValue(['a', 'b']);
    dialog.source.setValue('a');
    expect(dialog.targetOptions().map((o) => o.value)).toEqual(['b', 'c']);
    expect(dialog.targets.value).toEqual(['b']);
  });

  it('submits and shows the per-target copied/skipped counts', () => {
    dialog.source.setValue('a');
    dialog.targets.setValue(['b']);
    dialog.submit();
    expect(copy).toHaveBeenCalledWith({ sourceBranchId: 'a', targetBranchIds: ['b'] });
    expect(dialog.result()!.targets[0]).toMatchObject({ copied: 2, skipped: 1 });
  });
});
