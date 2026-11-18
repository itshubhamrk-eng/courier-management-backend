import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import {
  LegacyCommitResult, LegacyPreviewResult, LegacyPreviewRow, LegacyRowOutcome
} from '@core/models/legacy-import.model';
import { LegacyImportService } from './legacy-import.service';
import { downloadLegacyReport } from './legacy-csv';

type Stage = 'pick' | 'previewing' | 'ready' | 'committing' | 'done';
type RowFilter = 'ALL' | LegacyRowOutcome | 'DIFFERS';

/** Rows painted at once — the table is not virtualised; the CSV always carries every row. */
const MAX_VISIBLE_ROWS = 500;

/**
 * Legacy consignment import: pick the old software's export, preview it (writes nothing), map
 * each legacy origin to one of our branches, then commit. Imported shipments are history only —
 * DELIVERED, no pricing, wallet, commission or notification. A consignment already in the system
 * is never modified; differences are only reported.
 */
@Component({
  selector: 'app-legacy-import-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatIconModule, UiButton, UiSelect],
  template: `
    <div class="page">
      <header class="page__head">
        <div>
          <h1 class="text-h1">Legacy Import</h1>
          <p class="text-caption">Bring the old software's consignment export in as history-only shipments. Existing consignments are never changed.</p>
        </div>
      </header>

      @if (stage() === 'pick' || stage() === 'previewing') {
        <div class="picker">
          <button type="button" class="picker__btn" (click)="fileInput.click()">
            <mat-icon>upload_file</mat-icon> {{ file() ? file()!.name : 'Choose the Consignments .xls file' }}
          </button>
          <input #fileInput type="file" accept=".xls,.xlsx" hidden (change)="onFile($event)" />
          <app-button icon="visibility" [disabled]="!file()" [loading]="stage() === 'previewing'" (pressed)="preview()">Preview</app-button>
        </div>
      }

      @if (preview_(); as p) {
        <div class="tiles">
          <button type="button" class="tile" [class.tile--on]="filter() === 'ALL'" (click)="filter.set('ALL')"><b>{{ p.total }}</b><span>Rows</span></button>
          <button type="button" class="tile tile--ok" [class.tile--on]="filter() === 'NEW'" (click)="filter.set('NEW')"><b>{{ stage() === 'done' ? result()!.created : p.newCount }}</b><span>{{ stage() === 'done' ? 'Created' : 'New' }}</span></button>
          <button type="button" class="tile" [class.tile--on]="filter() === 'EXISTING'" (click)="filter.set('EXISTING')"><b>{{ shown(p.existingCount, result()?.skippedExisting) }}</b><span>Already present</span></button>
          <button type="button" class="tile" [class.tile--on]="filter() === 'DIFFERS'" (click)="filter.set('DIFFERS')"><b>{{ p.differsCount }}</b><span>Present, but differs</span></button>
          <button type="button" class="tile tile--err" [class.tile--on]="filter() === 'INVALID'" (click)="filter.set('INVALID')"><b>{{ shown(p.invalidCount, result()?.invalid) }}</b><span>Invalid</span></button>
        </div>

        @if (stage() === 'ready' && p.origins.length) {
          <section class="card">
            <h2 class="text-h3">Map each origin to a branch</h2>
            <p class="text-caption">Every new shipment is booked from the branch its origin maps to. Choices are remembered for the next upload.</p>
            <div class="origins">
              @for (o of p.origins; track o.originKey) {
                <div class="origin">
                  <div class="origin__name"><b>{{ o.originKey }}</b><span class="text-caption">{{ o.newRows }} new</span></div>
                  <app-select [control]="controlFor(o.originKey)" label="Branch" [options]="branchOptions()" placeholder="Choose a branch" />
                </div>
              }
            </div>
          </section>
        }

        <div class="bar">
          <span class="text-caption">Showing {{ visibleRows().length }} of {{ filteredRows().length }}{{ filteredRows().length > MAX ? ' (first ' + MAX + '; the report has all)' : '' }}</span>
          <span class="bar__spacer"></span>
          @if (stage() === 'done') {
            <app-button variant="stroked" icon="download" (pressed)="downloadReport()">Download report</app-button>
            <app-button variant="stroked" (pressed)="reset()">Import another file</app-button>
          } @else {
            <app-button variant="stroked" (pressed)="reset()">Cancel</app-button>
            <app-button icon="cloud_upload" [disabled]="!canCommit()" [loading]="stage() === 'committing'" (pressed)="commit()">
              Import {{ p.newCount }} shipment{{ p.newCount === 1 ? '' : 's' }}
            </app-button>
          }
        </div>
        @if (stage() === 'ready' && unmapped() > 0) {
          <p class="warn">Map {{ unmapped() }} more origin{{ unmapped() === 1 ? '' : 's' }} to enable the import.</p>
        }

        <div class="rows app-scroll">
          <table class="rtbl">
            <thead><tr><th>Row</th><th>Consignment No</th><th>Origin</th><th>Outcome</th><th>Details</th></tr></thead>
            <tbody>
              @for (r of visibleRows(); track r.rowNo) {
                <tr [class.err]="r.outcome === 'INVALID'">
                  <td>{{ r.rowNo }}</td>
                  <td>{{ r.consignmentNo }}</td>
                  <td>{{ r.origin }}</td>
                  <td>{{ r.outcome }}</td>
                  <td>{{ r.reason || (r.differences.length ? r.differences.join(' · ') : '—') }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .picker { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin-bottom:16px; }
    .picker__btn { display:flex; align-items:center; gap:8px; height:44px; padding:0 16px;
      border:1px dashed var(--surface-border); border-radius:var(--r-field); background:var(--surface);
      color:var(--content-fg); font:500 17px var(--font-sans); cursor:pointer; }
    .picker__btn:hover { border-color:var(--brand-500); }
    .tiles { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:10px; margin-bottom:16px; }
    @media (max-width:900px){ .tiles { grid-template-columns:repeat(2,minmax(0,1fr)); } }
    .tile { display:flex; flex-direction:column; gap:2px; padding:12px 14px; text-align:left; cursor:pointer;
      border:1px solid var(--surface-border); border-radius:var(--r-field); background:var(--surface); color:var(--content-fg); font:400 15px var(--font-sans); }
    .tile b { font:700 24px var(--font-sans); }
    .tile--ok b { color:var(--success); }
    .tile--err b { color:var(--danger); }
    .tile--on { border-color:var(--brand-500); box-shadow:0 0 0 1px var(--brand-500); }
    .card { border:1px solid var(--surface-border); border-radius:var(--r-field); background:var(--surface); padding:16px; margin-bottom:16px; }
    .origins { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:14px; margin-top:12px; }
    .origin { display:flex; flex-direction:column; gap:6px; }
    .origin__name { display:flex; gap:8px; align-items:baseline; }
    .bar { display:flex; gap:10px; align-items:center; flex-wrap:wrap; margin-bottom:10px; }
    .bar__spacer { flex:1; }
    .warn { color:var(--danger); font:500 15px var(--font-sans); margin:0 0 10px; }
    .rows { max-height:520px; overflow:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .rtbl { width:100%; border-collapse:collapse; font:400 16px var(--font-sans); }
    .rtbl th { position:sticky; top:0; text-align:left; padding:8px 10px; background:var(--surface-muted); font:600 15px var(--font-sans); }
    .rtbl td { padding:6px 10px; border-top:1px solid var(--surface-border); word-break:break-word; }
    .rtbl tr.err td { background:var(--danger-bg); color:var(--danger); }
  `]
})
export class LegacyImportPage implements OnInit {
  private readonly service = inject(LegacyImportService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);

