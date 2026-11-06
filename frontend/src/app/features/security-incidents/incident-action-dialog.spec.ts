import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { describe, expect, it, vi } from 'vitest';
import { IncidentActionDialog } from './incident-action-dialog';

describe('IncidentActionDialog', () => {
  it('closes with the trimmed note when the form is submitted', () => {
    const close = vi.fn();
    TestBed.configureTestingModule({
      imports: [IncidentActionDialog],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { title: 't', message: 'm', confirmLabel: 'Go' } },
      ],
    });
    const f = TestBed.createComponent(IncidentActionDialog);
    f.detectChanges();
    f.componentInstance.note.setValue('  hello ');
    // Real submit event: fails if the <form> has no form directive (ngSubmit never fires).
    f.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    expect(close).toHaveBeenCalledWith({ note: 'hello' });
  });
});
