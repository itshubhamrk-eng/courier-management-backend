import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import {
  WalletTransaction, RechargeMode, RECHARGE_MODE_SUB_TYPES, rechargeModeOf, formatMoney, prettyToken
} from '@core/models/wallet.model';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { SortState, TableColumn, UiTable } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { downloadCsv } from '@shared/utils/csv-export.util';
import { BranchWalletService } from './branch-wallet.service';

const VIEWERS = [AppRole.COMPANY_ADMIN, AppRole.FINANCE_USER];

const MODE_TABS: { value: RechargeMode | null; label: string }[] = [
  { value: null, label: 'All' }, { value: 'ONLINE', label: 'Razorpay' }, { value: 'MANUAL', label: 'Manual' }
];

/**
 * Wallet Recharge Report — company-wide, across every branch: every `WRC` (Razorpay) and
 * `MCR` (manual credit) entry side by side, each row carrying its branch. `COMPANY_ADMIN`/
 * `FINANCE_USER` only, same gate as `GET /branch-wallet/recharge-report`. Server pagination,
 * sort, debounced search, a Manual/Razorpay toggle, a date range and CSV export.
 */
@Component({
  selector: 'app-wallet-recharge-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, UiTable, UiPagination, UiSearch, UiButton, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div>
          <h1 class="text-h1">Wallet Recharge Report</h1>
          <p class="text-caption">Every branch, manual and Razorpay recharges — {{ page().totalElements }} entries.</p>
        </div>
        <div class="page__actions">
          <app-search placeholder="Search transaction no, payment ref…" (changed)="onSearch($event)" />
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportCsv()">Export</app-button>
        </div>
      </header>

      <div class="toolbar">
        <div class="tabs" role="tablist">
          @for (t of tabs; track t.label) {
            <button type="button" role="tab" [attr.aria-selected]="mode() === t.value"
                    [class.tab--active]="mode() === t.value" (click)="onMode(t.value)">{{ t.label }}</button>
          }
        </div>
        <label class="dt"><span class="dt__l">From</span>
          <input class="dt__i" type="date" [value]="fromDate()" (change)="onFrom($event)" /></label>
        <label class="dt"><span class="dt__l">To</span>
          <input class="dt__i" type="date" [value]="toDate()" (change)="onTo($event)" /></label>
      </div>

      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [sort]="sort()"
                 [startIndex]="page().page * page().size"
                 emptyTitle="No recharges" emptyHint="A manual credit or a settled Razorpay recharge will appear here."
                 (sortChange)="onSort($event)">
        <ng-template #row let-t>
          <td><div class="tc">{{ t.createdAt | date:'dd MMM y' }}</div>
              <div class="tc__sub">{{ t.createdAt | date:'HH:mm' }}</div></td>
          <td>{{ t.branchName || '—' }}<div class="tc__sub">{{ t.branchCode }}</div></td>
          <td><span class="mono">{{ t.transactionNo }}</span></td>
          <td>
            @if (modeOf(t.subTransactionType); as m) {
              <app-status-badge [value]="m" [label]="m === 'ONLINE' ? 'Razorpay' : 'Manual'"
                                 [tone]="m === 'ONLINE' ? 'info' : 'neutral'" />
            }
          </td>
          <td class="amt amt--cr">+{{ money(t.amount) }}</td>
          <td>{{ t.paymentGateway || '—' }}</td>
          <td><span class="mono">{{ t.paymentReference || '—' }}</span></td>
          <td>
            @if (t.paymentStatus) { <app-status-badge [value]="t.paymentStatus" [label]="pretty(t.paymentStatus)" /> }
            @else { <span class="dash">Settled</span> }
          </td>
          <td>{{ t.createdByName || '—' }}</td>
        </ng-template>
      </app-table>

      <app-pagination [page]="page()" (pageChange)="onPage($event)" />
    </div>
  `,
  styles: [`
    .toolbar { display:flex; align-items:center; gap:16px; flex-wrap:wrap; margin:4px 0 16px; }
    .tabs { display:inline-flex; background:var(--surface-muted); border-radius:var(--r-pill); padding:3px; gap:2px; }
    .tabs button { border:0; background:transparent; padding:7px 16px; border-radius:var(--r-pill);
      font:600 13px var(--font-sans); color:var(--content-muted); cursor:pointer; }
    .tab--active { background:var(--surface); color:var(--content-fg); box-shadow:var(--shadow-clay); }
    .dt { display:flex; align-items:center; gap:6px; }
    .dt__l { font:500 13px var(--font-sans); color:var(--content-muted); }
    .dt__i { height:36px; padding:0 10px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 13px var(--font-sans); color:var(--content-fg); }
    .tc { font:600 13px var(--font-sans); }
    .tc__sub { font:400 12px var(--font-sans); color:var(--content-muted); }
    .mono { font:600 13px var(--font-mono, ui-monospace); color:var(--content-fg); }
    .amt { font:700 14px var(--font-mono, ui-monospace); white-space:nowrap; }
    .amt--cr { color:var(--success); }
    .dash { color:var(--content-muted); }
  `]
})
export class WalletRechargeReport implements OnInit {
  private readonly service = inject(BranchWalletService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly router = inject(Router);

  readonly tabs = MODE_TABS;
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly page = signal<Page<WalletTransaction>>(emptyPage<WalletTransaction>());
  readonly sort = signal<SortState | null>({ active: 'createdAt', direction: 'desc' });
  readonly mode = signal<RechargeMode | null>(null);
  readonly fromDate = signal<string>('');
  readonly toDate = signal<string>('');

  private query: PageQuery = { page: 0, size: 20, sort: 'createdAt,desc' };

  readonly columns: TableColumn<WalletTransaction>[] = [
    { key: 'createdAt', header: 'Date', sortable: true },
    { key: 'branchName', header: 'Branch' },
    { key: 'transactionNo', header: 'Transaction No', sortable: true },
    { key: 'subTransactionType', header: 'Mode', width: '110px' },
    { key: 'amount', header: 'Amount', sortable: true, align: 'left' },
    { key: 'paymentGateway', header: 'Gateway' },
    { key: 'paymentReference', header: 'Payment Ref' },
    { key: 'paymentStatus', header: 'Status', width: '110px' },
    { key: 'createdByName', header: 'Recorded By' }
  ];

  ngOnInit(): void {
    if (!this.perms.canAccess({ roles: VIEWERS, permissions: ['WALLET_READ', 'WALLET_SEARCH'] })) {
      this.router.navigate(['/unauthorized']); return;
    }
    this.breadcrumb.set([{ label: 'Finance' }, { label: 'Branch Wallet', route: '/finance/branch-wallet' },
      { label: 'Recharge Report' }]);
    this.load();
  }

  private buildQuery(size?: number): PageQuery {
    return {
      ...this.query, ...(size ? { size, page: 0 } : {}),
      ...(this.mode() ? { subTransactionType: RECHARGE_MODE_SUB_TYPES[this.mode()!] } : {}),
      ...(this.fromDate() ? { fromDate: this.fromDate() } : {}),
      ...(this.toDate() ? { toDate: this.toDate() } : {})
    };
  }

  private load(): void {
    this.loading.set(true);
    this.service.rechargeReport(this.buildQuery()).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onSearch(t: string) { this.query = { ...this.query, search: t || undefined, page: 0 }; this.load(); }
  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.query = { ...this.query, sort: `${s.active},${s.direction}`, page: 0 }; this.load(); }
  onMode(m: RechargeMode | null) { this.mode.set(m); this.query = { ...this.query, page: 0 }; this.load(); }
  onFrom(e: Event) { this.fromDate.set((e.target as HTMLInputElement).value); this.query = { ...this.query, page: 0 }; this.load(); }
  onTo(e: Event) { this.toDate.set((e.target as HTMLInputElement).value); this.query = { ...this.query, page: 0 }; this.load(); }

  modeOf(sub: WalletTransaction['subTransactionType']): RechargeMode | null { return rechargeModeOf(sub); }
  pretty(v: string): string { return prettyToken(v); }
  money(n: number): string { return formatMoney(n); }

  exportCsv(): void {
    this.exporting.set(true);
    this.service.rechargeReport(this.buildQuery(200)).subscribe({
      next: (p) => { this.download(p.content); this.exporting.set(false); },
      error: () => { this.exporting.set(false); this.notify.error('Export failed.'); }
    });
  }

  private download(rows: WalletTransaction[]): void {
    const header = ['createdAt', 'branchCode', 'branchName', 'transactionNo', 'mode', 'amount',
      'paymentGateway', 'paymentReference', 'paymentStatus', 'recordedBy'];
    const lines = rows.map((t) => [
      t.createdAt, t.branchCode ?? '', t.branchName ?? '', t.transactionNo,
      this.modeOf(t.subTransactionType) ?? '', t.amount,
      t.paymentGateway ?? '', t.paymentReference ?? '', t.paymentStatus ?? '', t.createdByName ?? ''
    ]);
    // amount is column index 5 — the one figure a recharge report totals.
    downloadCsv(`wallet-recharge-report-${new Date().toISOString().slice(0, 10)}.csv`, header, lines, [5]);
    this.notify.info(`Exported ${rows.length} recharge(s).`);
  }
}
