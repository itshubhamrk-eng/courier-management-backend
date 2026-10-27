import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { CommissionLedgerRow } from '@core/models/shipment.model';
import { TableColumn, UiTable } from '@shared/components/ui-table/ui-table';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentService } from '../shipment/shipment.service';
import { excludingCancelled } from './non-cancelled.util';
import { downloadCsv } from '@shared/utils/csv-export.util';
import { currentMonth, monthLabel, monthRange } from './commission-month.util';

type Filter = 'ALL' | 'CREDITED' | 'PENDING';

/**
 * Commission Credit Report — per shipment, the booking branch's commission and whether it has
 * been credited to that branch's wallet (reference = shipment number) or is still pending, with
 * the scan it is waiting on. `GET /shipments/commission-ledger` over the month's booking dates.
 */
@Component({
  selector: 'app-commission-credit-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, DatePipe, UiTable, UiButton],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Commission Credit Report</h1>
          <p class="text-caption">Commission credited to the booking branch wallet, and what is still pending, for {{ label() }}.</p></div>
        <div class="page__actions">
          <label class="month">Month
            <input type="month" [value]="month()" [max]="maxMonth" (change)="onMonth($any($event.target).value)" />
          </label>
          <label class="month">Show
            <select [value]="filter()" (change)="filter.set($any($event.target).value)">
              <option value="ALL">All</option>
              <option value="CREDITED">Credited</option>
              <option value="PENDING">Pending</option>
            </select>
          </label>
          <app-button variant="stroked" icon="download" (pressed)="exportCsv()">Export</app-button>
        </div>
      </header>

      <div class="stats">
        <div class="stat"><span class="stat__l">Credited</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().credited | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Pending</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().pending | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Shipments</span><span class="stat__v">{{ loading() ? '—' : visible().length }}</span></div>
      </div>

      <app-table [columns]="columns" [rows]="visible()" [loading]="loading()"
                 emptyTitle="No commission" emptyHint="No commission-bearing shipments in this month.">
        <ng-template #row let-r>
          <td>{{ r.shipmentNumber }}</td>
          <td>{{ branchLabel(r.bookingBranchId) }}</td>
          <td>{{ r.paymentModeName }}</td>
          <td>{{ r.status }}</td>
          <td class="num strong">₹{{ r.commission | number: '1.2-2' }}</td>
          <td><span [class]="r.credited ? 'tag tag--ok' : 'tag tag--wait'">{{ r.credited ? 'Credited' : 'Pending' }}</span></td>
          <td>{{ r.credited ? (r.creditedAt | date: 'dd MMM yyyy, HH:mm') : ('Awaiting: ' + r.awaiting) }}</td>
          @if (isAdmin) {
            <td>
              @if (!r.credited) {
                <app-button variant="stroked" [loading]="crediting() === r.shipmentId"
                            (pressed)="creditNow(r)">Credit now</app-button>
              }
            </td>
          }
        </ng-template>
      </app-table>
    </div>
  `,
  styles: [`
    .page__head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; flex-wrap:wrap; margin-bottom:12px; }
    .page__actions { display:flex; align-items:flex-end; gap:10px; flex-wrap:wrap; }
    .month { display:flex; flex-direction:column; gap:2px; font:500 12px var(--font-sans); color:var(--content-muted); }
    .month input, .month select { padding:7px 10px; border:1px solid var(--surface-border); border-radius:var(--r-field);
      background:var(--surface); color:var(--content-fg); font:500 14px var(--font-sans); }
    .stats { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:20px; }
    .stat { display:flex; flex-direction:column; gap:4px; padding:12px 18px; min-width:150px;
      background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .stat__l { font:500 12px var(--font-sans); color:var(--content-muted); }
    .stat__v { font:700 20px var(--font-sans); color:var(--content-fg); }
    .num { text-align:right; }
    .strong { font-weight:700; }
    .tag { padding:2px 8px; border-radius:10px; font:600 12px var(--font-sans); }
    .tag--ok { background:#e6f4ea; color:#137333; }
    .tag--wait { background:#fef3e0; color:#b45309; }
  `]
})
export class CommissionCreditReport implements OnInit {
  private readonly service = inject(ShipmentService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly dialogs = inject(DialogService);

  /** Crediting ahead of the scan is a company-admin override — see ShipmentServiceImpl.creditPendingCommission. */
  protected readonly isAdmin = this.auth.hasAnyRole(['COMPANY_ADMIN']);
  protected readonly crediting = signal<string | null>(null);

  private readonly myBranchId = this.auth.user()?.branchId ?? null;
  protected readonly maxMonth = currentMonth();
  protected readonly month = signal(currentMonth());
  protected readonly label = computed(() => monthLabel(this.month()));
  protected readonly filter = signal<Filter>('ALL');
  protected readonly branchOptions = signal<SelectOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly rows = signal<CommissionLedgerRow[]>([]);
  protected readonly visible = computed(() => {
    const f = this.filter();
    return this.rows().filter((r) => f === 'ALL' || (f === 'CREDITED') === r.credited);
  });
  protected readonly totals = computed(() => this.rows().reduce((t, r) => ({
    credited: t.credited + (r.credited ? r.commission : 0),
    pending: t.pending + (r.credited ? 0 : r.commission)
  }), { credited: 0, pending: 0 }));

  protected readonly columns: TableColumn<CommissionLedgerRow>[] = [
    { key: 'shipmentNumber', header: 'Shipment' },
    { key: 'bookingBranchId', header: 'Booking Branch' },
    { key: 'paymentModeName', header: 'Payment' },
    { key: 'status', header: 'Status' },
    { key: 'commission', header: 'Commission', align: 'right' },
    { key: 'credited', header: 'State' },
    { key: 'creditedAt', header: 'Credited At / Waiting For' },
    ...(this.isAdmin ? [{ key: 'action', header: '' }] : [])
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Reports' }, { label: 'Commission Credit Report' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.load();
  }

  private load(): void {
    const { from, to } = monthRange(this.month());
    this.loading.set(true);
    this.service.commissionLedger({
      status: excludingCancelled(undefined),
      bookingBranchId: this.myBranchId ?? undefined,
      bookingDateFrom: from, bookingDateTo: to
    }).subscribe({
      next: (rows) => { this.rows.set(rows); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  protected creditNow(r: CommissionLedgerRow): void {
    this.dialogs.confirm({
      title: 'Credit commission now',
      message: `Credit ₹${r.commission.toFixed(2)} to ${this.branchLabel(r.bookingBranchId)}'s wallet for ${r.shipmentNumber}, `
        + `without waiting for ${r.awaiting?.toLowerCase() ?? 'the scan'}?`,
      confirmLabel: 'Credit'
    }).subscribe((ok) => {
      if (!ok) { return; }
      this.crediting.set(r.shipmentId);
      this.service.creditCommission(r.shipmentId).subscribe({
        next: () => { this.crediting.set(null); this.notify.success('Commission credited.'); this.load(); },
        error: (e) => { this.crediting.set(null); this.notify.error(e?.error?.message ?? 'Could not credit the commission.'); }
      });
    });
  }

  protected onMonth(value: string): void {
    if (!value) { return; }
    this.month.set(value);
    this.load();
  }

  protected branchLabel(id: string): string { return this.branchOptions().find((o) => o.value === id)?.label ?? '—'; }

  protected exportCsv(): void {
    const rows = this.visible();
    downloadCsv(`commission-credit-${this.month()}.csv`,
      ['shipment', 'branch', 'payment', 'status', 'commission', 'state', 'creditedAt', 'awaiting'],
      rows.map((r) => [r.shipmentNumber, this.branchLabel(r.bookingBranchId), r.paymentModeName, r.status,
        r.commission, r.credited ? 'Credited' : 'Pending', r.creditedAt ?? '', r.awaiting ?? '']), [4]);
    this.notify.info(`Exported ${rows.length} row(s).`);
  }
}
