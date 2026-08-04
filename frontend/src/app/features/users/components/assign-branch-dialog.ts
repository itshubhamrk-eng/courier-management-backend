import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { NotificationService } from '@core/services/notification.service';
import { UserProfile } from '@core/models/user.model';
import { Lookup, UserService } from '../user.service';

export interface AssignBranchData {
  userId: string;
  displayName: string;
  currentId: string | null;
  options: Lookup[];
}

/** Place a user at (or clear) a branch. A null selection clears the placement. */
@Component({
  selector: 'app-assign-branch-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, UiSelect, UiButton],
  template: `
    <div class="pd">
      <h2 class="text-h2">Assign branch</h2>
      <p class="text-caption">{{ data.displayName }} — a user belongs to at most one branch.</p>
      <app-select [control]="picker" label="Branch" [options]="options()" [allowEmpty]="true" emptyLabel="Unassigned" />
      <div class="pd__actions">
        <app-button variant="stroked" (pressed)="ref.close(null)">Cancel</app-button>
        <app-button icon="save" [loading]="busy()" (pressed)="save()">Save</app-button>
      </div>
    </div>
  `,
  styles: [`
    .pd { padding:24px; width:420px; max-width:92vw; display:flex; flex-direction:column; gap:16px; }
    .pd__actions { display:flex; justify-content:flex-end; gap:10px; margin-top:4px; }
  `]
})
export class AssignBranchDialog {
  readonly ref = inject(MatDialogRef<AssignBranchDialog>);
  readonly data = inject<AssignBranchData>(MAT_DIALOG_DATA);
  private readonly service = inject(UserService);
  private readonly notify = inject(NotificationService);

  readonly picker = new FormControl<string | null>(this.data.currentId);
  readonly busy = signal(false);
  readonly options = computed<SelectOption[]>(() => this.data.options.map((b) => ({ value: b.id, label: b.hint ? `${b.label} · ${b.hint}` : b.label })));

  save(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.service.assignBranch(this.data.userId, this.picker.value ?? null).subscribe({
      next: (u: UserProfile) => { this.busy.set(false); this.notify.success('Branch updated.'); this.ref.close(u); },
      error: () => { this.busy.set(false); this.notify.error('Could not update the branch.'); }
    });
  }
}
