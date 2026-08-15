import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { UiButton } from '../ui-button/ui-button';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
}

/** Reusable confirm dialog; open it through DialogService.confirm(). */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatIconModule, UiButton],
  template: `
    <div class="cd">
      <div class="cd__icon" [class.cd__icon--danger]="data.danger">
        <mat-icon>{{ data.danger ? 'warning' : 'help' }}</mat-icon>
      </div>
      <h2 class="text-h2">{{ data.title }}</h2>
      <p class="text-body cd__msg">{{ data.message }}</p>
      <div class="cd__actions">
        <app-button variant="stroked" (pressed)="ref.close(false)">Cancel</app-button>
        <app-button [variant]="data.danger ? 'danger' : 'primary'" (pressed)="ref.close(true)">
          {{ data.confirmLabel || 'Confirm' }}
        </app-button>
      </div>
    </div>
  `,
  styles: [`
    .cd { padding:28px; width:380px; max-width:90vw; text-align:center; }
    .cd__icon { width:56px; height:56px; margin:0 auto 14px; display:grid; place-items:center;
      border-radius:20px; background:var(--brand-50); color:var(--brand-600); box-shadow:var(--shadow-clay-sm); }
    .cd__icon--danger { background:var(--danger-bg); color:var(--danger); }
    .cd__msg { color:var(--content-muted); margin:6px 0 20px; }
    .cd__actions { display:flex; gap:10px; justify-content:center; }
  `]
})
export class ConfirmDialog {
  readonly ref = inject(MatDialogRef<ConfirmDialog>);
  readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
}
