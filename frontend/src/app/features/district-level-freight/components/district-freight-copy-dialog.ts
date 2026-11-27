import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { NotificationService } from '@core/services/notification.service';
import { CopyDistrictFreightResponse } from '@core/models/district-level-freight.model';
import { MasterDataService } from '@features/masters/master-data.service';
import { DistrictLevelFreightService } from '../district-level-freight.service';

/**
 * Copy one branch's active District Level Freight rows to other branches. A district the
 * target already has a rate for is skipped, never overwritten.
 */
@Component({
  selector: 'app-district-freight-copy-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, UiButton, UiSelect],
  template: `
    <div class="cp">
      <h2 class="text-h2">Copy rates from another branch</h2>
      <p class="text-caption">Every district and state rate of the source branch is copied to the chosen branches. Districts or states a target branch already has a rate for (or that are inactive) are skipped — existing rates are never changed.</p>

      @if (!result()) {
        <app-select [control]="source" label="Copy from (source branch)" [options]="branchOptions()" />
        <app-select [control]="targets" label="Copy to (target branches)" [options]="targetOptions()" [multiple]="true" />
      } @else {
        <div class="res">
          @for (t of result()!.targets; track t.branchId) {
            <div class="res__row">
              <strong>{{ t.branchCode }}</strong>
              <span class="chip chip--ok">Copied: {{ t.copied }}</span>
              <span class="chip">Skipped: {{ t.skipped }}</span>
            </div>
          }
        </div>
      }

      <div class="cp__actions">
        <app-button variant="stroked" (pressed)="close()">{{ result() ? 'Done' : 'Cancel' }}</app-button>
        @if (!result()) {
          <app-button icon="content_copy" [disabled]="!canSubmit()" [loading]="saving()" (pressed)="submit()">Copy rates</app-button>
        }
      </div>
    </div>
  `,
  styles: [`
    .cp { padding:24px; width:512px; max-width:90vw; box-sizing:border-box; display:flex; flex-direction:column; gap:14px; }
    .res { display:flex; flex-direction:column; gap:8px; max-height:320px; overflow:auto; }
    .res__row { display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
    .chip { display:inline-flex; align-items:center; height:26px; padding:0 10px; border-radius:999px;
      background:var(--surface-muted); font:600 15px var(--font-sans); color:var(--content-fg); }
    .chip--ok { background:var(--success-bg); color:var(--success); }
    .cp__actions { display:flex; justify-content:flex-end; gap:10px; }
  `]
})
export class DistrictFreightCopyDialog implements OnInit {
  private readonly ref = inject(MatDialogRef<DistrictFreightCopyDialog>);
  private readonly service = inject(DistrictLevelFreightService);
  private readonly masters = inject(MasterDataService);
  private readonly notify = inject(NotificationService);

  readonly source = new FormControl<string | null>(null);
  readonly targets = new FormControl<string[]>([], { nonNullable: true });
  readonly branchOptions = signal<SelectOption[]>([]);
  readonly saving = signal(false);
  readonly result = signal<CopyDistrictFreightResponse | null>(null);

  private readonly sourceId = toSignal(this.source.valueChanges, { initialValue: this.source.value });
  private readonly targetIds = toSignal(this.targets.valueChanges, { initialValue: this.targets.value });

  /** The source branch is never offered as a target. */
  readonly targetOptions = computed(() => this.branchOptions().filter((o) => o.value !== this.sourceId()));
  readonly canSubmit = computed(() => !!this.sourceId() && this.targetIds().filter((t) => t !== this.sourceId()).length > 0);

  ngOnInit(): void {
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.source.valueChanges.subscribe((id) => {
      if (id) this.targets.setValue(this.targets.value.filter((t) => t !== id));
    });
  }

  submit(): void {
    const sourceBranchId = this.source.value;
    if (!sourceBranchId || !this.canSubmit()) return;
    this.saving.set(true);
    this.service.copy({ sourceBranchId, targetBranchIds: this.targets.value }).subscribe({
      next: (r) => { this.result.set(r); this.saving.set(false); },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.notify.error(e?.error?.message ?? 'Could not copy the rates.');
      }
    });
  }

  close(): void {
    this.ref.close((this.result()?.targets ?? []).some((t) => t.copied > 0));
  }
}
