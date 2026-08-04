import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Page } from '@core/models/page.model';

/** Pager driven by the backend Page<T> envelope. Emits 0-based page indexes. */
@Component({
  selector: 'app-pagination',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <div class="pg">
      <span class="text-caption">
        {{ from() }}–{{ to() }} of {{ page().totalElements }}
      </span>
      <div class="pg__ctrls">
        <button class="pg__btn" [disabled]="page().first" (click)="go(page().page - 1)"><mat-icon>chevron_left</mat-icon></button>
        <span class="pg__cur">Page {{ page().page + 1 }} / {{ Math.max(1, page().totalPages) }}</span>
        <button class="pg__btn" [disabled]="page().last" (click)="go(page().page + 1)"><mat-icon>chevron_right</mat-icon></button>
      </div>
    </div>
  `,
  styles: [`
    .pg { display:flex; align-items:center; justify-content:space-between; padding:12px 4px; }
    .pg__ctrls { display:flex; align-items:center; gap:8px; }
    .pg__cur { font:500 13px var(--font-sans); color:var(--content-muted); }
    .pg__btn { display:grid; place-items:center; width:34px; height:34px; border-radius:8px;
      border:1px solid var(--surface-border); background:var(--surface); cursor:pointer; }
    .pg__btn:disabled { opacity:.4; cursor:not-allowed; }
  `]
})
export class UiPagination<T> {
  readonly page = input.required<Page<T>>();
  readonly pageChange = output<number>();
  protected readonly Math = Math;

  readonly from = computed(() => this.page().totalElements === 0 ? 0 : this.page().page * this.page().size + 1);
  readonly to = computed(() => Math.min((this.page().page + 1) * this.page().size, this.page().totalElements));

  go(index: number): void {
    if (index < 0 || index >= this.page().totalPages) return;
    this.pageChange.emit(index);
  }
}
