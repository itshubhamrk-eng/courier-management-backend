// Hub module not built yet — backend has no /api/v1/hubs controller. Disabled, not routed
// (see app.routes.ts, navigation.config.ts).
/*
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { UiTable, TableColumn, SortState } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { HubService } from './hub.service';

/** Hubs — same shape as branches; the backend module lands next, so the table shows an
 *  empty state until the endpoint exists (no mock data). * /
@Component({
  selector: 'app-hub-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiTable, UiPagination, UiSearch, UiButton, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Hubs</h1><p class="text-caption">Sorting hubs in your network.</p></div>
        <div class="page__actions"><app-search placeholder="Search hubs…" (changed)="onSearch($event)" /><app-button icon="add">New Hub</app-button></div>
      </header>
      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [sort]="sort()"
                 emptyTitle="No hubs yet" emptyHint="Hub management arrives in the next release." (sortChange)="onSort($event)" idKey="id">
        <ng-template #row let-h>
          <td><div class="cs">{{ h['hubName'] }}</div><div class="text-caption">{{ h['hubCode'] }}</div></td>
          <td>{{ h['city'] || '—' }}</td>
          <td><app-status-badge [value]="asStr(h['status'])" /></td>
        </ng-template>
      </app-table>
      <app-pagination [page]="page()" (pageChange)="onPage($event)" />
    </div>
  `,
  styles: [`.cs{font:600 14px var(--font-sans)}`]
})
export class HubList implements OnInit {
  private readonly service = inject(HubService);
  private readonly breadcrumb = inject(BreadcrumbService);
  readonly loading = signal(true);
  readonly page = signal<Page<Record<string, unknown>>>(emptyPage());
  readonly sort = signal<SortState | null>(null);
  private query: PageQuery = { page: 0, size: 20 };
  readonly columns: TableColumn<Record<string, unknown>>[] = [
    { key: 'hubName', header: 'Hub' }, { key: 'city', header: 'Location' },
    { key: 'status', header: 'Status', width: '120px' }
  ];
  ngOnInit(): void { this.breadcrumb.set([{ label: 'Management' }, { label: 'Hubs' }]); this.load(); }
  private load(): void { this.loading.set(true); this.service.list(this.query).subscribe({ next: (p) => { this.page.set(p); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  onSearch(t: string) { this.query = { ...this.query, search: t || undefined, page: 0 }; this.load(); }
  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.load(); }
  asStr(v: unknown) { return (v as string) ?? ''; }
}
*/
