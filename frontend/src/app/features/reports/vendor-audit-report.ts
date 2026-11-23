import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { VendorAuditRow, ShipmentSearchRequest, BranchSendStatus, SendOutcome } from '@core/models/shipment.model';
import { TableColumn } from '@shared/components/ui-table/ui-table';
import { UiTable } from '@shared/components/ui-table/ui-table';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentService } from '../shipment/shipment.service';
import { excludingCancelled } from './non-cancelled.util';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { apiMessage } from '@features/communication/api-error';
import { downloadCsv } from '@shared/utils/csv-export.util';

/**
 * Vendor Audit Report — one row per company branch (booking-side or delivery-side), the
 * reconciliation table a franchise/vendor audit reviews: paid/to-pay volumes and amounts,
 * booking and delivery (DRS) commission, ODA charges and other charges.
 * Company-level by design — unlike every other report in this module, it is deliberately
 * not locked to the caller's own branch, since an audit needs every branch side by side.
 * `GET /shipments/vendor-audit`, already grouped server-side — same "unpaged aggregate,
 * single call" shape every other report uses. See backend `VendorAuditRow` for the one
 * known limitation on the delivery-commission figures (recomputed at current DRS rate).
 */
@Component({
  selector: 'app-vendor-audit-report',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, UiTable, UiButton],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Vendor Audit Report</h1>
          <p class="text-caption">{{ rows().length }} branch(es) across the company.</p></div>
        <div class="page__actions">
          <label class="dfld">From <input type="date" [value]="from()" (change)="onFrom($event)" /></label>
          <label class="dfld">To <input type="date" [value]="to()" (change)="onTo($event)" /></label>
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportCsv()">Export</app-button>
          <span [title]="datesSet() ? '' : dateHint">
            <app-button variant="stroked" icon="send" [loading]="sendingAll()"
                        [disabled]="!datesSet()" (pressed)="sendAll()">Send to all branches</app-button>
          </span>
        </div>
      </header>

      <div class="stats">
        <div class="stat"><span class="stat__l">Total Booked</span><span class="stat__v">{{ loading() ? '—' : totals().totalBookedOrderCount }}</span></div>
        <div class="stat"><span class="stat__l">Total Delivered</span><span class="stat__v">{{ loading() ? '—' : totals().totalDeliveredOrderCount }}</span></div>
        <div class="stat"><span class="stat__l">Paid Orders</span><span class="stat__v">{{ loading() ? '—' : totals().paidOrderCount }}</span></div>
        <div class="stat"><span class="stat__l">To-Pay Orders</span><span class="stat__v">{{ loading() ? '—' : totals().topayOrderCount }}</span></div>
        <div class="stat"><span class="stat__l">Booking Commission</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().bookingTotalCommission | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Delivery Commission</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().deliveryTotalCommission | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">ODA Charges</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().odaCharges | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Other Charges</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().otherCharges | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Total Recharged</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().rechargeAmount | number: '1.2-2')) }}</span></div>
        <div class="stat"><span class="stat__l">Total DRS Credited</span><span class="stat__v">{{ loading() ? '—' : ('₹' + (totals().drsChargeCredited | number: '1.2-2')) }}</span></div>
      </div>

      <div class="twrap">
        <app-table [columns]="columns" [rows]="rows()" [loading]="loading()"
                   emptyTitle="No shipments" emptyHint="Nothing matches this date range yet.">
          <ng-template #row let-r>
            <td>{{ branchLabel(r.branchId) }}</td>
            <td class="num">{{ r.paidOrderCount }}</td>
            <td class="num">{{ r.paidOrderQuantity }}</td>
            <td class="num">₹{{ r.paidOrderAmount | number: '1.2-2' }}</td>
            <td class="num">{{ r.topayOrderCount }}</td>
            <td class="num">{{ r.topayOrderQuantity }}</td>
            <td class="num">₹{{ r.topayOrderAmount | number: '1.2-2' }}</td>
            <td class="num">₹{{ r.paidCommission | number: '1.2-2' }}</td>
            <td class="num">₹{{ r.deliveryCommission | number: '1.2-2' }}</td>
            <td class="num">{{ r.totalBookedOrderCount }}</td>
            <td class="num">{{ r.totalDeliveredOrderCount }}</td>
            <td class="num strong">₹{{ r.bookingTotalCommission | number: '1.2-2' }}</td>
            <td class="num strong">₹{{ r.deliveryTotalCommission | number: '1.2-2' }}</td>
            <td class="num">₹{{ r.odaCharges | number: '1.2-2' }}</td>
            <td class="num">₹{{ r.otherCharges | number: '1.2-2' }}</td>
            <td class="num">{{ r.openingBalance === null ? '—' : ('₹' + (r.openingBalance | number: '1.2-2')) }}</td>
            <td class="num">{{ r.rechargeCount === null ? '—' : r.rechargeCount }}</td>
            <td class="num">{{ r.drsChargeCredited === null ? '—' : ('₹' + (r.drsChargeCredited | number: '1.2-2')) }}</td>
            <td class="num">{{ r.closingBalance === null ? '—' : ('₹' + (r.closingBalance | number: '1.2-2')) }}</td>
            <td>{{ statusText(r.branchId) }}</td>
            <td class="acts">
              <span [title]="datesSet() ? '' : dateHint">
                <app-button variant="text" icon="picture_as_pdf" [disabled]="!datesSet() || isBusy(r.branchId)"
                            (pressed)="downloadPdf(r)">Download PDF</app-button>
              </span>
              <span [title]="datesSet() ? '' : dateHint">
                <app-button variant="text" icon="send" [disabled]="!datesSet() || isBusy(r.branchId) || noPhone(r.branchId)"
                            (pressed)="send(r)">Send on WhatsApp</app-button>
              </span>
              @if (noPhone(r.branchId)) { <span class="text-caption">No phone</span> }
            </td>
          </ng-template>
        </app-table>
      </div>
    </div>
  `,
  styles: [`
    .page__head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; flex-wrap:wrap; margin-bottom:12px; }
    .page__actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
    .dfld { display:flex; align-items:center; gap:6px; font:500 16px var(--font-sans); color:var(--content-fg); }
    .dfld input { height:38px; padding:0 10px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 16px var(--font-sans); color:var(--content-fg); }
    .stats { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:14px; }
    .stat { display:flex; flex-direction:column; gap:4px; padding:12px 18px; min-width:140px;
      background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .stat__l { font:500 15px var(--font-sans); color:var(--content-muted); }
    .stat__v { font:700 23px var(--font-sans); color:var(--content-fg); }
    .twrap { overflow-x:auto; }
    .num { text-align:right; }
    .acts { white-space:nowrap; }
    .strong { font-weight:700; }
  `]
})
export class VendorAuditReport implements OnInit {
  private readonly service = inject(ShipmentService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly dialogs = inject(DialogService);

  protected readonly dateHint = 'Pick a From and To date to send reports.';
  protected readonly sendStatuses = signal<BranchSendStatus[]>([]);
  protected readonly statusById = computed(() =>
    Object.fromEntries(this.sendStatuses().map((s) => [s.branchId, s])) as Record<string, BranchSendStatus>);
  protected readonly busyIds = signal<ReadonlySet<string>>(new Set());
  protected readonly sendingAll = signal(false);

  protected readonly branchOptions = signal<SelectOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly exporting = signal(false);
  protected readonly rows = signal<VendorAuditRow[]>([]);
  protected readonly from = signal('');
  protected readonly to = signal('');

  protected readonly columns: TableColumn<VendorAuditRow>[] = [
    { key: 'branchId', header: 'Branch' },
    { key: 'paidOrderCount', header: 'Paid Count', align: 'right' },
    { key: 'paidOrderQuantity', header: 'Paid Qty', align: 'right' },
    { key: 'paidOrderAmount', header: 'Paid Amount', align: 'right' },
    { key: 'topayOrderCount', header: 'To-Pay Count', align: 'right' },
    { key: 'topayOrderQuantity', header: 'To-Pay Qty', align: 'right' },
    { key: 'topayOrderAmount', header: 'To-Pay Amount', align: 'right' },
    { key: 'paidCommission', header: 'Commission on Paid', align: 'right' },
    { key: 'deliveryCommission', header: 'Commission on Delivered', align: 'right' },
    { key: 'totalBookedOrderCount', header: 'Total Booked', align: 'right' },
    { key: 'totalDeliveredOrderCount', header: 'Total Delivered', align: 'right' },
    { key: 'bookingTotalCommission', header: 'Booking Total Commission', align: 'right' },
    { key: 'deliveryTotalCommission', header: 'Delivery Total Commission', align: 'right' },
    { key: 'odaCharges', header: 'ODA Charges', align: 'right' },
    { key: 'otherCharges', header: 'Other Charges', align: 'right' },
    { key: 'openingBalance', header: 'Opening Balance', align: 'right' },
    { key: 'rechargeCount', header: 'Recharges', align: 'right' },
    { key: 'drsChargeCredited', header: 'DRS Charge Credited', align: 'right' },
    { key: 'closingBalance', header: 'Closing Balance', align: 'right' },
    { key: 'whatsapp', header: 'WhatsApp' },
    { key: 'report', header: 'Report' }
  ];

  protected readonly datesSet = computed(() => !!this.from() && !!this.to());

  protected readonly totals = computed(() => this.rows().reduce((t, r) => ({
    paidOrderCount: t.paidOrderCount + r.paidOrderCount,
    topayOrderCount: t.topayOrderCount + r.topayOrderCount,
    totalBookedOrderCount: t.totalBookedOrderCount + r.totalBookedOrderCount,
    totalDeliveredOrderCount: t.totalDeliveredOrderCount + r.totalDeliveredOrderCount,
    bookingTotalCommission: t.bookingTotalCommission + r.bookingTotalCommission,
    deliveryTotalCommission: t.deliveryTotalCommission + r.deliveryTotalCommission,
    odaCharges: t.odaCharges + r.odaCharges,
    otherCharges: t.otherCharges + r.otherCharges,
    rechargeAmount: t.rechargeAmount + (r.rechargeAmount ?? 0),
    drsChargeCredited: t.drsChargeCredited + (r.drsChargeCredited ?? 0)
  }), {
    paidOrderCount: 0, topayOrderCount: 0, totalBookedOrderCount: 0, totalDeliveredOrderCount: 0,
    bookingTotalCommission: 0, deliveryTotalCommission: 0, odaCharges: 0, otherCharges: 0,
    rechargeAmount: 0, drsChargeCredited: 0
  }));

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Reports' }, { label: 'Vendor Audit Report' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.load();
  }

  /** No branch lock, on purpose — this report is company-level, not scoped to the
   *  caller's own branch like every other report in this module. */
  private filterRequest(): ShipmentSearchRequest {
    return { status: excludingCancelled(), bookingDateFrom: this.from() || undefined, bookingDateTo: this.to() || undefined };
  }

  load(): void {
    this.loading.set(true);
    this.service.vendorAudit(this.filterRequest()).subscribe({
      next: (rows) => { this.rows.set(rows); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onFrom(e: Event): void { this.from.set((e.target as HTMLInputElement).value); this.load(); this.loadStatus(); }
  onTo(e: Event): void { this.to.set((e.target as HTMLInputElement).value); this.load(); this.loadStatus(); }

  /** Send-status per branch for the chosen range; a failure leaves the report table untouched. */
  private loadStatus(): void {
    if (!this.datesSet()) { this.sendStatuses.set([]); return; }
    this.service.vendorAuditSendStatus(this.from(), this.to()).subscribe({
      next: (s) => this.sendStatuses.set(s),
      error: (e) => this.notify.error(apiMessage(e, 'Could not load WhatsApp send status.'))
    });
  }

  protected isBusy(id: string): boolean { return this.busyIds().has(id); }
  protected noPhone(id: string): boolean { return this.statusById()[id]?.managerPhoneCount === 0; }

  protected statusText(id: string): string {
    const s = this.statusById()[id];
    if (!s?.lastStatus) return 'Not sent';
    const label = ({ SENT: 'Sent', DELIVERED: 'Delivered', FAILED: 'Failed', PENDING: 'Pending' } as Record<string, string>)[s.lastStatus]
      ?? s.lastStatus;
    return s.lastSentAt ? `${label} · ${new Date(s.lastSentAt).toLocaleString()}` : label;
  }

  private setBusy(id: string, on: boolean): void {
    this.busyIds.update((set) => { const next = new Set(set); if (on) next.add(id); else next.delete(id); return next; });
  }

  downloadPdf(r: VendorAuditRow): void {
    this.setBusy(r.branchId, true);
    this.service.vendorAuditPdf(r.branchId, this.from(), this.to()).subscribe({
      next: ({ blob, filename }) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        this.setBusy(r.branchId, false);
      },
      error: (e) => { this.setBusy(r.branchId, false); this.notify.error(apiMessage(e, 'Could not download the report.')); }
    });
  }

  send(r: VendorAuditRow): void {
    const last = this.statusById()[r.branchId]?.lastStatus;
    if (last === 'SENT' || last === 'DELIVERED') this.confirmThen(r, () => this.doSend(r, true));
    else this.doSend(r, false);
  }

  private confirmThen(r: VendorAuditRow, go: () => void): void {
    this.dialogs.confirm({
      title: 'Send report again',
      message: `This report was already sent to ${this.statusById()[r.branchId]?.branchName ?? this.branchLabel(r.branchId)}. Send it again?`,
      confirmLabel: 'Send again'
    }).subscribe((ok) => { if (ok) go(); });
  }

  private doSend(r: VendorAuditRow, force: boolean): void {
    this.setBusy(r.branchId, true);
    this.service.sendVendorAuditReport(r.branchId, this.from(), this.to(), force).subscribe({
      next: (o) => {
        if (o.result === 'ALREADY_SENT' && !force) {
          this.setBusy(r.branchId, false);
          this.confirmThen(r, () => this.doSend(r, true));
          return;
        }
        this.announce(o);
        this.setBusy(r.branchId, false);
        this.loadStatus();
      },
      error: (e) => {
        this.setBusy(r.branchId, false);
        this.notify.error(apiMessage(e, 'Could not send the report.'));
        this.loadStatus();
      }
    });
  }

  private announce(o: SendOutcome): void {
    switch (o.result) {
      case 'SENT': this.notify.success(`Report sent to ${o.branchName} on WhatsApp`); break;
      case 'NO_PHONE': this.notify.warn(`No manager phone number for ${o.branchName}`); break;
      case 'TEMPLATE_NOT_APPROVED':
        this.notify.warn('Template not approved by Meta yet. Create branch_monthly_report in WhatsApp Manager and check its status in Communication > Templates.');
        break;
      case 'FAILED':
        this.notify.error(`Could not send the report to ${o.branchName}. Check Communication > Dashboard for the reason.`);
        break;
      default: break;
    }
  }

  sendAll(): void {
    this.sendingAll.set(true);
    this.service.sendVendorAuditReportToAll(this.from(), this.to(), false).subscribe({
      next: (outcomes) => {
        const n = (res: SendOutcome['result']) => outcomes.filter((o) => o.result === res).length;
        const parts = [`Sent ${n('SENT')}`, `no phone ${n('NO_PHONE')}`, `failed ${n('FAILED')}`];
        if (n('ALREADY_SENT')) parts.push(`already sent ${n('ALREADY_SENT')}`);
        if (n('TEMPLATE_NOT_APPROVED')) parts.push(`template not approved ${n('TEMPLATE_NOT_APPROVED')}`);
        this.notify.success(parts.join(', '));
        this.sendingAll.set(false);
        this.loadStatus();
      },
      error: (e) => {
        this.sendingAll.set(false);
        this.notify.error(apiMessage(e, 'Could not send the reports.'));
        this.loadStatus();
      }
    });
  }

  protected branchLabel(id: string): string { return this.branchOptions().find((o) => o.value === id)?.label ?? '—'; }

  exportCsv(): void {
    this.exporting.set(true);
    try {
      this.download(this.rows());
    } finally {
      this.exporting.set(false);
    }
  }

  private download(rows: VendorAuditRow[]): void {
    const header = ['branch', 'paidOrderCount', 'paidOrderQuantity', 'paidOrderAmount',
      'topayOrderCount', 'topayOrderQuantity', 'topayOrderAmount', 'paidCommission', 'deliveryCommission',
      'totalBookedOrderCount', 'totalDeliveredOrderCount', 'bookingTotalCommission', 'deliveryTotalCommission',
      'odaCharges', 'otherCharges', 'openingBalance', 'rechargeCount', 'drsChargeCredited', 'closingBalance'];
    const lines = rows.map((r) => [this.branchLabel(r.branchId), r.paidOrderCount, r.paidOrderQuantity,
      r.paidOrderAmount, r.topayOrderCount, r.topayOrderQuantity, r.topayOrderAmount, r.paidCommission,
      r.deliveryCommission, r.totalBookedOrderCount, r.totalDeliveredOrderCount, r.bookingTotalCommission,
      r.deliveryTotalCommission, r.odaCharges, r.otherCharges,
      r.openingBalance, r.rechargeCount, r.drsChargeCredited, r.closingBalance]);
    downloadCsv(`vendor-audit-report-${new Date().toISOString().slice(0, 10)}.csv`, header, lines,
      [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18]);
    this.notify.info(`Exported ${rows.length} branch row(s).`);
  }
}
