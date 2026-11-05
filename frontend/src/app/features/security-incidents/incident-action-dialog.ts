import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { UiButton } from '@shared/components/ui-button/ui-button';

export const NOTE_MAX = 1000;

export interface IncidentActionDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
}

/** Result: `{ note }` when confirmed (null when left blank), `undefined` when cancelled. */
export interface IncidentActionResult { note: string | null; }

/** Confirm with an optional note, for ban / dismiss / unban. */
@Component({
  selector: 'app-incident-action-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, MatIconModule, UiButton],
  template: `
    <form class="ad" role="alertdialog" aria-labelledby="ad-title" aria-describedby="ad-msg" (ngSubmit)="submit()">
      <div class="ad__head">
        <mat-icon class="ad__icon" [class.danger]="data.danger" aria-hidden="true">{{ data.danger ? 'warning' : 'help' }}</mat-icon>
        <div>
          <h2 class="text-h2" id="ad-title">{{ data.title }}</h2>
          <p class="text-caption" id="ad-msg">{{ data.message }}</p>
        </div>
      </div>
      <label class="ad__f">
        <span class="ad__l">Note (optional)</span>
        <textarea class="ad__t" rows="3" [formControl]="note" [attr.maxlength]="max"></textarea>
        <span class="text-caption">{{ note.value.length }} / {{ max }}</span>
      </label>
      <div class="ad__actions">
        <app-button variant="stroked" type="button" (pressed)="ref.close()">Cancel</app-button>
        <app-button type="submit" [variant]="data.danger ? 'danger' : 'primary'" [disabled]="note.invalid">{{ data.confirmLabel }}</app-button>
      </div>
    </form>
  `,
  styles: [`
    .ad { padding:28px; width:480px; max-width:92vw; display:flex; flex-direction:column; gap:18px; }
    .ad__head { display:flex; gap:14px; align-items:flex-start; }
    .ad__icon { color:var(--brand-500); background:var(--brand-50); border-radius:14px; padding:8px; box-sizing:content-box;
      font-size:31px; width:28px; height:28px; box-shadow:var(--shadow-clay-sm); }
    .ad__icon.danger { background:var(--danger-bg); color:var(--danger); }
    .ad__f { display:flex; flex-direction:column; gap:6px; }
    .ad__l { font:500 16px var(--font-sans); color:var(--content-fg); }
    .ad__t { padding:10px 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 17px var(--font-sans); color:var(--content-fg); resize:vertical; }
    .ad__actions { display:flex; justify-content:flex-end; gap:10px; }
  `]
})
export class IncidentActionDialog {
  readonly ref = inject<MatDialogRef<IncidentActionDialog, IncidentActionResult | undefined>>(MatDialogRef);
  readonly data = inject<IncidentActionDialogData>(MAT_DIALOG_DATA);
  readonly max = NOTE_MAX;
  readonly note = new FormControl<string>('', { nonNullable: true, validators: [Validators.maxLength(NOTE_MAX)] });

  submit(): void {
    if (this.note.invalid) return;
    const trimmed = this.note.value.trim();
    this.ref.close({ note: trimmed || null });
  }
}
