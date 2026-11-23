import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { AuthService } from '@core/auth/auth.service';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { AppRole } from '@core/models/role.model';
import { Shipment, ShipmentSearchRequest } from '@core/models/shipment.model';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { SortState } from '@shared/components/ui-table/ui-table';
import { SelectOption } from '@shared/components/ui-select/ui-select';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiDrawer } from '@shared/components/ui-drawer/ui-drawer';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentTable, ShipmentPerms, ShipmentAction } from './components/shipment-table';
import { ShipmentFilter } from './components/shipment-filter';
import { ShipmentService } from './shipment.service';
import { excludingCancelled } from '../reports/non-cancelled.util';
import { ShipmentPrintDataService } from './shipment-print-data.service';
import { printShipmentCopies } from './shipment-copy-print.util';
import { downloadCsv } from '@shared/utils/csv-export.util';

const WRITERS = [AppRole.COMPANY_ADMIN, AppRole.BRANCH_MANAGER, AppRole.BOOKING_OPERATOR];

/**
 * Shipment list — server pagination, sort, debounced search (shipment number / AWB),
 * advanced filter drawer and CSV export. Row actions (view, edit while BOOKED, cancel
 * while still cancellable) route through the list, mirroring ShipmentServiceImpl's own
 * `isEditable`/`isCancellable` rules client-side so a disabled action is never a surprise.
 */
