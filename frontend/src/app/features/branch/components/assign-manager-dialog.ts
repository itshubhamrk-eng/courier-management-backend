import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { NotificationService } from '@core/services/notification.service';
import { BranchResponse } from '@core/models/branch.model';
import { Lookup, BranchService } from '../branch.service';

export interface AssignManagerData {
  branchId: string;
  branchName: string;
  currentId: string | null;
  options: Lookup[];
}

/**
 * Set (or clear) a branch's manager — a company user, one per branch, via the backend
 * assign-manager endpoint. A null selection clears it. Returns the updated BranchResponse
 * so the caller can refresh without a second fetch.
 *
 * Honesty: the UI-10 spec asked for an "Assign Hub" dialog, but a branch has no hub
 * relation in this backend (hubs are their own module; a user carries `hub_id`). The real
 * per-branch assignment endpoint is the manager, so that is what this dialog drives.
 */
@Component({
  selector: 'app-assign-manager-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, UiSelect, UiButton],
  template: `
    <div class="md">
      <h2 class="text-h2">Assign manager</h2>
      <p class="text-caption">{{ data.branchName }} — one manager per branch.</p>
      <app-select [control]="picker" label="Manager" [options]="options()" [allowEmpty]="true" emptyLabel="Unassigned" />
      <div class="md__actions">
        <app-button variant="stroked" (pressed)="ref.close(null)">Cancel</app-button>
        <app-button icon="save" [loading]="busy()" (pressed)="save()">Save</app-button>
      </div>
    </div>
  `,
  styles: [`
    .md { padding:24px; width:420px; max-width:92vw; display:flex; flex-direction:column; gap:16px; }
    .md__actions { display:flex; justify-content:flex-end; gap:10px; margin-top:4px; }
  `]
})
export class AssignManagerDialog {
  readonly ref = inject(MatDialogRef<AssignManagerDialog>);
  readonly data = inject<AssignManagerData>(MAT_DIALOG_DATA);
  private readonly service = inject(BranchService);
  private readonly notify = inject(NotificationService);

  readonly picker = new FormControl<string | null>(this.data.currentId);
  readonly busy = signal(false);
  readonly options = computed<SelectOption[]>(() =>
    this.data.options.map((m) => ({ value: m.id, label: m.hint ? `${m.label} · ${m.hint}` : m.label })));

  save(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.service.assignManager(this.data.branchId, this.picker.value ?? null).subscribe({
      next: (b: BranchResponse) => { this.busy.set(false); this.notify.success('Manager updated.'); this.ref.close(b); },
      error: (e) => { this.busy.set(false); this.notify.error(e?.error?.message ?? 'Could not update the manager.'); }
    });
  }
}
