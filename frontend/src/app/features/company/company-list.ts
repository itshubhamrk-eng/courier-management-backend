import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { Company } from '@core/models/company.model';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { UiTable, TableColumn, SortState } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { CompanyService } from './company.service';

@Component({
  selector: 'app-company-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiTable, UiPagination, UiSearch, StatusBadge, UiButton],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Companies</h1><p class="text-caption">Every company on the platform.</p></div>
        <div class="page__actions">
          <app-search placeholder="Search companies…" (changed)="onSearch($event)" />
          <app-button icon="add_business" (pressed)="create()">New Company</app-button>
        </div>
      </header>
      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [sort]="sort()"
                 emptyTitle="No companies" (sortChange)="onSort($event)" (rowClick)="open($event)">
        <ng-template #row let-c>
          <td><div class="cs">{{ c.companyName }}</div><div class="text-caption">{{ c.companyCode }}</div></td>
          <td>{{ c.email }}<div class="text-caption">{{ c.mobile }}</div></td>
          <td>{{ c.city || '—' }}, {{ c.state || '' }}</td>
          <td><app-status-badge [value]="c.status" /></td>
        </ng-template>
      </app-table>
      <app-pagination [page]="page()" (pageChange)="onPage($event)" />
    </div>
  `,
  styles: [`
    .page__actions { display:flex; align-items:center; gap:10px; }.cs{font:600 14px var(--font-sans)}`]
})
export class CompanyList implements OnInit {
  private readonly service = inject(CompanyService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  readonly loading = signal(true);
  readonly page = signal<Page<Company>>(emptyPage<Company>());
  readonly sort = signal<SortState | null>({ active: 'companyName', direction: 'asc' });
  private query: PageQuery = { page: 0, size: 20, sort: 'companyName,asc' };
  readonly columns: TableColumn<Company>[] = [
    { key: 'companyName', header: 'Company', sortable: true },
    { key: 'email', header: 'Contact' },
    { key: 'city', header: 'Location' },
    { key: 'status', header: 'Status', sortable: true, width: '120px' }
  ];
  ngOnInit(): void { this.breadcrumb.set([{ label: 'Management' }, { label: 'Companies' }]); this.load(); }
  private load(): void { this.loading.set(true); this.service.list(this.query).subscribe({ next: (p) => { this.page.set(p); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  onSearch(t: string) { this.query = { ...this.query, search: t || undefined, page: 0 }; this.load(); }
  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.query = { ...this.query, sort: `${s.active},${s.direction}` }; this.load(); }
  create(): void { this.router.navigate(['/companies', 'new']); }

  open(c: Company) { this.router.navigate(['/companies', c.id]); }
}
