import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { BranchCommissionSummary, Shipment, ShipmentSearchRequest } from '@core/models/shipment.model';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { SortState, TableColumn } from '@shared/components/ui-table/ui-table';
import { UiTable } from '@shared/components/ui-table/ui-table';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiDrawer } from '@shared/components/ui-drawer/ui-drawer';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentFilter } from '../shipment/components/shipment-filter';
import { ShipmentService } from '../shipment/shipment.service';

/**
 * Commission Report — an overall total (summed client-side over
 * `GET /shipments/commission-summary`'s branch rows — see `ShipmentServiceImpl
 * .commissionSummary`) plus the per-shipment breakdown underneath, same filter/branch-lock/
 * pagination shape as Booking Report. Admin (no own branch) sees the total across every
 * branch; a branch role's own `bookingBranchId` lock (same as every other report) narrows
 * both the total and the detail table to its own shipments — no separate "admin vs branch"
 * code path, the existing lock does both jobs.
 */
@Component({
  selector: 'app-commission-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, UiTable, UiPagination, UiButton, UiDrawer, ShipmentFilter],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Commission Report</h1>
          <p class="text-caption">Commission earned {{ myBranchId ? 'by your branch' : 'across every branch' }}.</p></div>
        <div class="page__actions">
          <app-button variant="stroked" icon="filter_list" (pressed)="filterOpen.set(true)">
            Filters@if (activeFilters()) { <span class="fbadge">{{ activeFilters() }}</span> }
          </app-button>
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportSummaryCsv()">Export</app-button>
        </div>
      </header>

      <div class="stats">
        <div class="stat"><span class="stat__l">Shipments</span><span class="stat__v">{{ summaryLoading() ? '—' : totals().shipmentCount }}</span></div>
        <div class="stat"><span class="stat__l">Amount</span><span class="stat__v">{{ summaryLoading() ? '—' : ('₹' + (totals().totalNetAmount | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Commission on Basic Freight</span><span class="stat__v">{{ summaryLoading() ? '—' : ('₹' + (totals().commissionOnBasicFreight | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Branch Commission on Other Amount</span><span class="stat__v">{{ summaryLoading() ? '—' : ('₹' + (totals().branchCommissionOnOtherAmount | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Company Commission on Basic Freight</span><span class="stat__v">{{ summaryLoading() ? '—' : ('₹' + (totals().companyCommissionOnBasicFreight | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Total Commission</span><span class="stat__v">{{ summaryLoading() ? '—' : ('₹' + (totals().totalCommission | number: '1.2-2')) }}</span></div>
      </div>

      <section class="block">
        <h2 class="text-h3">Shipment-wise Breakdown</h2>
        <app-table [columns]="detailColumns" [rows]="page().content" [loading]="loading()" [sort]="sort()"
                   emptyTitle="No shipments" emptyHint="Nothing matches this filter yet."
                   (sortChange)="onSort($event)" (rowClick)="view($event)">
          <ng-template #row let-s>
            <td><span class="mono">{{ s.shipmentNumber }}</span></td>
            <td><span class="mono awb">{{ s.trackingNumber }}</span></td>
            <td>{{ s.bookingDate }}</td>
            <td>{{ branchLabel(s.bookingBranchId) }}</td>
            <td class="num">{{ s.netAmount != null ? ('₹' + (s.netAmount | number: '1.2-2')) : '—' }}</td>
            <td class="num">{{ s.commissionOnBasicFreight != null ? ('₹' + (s.commissionOnBasicFreight | number: '1.2-2')) : '—' }}</td>
            <td class="num">{{ s.branchCommissionOnOtherAmount != null ? ('₹' + (s.branchCommissionOnOtherAmount | number: '1.2-2')) : '—' }}</td>
            <td class="num">{{ s.companyCommissionOnBasicFreight != null ? ('₹' + (s.companyCommissionOnBasicFreight | number: '1.2-2')) : '—' }}</td>
            <td class="num strong">{{ s.totalCommission != null ? ('₹' + (s.totalCommission | number: '1.2-2')) : '—' }}</td>
          </ng-template>
        </app-table>
        <app-pagination [page]="page()" (pageChange)="onPage($event)" />
      </section>

      <app-drawer [open]="filterOpen()" title="Advanced filters" subtitle="Narrow the commission report." (closed)="filterOpen.set(false)">
        <app-shipment-filter mode="booking" [lockBookingBranch]="!!myBranchId" (changed)="onFilter($event)" />
      </app-drawer>
    </div>
  `,
  styles: [`
    .page__head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; flex-wrap:wrap; margin-bottom:12px; }
    .page__actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
    .fbadge { display:inline-grid; place-items:center; min-width:18px; height:18px; padding:0 5px; margin-left:2px;
      background:var(--brand-600); color:#fff; border-radius:999px; font:700 11px var(--font-sans); }
    .stats { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:20px; }
    .stat { display:flex; flex-direction:column; gap:4px; padding:12px 18px; min-width:150px;
      background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .stat__l { font:500 12px var(--font-sans); color:var(--content-muted); }
    .stat__v { font:700 20px var(--font-sans); color:var(--content-fg); }
    .block { margin-bottom:24px; }
    .block h2 { margin: 0 0 10px; }
    .mono { font:600 13px var(--font-mono, ui-monospace); color:var(--content-fg); }
    .awb { color:var(--brand-600); }
    .num { text-align:right; }
    .strong { font-weight:700; }
  `]
})
export class CommissionReport implements OnInit {
  private readonly service = inject(ShipmentService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  protected readonly branchOptions = signal<SelectOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly summaryLoading = signal(true);
  protected readonly exporting = signal(false);
  protected readonly filterOpen = signal(false);
  protected readonly page = signal<Page<Shipment>>(emptyPage<Shipment>());
  protected readonly sort = signal<SortState | null>({ active: 'bookingDate', direction: 'desc' });
  protected readonly summary = signal<BranchCommissionSummary[]>([]);
  /** The single total this report now leads with — summed client-side over every branch
   *  row `commissionSummary` returned (already filtered/branch-locked server-side). */
  protected readonly totals = computed(() => this.summary().reduce((t, r) => ({
    shipmentCount: t.shipmentCount + r.shipmentCount,
    totalNetAmount: t.totalNetAmount + r.totalNetAmount,
    commissionOnBasicFreight: t.commissionOnBasicFreight + r.commissionOnBasicFreight,
    branchCommissionOnOtherAmount: t.branchCommissionOnOtherAmount + r.branchCommissionOnOtherAmount,
    companyCommissionOnBasicFreight: t.companyCommissionOnBasicFreight + r.companyCommissionOnBasicFreight,
    totalCommission: t.totalCommission + r.totalCommission
  }), {
    shipmentCount: 0, totalNetAmount: 0, commissionOnBasicFreight: 0,
    branchCommissionOnOtherAmount: 0, companyCommissionOnBasicFreight: 0, totalCommission: 0
  }));

  protected readonly detailColumns: TableColumn<Shipment>[] = [
    { key: 'shipmentNumber', header: 'Shipment No.', sortable: true },
    { key: 'trackingNumber', header: 'AWB' },
    { key: 'bookingDate', header: 'Booking Date', sortable: true },
    { key: 'bookingBranchId', header: 'Booking Branch' },
    { key: 'netAmount', header: 'Amount', align: 'right' },
    { key: 'commissionOnBasicFreight', header: 'Commission on Basic Freight', align: 'right' },
    { key: 'branchCommissionOnOtherAmount', header: 'Branch Commission on Other Amount', align: 'right' },
    { key: 'companyCommissionOnBasicFreight', header: 'Company Commission on Basic Freight', align: 'right' },
    { key: 'totalCommission', header: 'Total Commission', align: 'right' }
  ];

  private query: PageQuery = { page: 0, size: 20, sort: 'bookingDate,desc' };
  private readonly filters = signal<ShipmentSearchRequest>({});
  protected readonly activeFilters = computed(() =>
    Object.values(this.filters()).filter((v) => v != null && (!Array.isArray(v) || v.length)).length);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Reports' }, { label: 'Commission Report' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.load();
    this.loadSummary();
  }

  /** Shared by the detail list and the branch summary, same branch-lock every report uses. */
  private filterRequest(): ShipmentSearchRequest {
    const f = this.filters();
    return {
      bookingBranchId: this.myBranchId ?? f.bookingBranchId,
      bookingDateFrom: f.bookingDateFrom, bookingDateTo: f.bookingDateTo
    };
  }

  private buildQuery(size?: number): PageQuery {
    const f = this.filterRequest();
    return {
      ...this.query, ...(size ? { size, page: 0 } : {}),
      bookingBranchId: f.bookingBranchId, bookingDateFrom: f.bookingDateFrom, bookingDateTo: f.bookingDateTo
    };
  }

  private load(): void {
    this.loading.set(true);
    this.service.list(this.buildQuery()).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  private loadSummary(): void {
    this.summaryLoading.set(true);
    this.service.commissionSummary(this.filterRequest()).subscribe({
      next: (rows) => { this.summary.set(rows); this.summaryLoading.set(false); },
      error: () => this.summaryLoading.set(false)
    });
  }

  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.query = { ...this.query, sort: `${s.active},${s.direction}`, page: 0 }; this.load(); }
  onFilter(f: ShipmentSearchRequest) {
    this.filters.set(f); this.query = { ...this.query, page: 0 }; this.filterOpen.set(false);
    this.load(); this.loadSummary();
  }

  view(s: Shipment): void { this.router.navigate(['/shipments', s.id]); }

  protected branchLabel(id: string): string { return this.branchOptions().find((o) => o.value === id)?.label ?? '—'; }

  exportSummaryCsv(): void {
    this.exporting.set(true);
    this.service.commissionSummary(this.filterRequest()).subscribe({
      next: (rows) => { this.download(rows); this.exporting.set(false); },
      error: () => { this.exporting.set(false); this.notify.error('Export failed.'); }
    });
  }

  private download(rows: BranchCommissionSummary[]): void {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const header = ['branch', 'shipmentCount', 'totalNetAmount', 'commissionOnBasicFreight',
      'branchCommissionOnOtherAmount', 'companyCommissionOnBasicFreight', 'totalCommission'];
    const line = (r: BranchCommissionSummary) => [this.branchLabel(r.bookingBranchId), r.shipmentCount,
      r.totalNetAmount, r.commissionOnBasicFreight, r.branchCommissionOnOtherAmount,
      r.companyCommissionOnBasicFreight, r.totalCommission].map(esc).join(',');
    const csv = [header.join(','), ...rows.map(line)].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url; a.download = `commission-report-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click(); URL.revokeObjectURL(url);
    this.notify.info(`Exported ${rows.length} branch row(s).`);
  }
}