  readonly MAX = MAX_VISIBLE_ROWS;
  readonly stage = signal<Stage>('pick');
  readonly file = signal<File | null>(null);
  /** Named with a trailing underscore: `preview()` is the action that loads it. */
  readonly preview_ = signal<LegacyPreviewResult | null>(null);
  readonly result = signal<LegacyCommitResult | null>(null);
  readonly filter = signal<RowFilter>('ALL');
  readonly branchOptions = signal<SelectOption[]>([]);
  /** originKey -> the branch picked or already remembered; the source of truth for "is it mapped". */
  readonly mapping = signal<Record<string, string | null>>({});
  private controls = new Map<string, FormControl<string | null>>();

  readonly unmapped = computed(() => {
    const p = this.preview_();
    const m = this.mapping();
    return p ? p.origins.filter((o) => !m[o.originKey]).length : 0;
  });
  readonly canCommit = computed(() =>
    this.stage() === 'ready' && (this.preview_()?.newCount ?? 0) > 0 && this.unmapped() === 0);

  /** After the commit, the rows shown are the commit's own outcomes, not the preview's guesses. */
  readonly rows = computed<LegacyPreviewRow[]>(() => this.result()?.rows ?? this.preview_()?.rows ?? []);
  readonly filteredRows = computed(() => {
    const f = this.filter();
    return this.rows().filter((r) =>
      f === 'ALL' ? true : f === 'DIFFERS' ? r.outcome === 'EXISTING' && r.differences.length > 0 : r.outcome === f);
  });
  readonly visibleRows = computed(() => this.filteredRows().slice(0, MAX_VISIBLE_ROWS));

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Legacy Import' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
  }

  shown(previewCount: number, committed: number | undefined): number {
    return this.stage() === 'done' && committed !== undefined ? committed : previewCount;
  }

  onFile(event: Event): void {
    this.file.set((event.target as HTMLInputElement).files?.[0] ?? null);
  }

  preview(): void {
    const file = this.file();
    if (!file) return;
    this.stage.set('previewing');
    this.service.preview(file).subscribe({
      next: (p) => this.applyPreview(p),
      error: (e: HttpErrorResponse) => {
        this.stage.set('pick');
        this.notify.error(e?.error?.message ?? 'Could not read the file.');
      }
    });
  }

  private applyPreview(p: LegacyPreviewResult): void {
    this.controls = new Map();
    const initial: Record<string, string | null> = {};
    for (const o of p.origins) {
      initial[o.originKey] = o.mappedBranchId;
      const control = new FormControl<string | null>(o.mappedBranchId);
      control.valueChanges.subscribe((branchId) => this.saveMapping(o.originKey, branchId));
      this.controls.set(o.originKey, control);
    }
    this.mapping.set(initial);
    this.preview_.set(p);
    this.result.set(null);
    this.filter.set('ALL');
    this.stage.set('ready');
  }

  controlFor(originKey: string): FormControl<string | null> {
    return this.controls.get(originKey)!;
  }

  private saveMapping(origin: string, branchId: string | null): void {
    if (!branchId) return;
    this.service.mapOrigin(origin, branchId).subscribe({
      next: () => this.mapping.update((m) => ({ ...m, [origin]: branchId })),
      error: (e: HttpErrorResponse) => {
        this.mapping.update((m) => ({ ...m, [origin]: null }));
        this.controls.get(origin)?.setValue(null, { emitEvent: false });
        this.notify.error(e?.error?.message ?? `Could not map ${origin}.`);
      }
    });
  }

  commit(): void {
    const file = this.file();
    if (!file || !this.canCommit()) return;
    this.stage.set('committing');
    this.service.commit(file).subscribe({
      next: (r) => {
        this.result.set(r);
        this.filter.set('ALL');
        this.stage.set('done');
        this.notify.success(`Import complete: ${r.created} created, ${r.skippedExisting} already present, ${r.invalid} invalid.`);
      },
      error: (e: HttpErrorResponse) => {
        this.stage.set('ready');
        this.notify.error(e?.error?.message ?? 'Import failed.');
      }
    });
  }

  downloadReport(): void {
    downloadLegacyReport(`legacy-import-${new Date().toISOString().slice(0, 10)}.csv`, this.rows());
  }

  reset(): void {
    this.stage.set('pick');
    this.file.set(null);
    this.preview_.set(null);
    this.result.set(null);
    this.mapping.set({});
    this.controls = new Map();
  }
}
