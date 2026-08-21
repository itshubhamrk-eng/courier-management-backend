import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { UiButton } from '../ui-button/ui-button';
import { UiInput } from '../ui-input/ui-input';

export interface ReasonData {
  title: string;
  message: string;
  label?: string;
  placeholder?: string;
  confirmLabel?: string;
  danger?: boolean;
  maxLength?: number;
}

/**
 * A confirm that also collects the reason.
 *
 * <p>Several backend endpoints refuse without one — suspending a company, suspending a
 * subscription — because "why is Acme suspended?" is the first thing support is asked.
 * A plain confirm followed by a 422 would teach the operator that the button is broken,
 * so the reason is collected before the call rather than discovered after it.
 *
 * <p>Returns the trimmed reason, or `undefined` when cancelled. Never an empty string:
 * the caller's `if (!reason) return;` must not be defeated by whitespace.
 */
@Component({
  selector: 'app-reason-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, MatIconModule, UiButton, UiInput],
  template: `
    <form class="rd" [formGroup]="form" (ngSubmit)="submit()">
      <div class="rd__head">
        <mat-icon class="rd__icon" [class.danger]="data.danger">
          {{ data.danger ? 'warning' : 'help' }}
        </mat-icon>
        <div>
          <h2 class="text-h2">{{ data.title }}</h2>
          <p class="text-caption">{{ data.message }}</p>
        </div>
      </div>

      <app-input [control]="reason" [label]="data.label ?? 'Reason'"
        [placeholder]="data.placeholder ?? ''" [required]="true" [maxLength]="data.maxLength ?? 500" />

      <div class="rd__actions">
        <app-button variant="stroked" type="button" (pressed)="ref.close()">Cancel</app-button>
        <app-button type="submit" [variant]="data.danger ? 'danger' : 'primary'" [disabled]="form.invalid">
          {{ data.confirmLabel ?? 'Confirm' }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .rd { padding:28px; width:480px; max-width:92vw; display:flex; flex-direction:column; gap:18px; }
    .rd__head { display:flex; gap:14px; align-items:flex-start; }
    .rd__icon { color:var(--brand-500); font-size:28px; width:28px; height:28px; }
    .rd__icon { background:var(--brand-50); border-radius:14px; padding:8px; box-sizing:content-box; box-shadow:var(--shadow-clay-sm); }
    .rd__icon.danger { background:var(--danger-bg); }
    .rd__icon.danger { color:var(--danger); }
    .rd__actions { display:flex; justify-content:flex-end; gap:10px; }
  `]
})
export class ReasonDialog {
  readonly ref = inject(MatDialogRef<ReasonDialog, string | undefined>);
  readonly data = inject<ReasonData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    reason: ['', [Validators.required, notBlank, Validators.maxLength(this.data.maxLength ?? 500)]]
  });

  get reason(): FormControl {
    return this.form.get('reason') as FormControl;
  }

  submit(): void {
    if (this.form.invalid) return;
    this.ref.close((this.form.getRawValue().reason ?? '').trim());
  }
}

/** `Validators.required` accepts "   ", and so would the button. The server would not. */
function notBlank(control: { value: unknown }) {
  return String(control.value ?? '').trim() ? null : { required: true };
}
