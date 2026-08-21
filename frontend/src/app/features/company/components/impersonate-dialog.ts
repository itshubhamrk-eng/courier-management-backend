import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';

export interface ImpersonateData {
  companyName: string;
}

/**
 * Step-up confirmation before a SUPER_ADMIN "logs in as" a company — re-enters their
 * own current password even though they are already authenticated, as a defence
 * against a hijacked or left-open SUPER_ADMIN session reaching into every company on
 * the platform. Mirrors {@code ReasonDialog}'s shape, but the field is a masked
 * password, not free text.
 */
@Component({
  selector: 'app-impersonate-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, MatIconModule, UiButton, UiInput],
  template: `
    <form class="id" [formGroup]="form" (ngSubmit)="submit()">
      <div class="id__head">
        <mat-icon class="id__icon">admin_panel_settings</mat-icon>
        <div>
          <h2 class="text-h2">Login as {{ data.companyName }}</h2>
          <p class="text-caption">
            Opens a 15-minute session acting as this company's own admin. Confirm your
            password to continue — this action is audited.
          </p>
        </div>
      </div>

      <app-input [control]="password" type="password" label="Your password" [togglePassword]="true"
        placeholder="••••••••" [required]="true" [maxLength]="72" />

      <div class="id__actions">
        <app-button variant="stroked" type="button" (pressed)="ref.close()">Cancel</app-button>
        <app-button type="submit" variant="primary" [disabled]="form.invalid">Login as company</app-button>
      </div>
    </form>
  `,
  styles: [`
    .id { padding:28px; width:440px; max-width:92vw; display:flex; flex-direction:column; gap:18px; }
    .id__head { display:flex; gap:14px; align-items:flex-start; }
    .id__icon { color:var(--brand-500); font-size:28px; width:28px; height:28px;
      background:var(--brand-50); border-radius:14px; padding:8px; box-sizing:content-box; box-shadow:var(--shadow-clay-sm); }
    .id__actions { display:flex; justify-content:flex-end; gap:10px; }
  `]
})
export class ImpersonateDialog {
  readonly ref = inject(MatDialogRef<ImpersonateDialog, string | undefined>);
  readonly data = inject<ImpersonateData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    password: ['', [Validators.required, Validators.maxLength(72)]]
  });

  get password(): FormControl {
    return this.form.get('password') as FormControl;
  }

  submit(): void {
    if (this.form.invalid) return;
    this.ref.close(this.form.getRawValue().password ?? undefined);
  }
}