@Component({
  selector: 'app-shipment-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiPagination, UiSearch, UiButton, UiDrawer, ShipmentTable, ShipmentFilter],
  template: `
    <div class="page">
      <header class="page__head" data-tour="shipment-search-head">
        <div><h1 class="text-h1">Shipments</h1>
          <p class="text-caption">{{ page().totalElements }} booked {{ myBranchId ? 'at your branch' : 'in all' }}.</p></div>
        <div class="page__actions">
          <app-search placeholder="Search shipment no. or AWB…" (changed)="onSearch($event)" />
          <app-button variant="stroked" icon="filter_list" (pressed)="filterOpen.set(true)">
            Filters@if (activeFilters()) { <span class="fbadge">{{ activeFilters() }}</span> }
          </app-button>
          <app-button variant="stroked" icon="print" [loading]="printing()" [disabled]="!selectedCount()" (pressed)="printSelected()">
            Print Selected@if (selectedCount()) { ({{ selectedCount() }}) }
          </app-button>
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportCsv()">Export</app-button>
          @if (can().create) { <app-button icon="add" (pressed)="create()">New Shipment</app-button> }
        </div>
      </header>

      <app-shipment-table [showCompanyCommission]="!myBranchId" [rows]="page().content" [loading]="loading()" [sort]="sort()" [perms]="tablePerms()"
        [startIndex]="page().page * page().size"
                          [branchOptions]="branchOptions()" [paymentModeOptions]="paymentModeOptions()"
                          [selectedIds]="selected()" (selectionChange)="onSelect($event)"
                          (sortChange)="onSort($event)" (action)="onAction($event)" />

      <app-pagination [page]="page()" (pageChange)="onPage($event)" />

      <app-drawer [open]="filterOpen()" title="Advanced filters" subtitle="Narrow the shipment list." (closed)="filterOpen.set(false)">
        <app-shipment-filter [excludeCancelled]="true" [lockBookingBranch]="!!myBranchId" (changed)="onFilter($event)" />
      </app-drawer>
    </div>
  `,
  styles: [`
    .fbadge { display:inline-grid; place-items:center; min-width:18px; height:18px; padding:0 5px; margin-left:2px;
      background:var(--brand-600); color:#fff; border-radius:999px; font:700 14px var(--font-sans); }
  `]
})
export class ShipmentList implements OnInit {
  private readonly service = inject(ShipmentService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly confirm = inject(DialogService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly printData = inject(ShipmentPrintDataService);

  /** A branch-tier viewer sees only their own branch's bookings — booking branch is
   *  fixed to it in every query and the filter's own picker is hidden. Company-level
   *  roles carry no branchId and keep the unscoped company-wide view. */
  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly branchOptions = signal<SelectOption[]>([]);
  readonly paymentModeOptions = signal<SelectOption[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly printing = signal(false);
  /** Shipment ids ticked for bulk print; kept across pages, cleared after a print. */
  readonly selected = signal<ReadonlySet<string>>(new Set<string>());
  readonly selectedCount = computed(() => this.selected().size);
  readonly filterOpen = signal(false);
  readonly page = signal<Page<Shipment>>(emptyPage<Shipment>());
  readonly sort = signal<SortState | null>({ active: 'createdDate', direction: 'desc' });

  private query: PageQuery = { page: 0, size: 20, sort: 'createdDate,desc' };
  private readonly filters = signal<ShipmentSearchRequest>({});
  readonly activeFilters = computed(() =>
    Object.values(this.filters()).filter((v) => v != null && (!Array.isArray(v) || v.length)).length);

  readonly can = computed(() => ({
    create: this.perms.canAccess({ roles: WRITERS, permissions: ['SHIPMENT_CREATE'] }),
    update: this.perms.canAccess({ roles: WRITERS, permissions: ['SHIPMENT_UPDATE'] }),
    cancel: this.perms.canAccess({ roles: WRITERS, permissions: ['SHIPMENT_DELETE'] })
  }));
  readonly tablePerms = computed<ShipmentPerms>(() => ({ update: this.can().update, cancel: this.can().cancel }));

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Shipments' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.masters.options('payment-modes').subscribe((o) => this.paymentModeOptions.set(o));
    this.applyLinkedFilters();
    this.load();
  }

  /** A dashboard tile deep-links here with ?status=A,B&bookingDateFrom=..&bookingDateTo=.. */
  private applyLinkedFilters(): void {
    const q = this.route.snapshot.queryParamMap;
    const status = q.get('status');
    const from = q.get('bookingDateFrom');
    const to = q.get('bookingDateTo');
    if (!status && !from && !to) return;
    const datePattern = /^\d{4}-\d{2}-\d{2}$/;
    this.filters.set({
      ...(status ? { status: status.split(',').filter(Boolean) as ShipmentSearchRequest['status'] } : {}),
      ...(from && datePattern.test(from) ? { bookingDateFrom: from } : {}),
      ...(to && datePattern.test(to) ? { bookingDateTo: to } : {})
    });
  }

  private buildQuery(size?: number): PageQuery {
    const f = this.filters();
    return {
      ...this.query, ...(size ? { size, page: 0 } : {}),
      status: excludingCancelled(f.status) as unknown as string,
      bookingBranchId: this.myBranchId ?? f.bookingBranchId, deliveryBranchId: f.deliveryBranchId,
      bookingDateFrom: f.bookingDateFrom, bookingDateTo: f.bookingDateTo, paymentModeId: f.paymentModeId
    };
  }

  private load(): void {
    this.loading.set(true);
    this.service.list(this.buildQuery()).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onSearch(t: string) { this.query = { ...this.query, search: t || undefined, page: 0 }; this.load(); }
  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.query = { ...this.query, sort: `${s.active},${s.direction}`, page: 0 }; this.load(); }
  onFilter(f: ShipmentSearchRequest) { this.filters.set(f); this.query = { ...this.query, page: 0 }; this.filterOpen.set(false); this.load(); }

  /** Each shipment prints 3 copies, and every one costs a shipment + charge fetch — cap the batch. */
  private static readonly MAX_BULK_PRINT = 50;

  onSelect({ ids, selected }: { ids: string[]; selected: boolean }): void {
    const next = new Set(this.selected());
    for (const id of ids) {
      if (!selected) next.delete(id);
      else if (next.size < ShipmentList.MAX_BULK_PRINT) next.add(id);
      else { this.notify.info(`Select up to ${ShipmentList.MAX_BULK_PRINT} shipments per print.`); break; }
    }
    this.selected.set(next);
  }

  printSelected(): void {
    const ids = [...this.selected()];
    if (!ids.length) return;
    this.printing.set(true);
    this.printData.buildMany(ids).subscribe({
      next: (list) => {
        this.printing.set(false);
        if (!list.length) return void this.notify.error('Nothing to print — no charge record found for the selection.');
        if (list.length < ids.length) this.notify.info(`${ids.length - list.length} shipment(s) skipped (no charge record).`);
        printShipmentCopies(list);
        this.selected.set(new Set());
      },
      error: () => { this.printing.set(false); this.notify.error('Could not prepare the print.'); }
    });
  }

  create() { this.router.navigate(['/shipments/new']); }

  onAction({ type, shipment }: { type: ShipmentAction; shipment: Shipment }): void {
    switch (type) {
      case 'view': return void this.router.navigate(['/shipments', shipment.id]);
      case 'edit': return void this.router.navigate(['/shipments', shipment.id, 'edit']);
      case 'cancel': return this.cancel(shipment);
    }
  }

  private cancel(shipment: Shipment): void {
    this.confirm.prompt({
      title: 'Cancel shipment', message: `"${shipment.shipmentNumber}" will be cancelled. This cannot be undone once it has left the branch.`,
      label: 'Reason (optional)', placeholder: 'Booked in error, customer request…', confirmLabel: 'Cancel Shipment', danger: true
    }).subscribe((reason) => {
      if (reason === undefined) return;
      this.service.cancel(shipment.id, reason).subscribe({
        next: () => { this.notify.success('Shipment cancelled.'); this.load(); },
        error: (e) => this.notify.error(e?.error?.message ?? 'Could not cancel the shipment.')
      });
    });
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.service.list(this.buildQuery(100)).subscribe({
      next: (p) => { this.download(p.content); this.exporting.set(false); },
      error: () => { this.exporting.set(false); this.notify.error('Export failed.'); }
    });
  }

  private branchLabel(id: string | null | undefined): string { return this.branchOptions().find((o) => o.value === id)?.label ?? id ?? '—'; }

  private download(rows: Shipment[]): void {
    const withCompany = !this.myBranchId;
    const header = ['shipmentNumber', 'trackingNumber', 'bookingDate', 'bookingBranch', 'deliveryBranch',
      'sender', 'senderContact', 'receiver', 'receiverContact', 'quantity', 'chargeableWeight', 'netAmount',
      'totalCommission', 'commissionOnBasicFreight', 'branchCommissionOnOtherAmount',
      ...(withCompany ? ['companyCommissionOnBasicFreight'] : []), 'status'];
    const lines = rows.map((r) => [r.shipmentNumber, r.trackingNumber, r.bookingDate,
      this.branchLabel(r.bookingBranchId), this.branchLabel(r.deliveryBranchId),
      r.senderName, r.senderContact, r.receiverName, r.receiverContact,
      r.numberOfPackages ?? '', r.chargeableWeight, r.netAmount ?? '',
      r.totalCommission ?? '', r.commissionOnBasicFreight ?? '', r.branchCommissionOnOtherAmount ?? '',
      ...(withCompany ? [r.companyCommissionOnBasicFreight ?? ''] : []), r.status]);
    downloadCsv(`shipments-${new Date().toISOString().slice(0, 10)}.csv`, header, lines,
      withCompany ? [9, 10, 11, 12, 13, 14] : [9, 10, 11, 12, 13]);
    this.notify.info(`Exported ${rows.length} shipment(s).`);
  }
}
