import { ChangeDetectionStrategy, Component, TemplateRef, contentChild, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { UiLoader } from '../ui-loader/ui-loader';

export interface TableColumn<T = Record<string, unknown>> {
  key: string;
  header: string;
  sortable?: boolean;
  width?: string;
  align?: 'left' | 'right' | 'center';
  cell?: (row: T) => string;   // simple text projection; use the row template for rich cells
}

export interface SortState { active: string; direction: 'asc' | 'desc'; }

/**
 * Generic data table. Columns are declared; a `#row` template projects rich cells (badges,
 * actions) when a plain `cell()` is not enough. Emits sort changes; loading/empty are
 * first-class states so every list looks consistent.
 */
@Component({
  selector: 'app-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, MatIconModule, UiLoader],
  template: `
    <div class="app-card tbl__wrap app-scroll">
      <table class="tbl">
        <thead>
          <tr>
            @for (col of columns(); track col.key) {
              <th [style.width]="col.width" [style.text-align]="col.align || 'left'"
                  [class.tbl__th--sortable]="col.sortable" (click)="onSort(col)">
                <span class="tbl__th">{{ col.header }}
                  @if (col.sortable && sort()?.active === col.key) {
                    <mat-icon class="tbl__sort">{{ sort()!.direction === 'asc' ? 'arrow_upward' : 'arrow_downward' }}</mat-icon>
                  }
                </span>
              </th>
            }
          </tr>
        </thead>
        <tbody>
          @if (loading()) {
            <tr><td [attr.colspan]="columns().length"><app-loader [minHeight]="200" caption="Loading…" /></td></tr>
          } @else if (rows().length === 0) {
            <tr><td [attr.colspan]="columns().length">
              <div class="tbl__empty">
                <mat-icon>inbox</mat-icon>
                <p class="text-h3">{{ emptyTitle() }}</p>
                <p class="text-caption">{{ emptyHint() }}</p>
              </div>
            </td></tr>
          } @else {
            @for (row of rows(); track trackId(row)) {
              <tr class="tbl__row" (click)="rowClick.emit(row)">
                @if (rowTpl()) {
                  <ng-container [ngTemplateOutlet]="rowTpl()!" [ngTemplateOutletContext]="{ $implicit: row }" />
                } @else {
                  @for (col of columns(); track col.key) {
                    <td [style.text-align]="col.align || 'left'">{{ col.cell ? col.cell(row) : value(row, col.key) }}</td>
                  }
                }
              </tr>
            }
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .tbl__wrap { overflow:auto; }
    .tbl { width:100%; border-collapse:collapse; font:400 14px var(--font-sans); }
    .tbl thead th { position:sticky; top:0; background:var(--surface-muted); color:var(--content-muted);
      font:600 12px var(--font-sans); text-transform:uppercase; letter-spacing:.04em;
      padding:12px 16px; border-bottom:1px solid var(--surface-border); white-space:nowrap; }
    .tbl__th { display:inline-flex; align-items:center; gap:4px; }
    .tbl__th--sortable { cursor:pointer; user-select:none; }
    .tbl__sort { font-size:14px; width:14px; height:14px; }
    .tbl tbody td { padding:14px 16px; border-bottom:1px solid var(--surface-border); color:var(--content-fg); }
    .tbl__row { transition:.12s; cursor:default; }
    .tbl__row:hover { background:var(--surface-muted); }
    .tbl__empty { display:flex; flex-direction:column; align-items:center; gap:6px; padding:48px 0; color:var(--content-muted); }
    .tbl__empty mat-icon { font-size:40px; width:40px; height:40px; opacity:.5; }
  `]
})
export class UiTable<T = Record<string, unknown>> {
  readonly columns = input.required<TableColumn<T>[]>();
  readonly rows = input<T[]>([]);
  readonly loading = input(false);
  readonly sort = input<SortState | null>(null);
  readonly idKey = input('id');
  readonly emptyTitle = input('No records');
  readonly emptyHint = input('Nothing to show yet.');
  readonly rowTpl = contentChild<TemplateRef<{ $implicit: T }>>('row');

  readonly sortChange = output<SortState>();
  readonly rowClick = output<T>();

  value(row: T, key: string): unknown { return (row as Record<string, unknown>)[key]; }
  trackId(row: T): unknown { return (row as Record<string, unknown>)[this.idKey()]; }

  onSort(col: TableColumn<T>): void {
    if (!col.sortable) return;
    const cur = this.sort();
    const direction: 'asc' | 'desc' =
      cur?.active === col.key && cur.direction === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ active: col.key, direction });
  }
}
