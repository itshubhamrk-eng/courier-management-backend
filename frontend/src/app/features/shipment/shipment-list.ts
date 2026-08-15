import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
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
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportCsv()">Export</app-button>
          @if (can().create) { <app-button icon="add" (pressed)="create()">New Shipment</app-button> }
        </div>
      </header>

      <app-shipment-table [rows]="page().content" [loading]="loading()" [sort]="sort()" [perms]="tablePerms()"
                          [branchOptions]="branchOptions()" (sortChange)="onSort($event)" (action)="onAction($event)" />

      <app-pagination [page]="page()" (pageChange)="onPage($event)" />

      <app-drawer [open]="filterOpen()" title="Advanced filters" subtitle="Narrow the shipment list." (closed)="filterOpen.set(false)">
        <app-shipment-filter [lockBookingBranch]="!!myBranchId" (changed)="onFilter($event)" />
      </app-drawer>
    </div>
  `,
  styles: [`
    .fbadge { display:inline-grid; place-items:center; min-width:18px; height:18px; padding:0 5px; margin-left:2px;
      background:var(--brand-600); color:#fff; border-radius:999px; font:700 11px var(--font-sans); }
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
  private readonly auth = inject(AuthService);

  /** A branch-tier viewer sees only their own branch's bookings — booking branch is
   *  fixed to it in every query and the filter's own picker is hidden. Company-level
   *  roles carry no branchId and keep the unscoped company-wide view. */
  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly branchOptions = signal<SelectOption[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
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
    cancel: this.perms.canAccess({ roles: WRITERS, permissions: ['SHIPMENT_CANCEL'] })
  }));
  readonly tablePerms = computed<ShipmentPerms>(() => ({ update: this.can().update, cancel: this.can().cancel }));

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Shipments' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.load();
  }

  private buildQuery(size?: number): PageQuery {
    const f = this.filters();
    return {
      ...this.query, ...(size ? { size, page: 0 } : {}),
      status: f.status as unknown as string | undefined,
      bookingBranchId: this.myBranchId ?? f.bookingBranchId, deliveryBranchId: f.deliveryBranchId,
      bookingDateFrom: f.bookingDateFrom, bookingDateTo: f.bookingDateTo
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

  private branchLabel(id: string): string { return this.branchOptions().find((o) => o.value === id)?.label ?? id; }

  private download(rows: Shipment[]): void {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const header = ['shipmentNumber', 'trackingNumber', 'bookingDate', 'bookingBranch', 'deliveryBranch',
      'sender', 'senderContact', 'receiver', 'receiverContact', 'chargeableWeight', 'netAmount',
      'totalCommission', 'commissionOnBasicFreight', 'branchCommissionOnOtherAmount',
      'companyCommissionOnBasicFreight', 'status'];
    const line = (r: Shipment) => [r.shipmentNumber, r.trackingNumber, r.bookingDate,
      this.branchLabel(r.bookingBranchId), this.branchLabel(r.deliveryBranchId),
      r.senderName, r.senderContact, r.receiverName, r.receiverContact,
      r.chargeableWeight, r.netAmount ?? '',
      r.totalCommission ?? '', r.commissionOnBasicFreight ?? '', r.branchCommissionOnOtherAmount ?? '',
      r.companyCommissionOnBasicFreight ?? '', r.status].map(esc).join(',');
    const csv = [header.join(','), ...rows.map(line)].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url; a.download = `shipments-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click(); URL.revokeObjectURL(url);
    this.notify.info(`Exported ${rows.length} shipment(s).`);
  }
}
