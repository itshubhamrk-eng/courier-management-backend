import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { RechargeAttempt, RechargeAttemptStatus, formatMoney } from '@core/models/wallet.model';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { SortState, TableColumn, UiTable } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { downloadCsv } from '@shared/utils/csv-export.util';
import { BranchWalletService } from './branch-wallet.service';

const VIEWERS = [AppRole.COMPANY_ADMIN, AppRole.FINANCE_USER];

const STATUS_TABS: { value: RechargeAttemptStatus | null; label: string }[] = [
  { value: null, label: 'All' }, { value: 'PENDING', label: 'Pending' },
  { value: 'SUCCESS', label: 'Success' }, { value: 'FAILED', label: 'Failed' }
];

/**
 * Recharge Attempts — every online (Razorpay) recharge attempt across every branch, whatever
 * became of it: `PENDING` (waiting on the gateway / the scheduler), `SUCCESS` (wallet credited)
 * or `FAILED` (with the reason). The Recharge Report next to it lists only credited recharges.
 * `COMPANY_ADMIN`/`FINANCE_USER`, same gate as `GET /branch-wallet/recharge-attempts`.
 */
@Component({
  selector: 'app-wallet-recharge-attempts',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, UiTable, UiPagination, UiSearch, UiButton, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div>
          @if (!embedded()) { <h1 class="text-h1">Recharge Attempts</h1> }
          <p class="text-caption">Online recharges by outcome — {{ page().totalElements }} attempts. Pending ones are re-checked with the gateway every 2 minutes.</p>
        </div>
        <div class="page__actions">
          <app-search placeholder="Search transaction no, order or payment id…" (changed)="onSearch($event)" />
          <app-button variant="stroked" icon="refresh" (pressed)="reload()">Refresh</app-button>
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportCsv()">Export</app-button>
        </div>
      </header>

      <div class="toolbar">
        <div class="tabs" role="tablist">
          @for (t of tabs; track t.label) {
            <button type="button" role="tab" [attr.aria-selected]="status() === t.value"
                    [class.tab--active]="status() === t.value" (click)="onStatus(t.value)">{{ t.label }}</button>
          }
        </div>
        <label class="dt"><span class="dt__l">From</span>
          <input class="dt__i" type="date" [value]="fromDate()" (change)="onFrom($event)" /></label>
        <label class="dt"><span class="dt__l">To</span>
          <input class="dt__i" type="date" [value]="toDate()" (change)="onTo($event)" /></label>
      </div>

      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [sort]="sort()"
                 [startIndex]="page().page * page().size"
                 emptyTitle="No recharge attempts" emptyHint="An online recharge started from Branch Wallet will appear here."
                 (sortChange)="onSort($event)">
        <ng-template #row let-a>
          <td><div class="tc">{{ a.createdAt | date:'dd MMM y' }}</div>
              <div class="tc__sub">{{ a.createdAt | date:'HH:mm:ss' }}</div></td>
          <td>{{ a.branchName || '—' }}<div class="tc__sub">{{ a.branchCode }}</div></td>
          <td><span class="mono">{{ a.transactionNo }}</span></td>
          <td class="amt">{{ money(a.amount) }}</td>
          <td><app-status-badge [value]="a.status" [label]="label(a.status)" /></td>
          <td>
            @if (a.failureReason) { <span class="reason">{{ a.failureReason }}</span> }
            @else if (a.status === 'PENDING') { <span class="dash">Waiting for the gateway</span> }
            @else { <span class="dash">—</span> }
          </td>
          <td><span class="mono">{{ a.gatewayOrderId || '—' }}</span></td>
          <td><span class="mono">{{ a.gatewayPaymentId || '—' }}</span></td>
          <td>
            @if (a.settledAt) { {{ a.settledAt | date:'dd MMM, HH:mm' }} }
            @else if (a.lastCheckedAt) { <span class="dash">checked {{ a.lastCheckedAt | date:'HH:mm' }}</span> }
            @else { <span class="dash">—</span> }
          </td>
          <td>
            @if (a.status === 'PENDING') {
              <app-button variant="stroked" icon="sync" [loading]="checkingId() === a.id"
                          [disabled]="checkingId() !== null" (pressed)="check(a)">Check status</app-button>
            }
          </td>
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
    .mono { font:600 12px var(--font-mono, ui-monospace); color:var(--content-fg); word-break:break-all; }
    .amt { font:700 14px var(--font-mono, ui-monospace); white-space:nowrap; }
    .reason { font:400 13px var(--font-sans); color:var(--danger); }
    .dash { color:var(--content-muted); font:400 13px var(--font-sans); }
  `]
})
export class WalletRechargeAttempts implements OnInit {
  private readonly service = inject(BranchWalletService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly router = inject(Router);

  /** True when shown inside the Recharge Report, which owns the title, breadcrumb and access check. */
  readonly embedded = input(false);

  readonly tabs = STATUS_TABS;
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly checkingId = signal<string | null>(null);
  readonly page = signal<Page<RechargeAttempt>>(emptyPage<RechargeAttempt>());
  readonly sort = signal<SortState | null>({ active: 'createdAt', direction: 'desc' });
  readonly status = signal<RechargeAttemptStatus | null>(null);
  readonly fromDate = signal<string>('');
  readonly toDate = signal<string>('');

  private query: PageQuery = { page: 0, size: 20, sort: 'createdAt,desc' };

  readonly columns: TableColumn<RechargeAttempt>[] = [
    { key: 'createdAt', header: 'Started', sortable: true },
    { key: 'branchName', header: 'Branch' },
    { key: 'transactionNo', header: 'Transaction No', sortable: true },
    { key: 'amount', header: 'Amount', sortable: true, align: 'left' },
    { key: 'status', header: 'Status', sortable: true, width: '110px' },
    { key: 'failureReason', header: 'Reason' },
    { key: 'gatewayOrderId', header: 'Gateway Order' },
    { key: 'gatewayPaymentId', header: 'Payment Id' },
    { key: 'settledAt', header: 'Settled / Checked' },
    { key: 'status', header: '', width: '150px' }
  ];

  ngOnInit(): void {
    if (this.embedded()) { this.load(); return; }
    if (!this.perms.canAccess({ roles: VIEWERS, permissions: ['WALLET_READ', 'WALLET_SEARCH'] })) {
      this.router.navigate(['/unauthorized']); return;
    }
    this.breadcrumb.set([{ label: 'Finance' }, { label: 'Branch Wallet', route: '/finance/branch-wallet' },
      { label: 'Recharge Attempts' }]);
    this.load();
  }

  private buildQuery(size?: number): PageQuery {
    return {
      ...this.query, ...(size ? { size, page: 0 } : {}),
      ...(this.status() ? { status: this.status()! } : {}),
      ...(this.fromDate() ? { fromDate: this.fromDate() } : {}),
      ...(this.toDate() ? { toDate: this.toDate() } : {})
    };
  }

  private load(): void {
    this.loading.set(true);
    this.service.rechargeAttempts(this.buildQuery()).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  reload() { this.load(); }

  /** Asks the gateway about one pending attempt right now, then reports what became of it. */
  check(a: RechargeAttempt): void {
    this.checkingId.set(a.id);
    this.service.checkRechargeAttempt(a.id).subscribe({
      next: (r) => {
        this.checkingId.set(null);
        this.page.update((p) => ({ ...p, content: p.content.map((x) => (x.id === r.id ? r : x)) }));
        if (r.status === 'SUCCESS') this.notify.success(`Payment received — ${this.money(r.amount)} credited to the wallet.`);
        else if (r.status === 'FAILED') this.notify.error(r.failureReason ?? 'The payment failed.');
        else this.notify.info('Still pending — the gateway has no completed payment for it yet.');
      },
      error: (e) => { this.checkingId.set(null); this.notify.error(e?.error?.message ?? 'Could not check the payment.'); }
    });
  }
  onSearch(t: string) { this.query = { ...this.query, search: t || undefined, page: 0 }; this.load(); }
  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.query = { ...this.query, sort: `${s.active},${s.direction}`, page: 0 }; this.load(); }
  onStatus(s: RechargeAttemptStatus | null) { this.status.set(s); this.query = { ...this.query, page: 0 }; this.load(); }
  onFrom(e: Event) { this.fromDate.set((e.target as HTMLInputElement).value); this.query = { ...this.query, page: 0 }; this.load(); }
  onTo(e: Event) { this.toDate.set((e.target as HTMLInputElement).value); this.query = { ...this.query, page: 0 }; this.load(); }

  label(s: RechargeAttemptStatus): string { return s.charAt(0) + s.slice(1).toLowerCase(); }
  money(n: number): string { return formatMoney(n); }

  exportCsv(): void {
    this.exporting.set(true);
    this.service.rechargeAttempts(this.buildQuery(200)).subscribe({
      next: (p) => { this.download(p.content); this.exporting.set(false); },
      error: () => { this.exporting.set(false); this.notify.error('Export failed.'); }
    });
  }

  private download(rows: RechargeAttempt[]): void {
    const header = ['createdAt', 'branchCode', 'branchName', 'transactionNo', 'amount', 'status',
      'failureReason', 'gatewayOrderId', 'gatewayPaymentId', 'settledAt', 'lastCheckedAt'];
    const lines = rows.map((a) => [
      a.createdAt, a.branchCode ?? '', a.branchName ?? '', a.transactionNo, a.amount, a.status,
      a.failureReason ?? '', a.gatewayOrderId ?? '', a.gatewayPaymentId ?? '', a.settledAt ?? '', a.lastCheckedAt ?? ''
    ]);
    // amount is column index 4.
    downloadCsv(`recharge-attempts-${new Date().toISOString().slice(0, 10)}.csv`, header, lines, [4]);
    this.notify.info(`Exported ${rows.length} attempt(s).`);
  }
}
