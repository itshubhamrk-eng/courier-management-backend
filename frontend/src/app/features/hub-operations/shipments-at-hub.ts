import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { AuthService } from '@core/auth/auth.service';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { Shipment, ShipmentStatus } from '@core/models/shipment.model';
import { UiTable, TableColumn, SortState } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ShipmentStatusBadge } from '@features/shipment/components/shipment-status-badge';
import { ShipmentService } from '@features/shipment/shipment.service';
import { MasterDataService } from '@features/masters/master-data.service';

const STATUS_OPTIONS: SelectOption[] = [
  { value: 'DISPATCHED', label: 'Dispatched (en route)' },
  { value: 'READY_FOR_MANIFEST', label: 'Arrived, not yet sorted' },
  { value: 'MANIFEST_CREATED', label: 'On a Load Sheet, ready to dispatch' },
  { value: 'IN_SCAN', label: 'Received (final destination)' }
];

/**
 * Shipments At Hub — every shipment currently at, or currently headed to, the
 * signed-in user's hub. Filters: AWB, destination, booking branch, status, date,
 * shipment type. Reuses `ShipmentService.list` (`GET /shipments`) filtered by
 * `currentLocationId`/`nextLocationId` — no new backend endpoint.
 */
@Component({
  selector: 'app-shipments-at-hub',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, UiTable, UiPagination, UiSearch, UiButton, UiSelect, ShipmentStatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Shipments At Hub</h1>
          <p class="text-caption">{{ page().totalElements }} shipment(s) at or inbound to your hub.</p></div>
        <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
      </header>

      <div class="filters">
        <app-search placeholder="AWB / tracking number…" (changed)="onSearch($event)" />
        <app-select [control]="statusControl" label="Status" [options]="statusOptions" placeholder="Any status…" [allowEmpty]="true" emptyLabel="Any status" />
        <label class="dfld">Date <input type="date" [value]="date()" (change)="onDate($event)" /></label>
      </div>

      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [sort]="sort()"
                 [startIndex]="page().page * page().size"
                 emptyTitle="Nothing here" emptyHint="No shipment matches these filters." (sortChange)="onSort($event)" idKey="id">
        <ng-template #row let-s>
          <td><div class="cs">{{ s.trackingNumber }}</div><div class="text-caption">{{ s.shipmentNumber }}</div></td>
          <td>{{ branchNames().get(s.bookingBranchId) || '—' }}</td>
          <td>{{ s.fromCity || '—' }}</td>
          <td>{{ s.toCity || '—' }}</td>
          <td>{{ s.currentLocationId ? (branchNames().get(s.currentLocationId) || '—') : '—' }}</td>
          <td>{{ s.nextLocationId ? (branchNames().get(s.nextLocationId) || '—') : '—' }}</td>
          <td class="tbl--right">{{ s.chargeableWeight }} kg</td>
          <td><app-shipment-status-badge [status]="s.status" /></td>
          <td>{{ s.receivedAt ? (s.receivedAt | date: 'dd MMM, HH:mm') : '—' }}</td>
        </ng-template>
      </app-table>
      <app-pagination [page]="page()" (pageChange)="onPage($event)" />
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .filters { display:flex; gap:12px; align-items:flex-end; flex-wrap:wrap; margin-bottom:12px; }
    .dfld { display:flex; flex-direction:column; gap:4px; font:500 13px var(--font-sans); }
    .dfld input { padding:8px 10px; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .cs { font:600 14px var(--font-sans); }
    .tbl--right { text-align:right; }
  `]
})
export class ShipmentsAtHub implements OnInit {
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly auth = inject(AuthService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly masterData = inject(MasterDataService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;
  readonly loading = signal(true);
  readonly page = signal<Page<Shipment>>(emptyPage());
  readonly sort = signal<SortState | null>(null);
  readonly branchNames = signal<Map<string, string>>(new Map());
  readonly date = signal('');
  readonly statusControl = new FormControl<string | null>(null);
  readonly statusOptions = STATUS_OPTIONS;

  private query: PageQuery = { page: 0, size: 20 };

  readonly columns: TableColumn<Shipment>[] = [
    { key: 'trackingNumber', header: 'AWB' },
    { key: 'bookingBranchId', header: 'Booking Branch' },
    { key: 'fromCity', header: 'Origin' },
    { key: 'toCity', header: 'Destination' },
    { key: 'currentLocationId', header: 'Current Hub' },
    { key: 'nextLocationId', header: 'Next Destination' },
    { key: 'chargeableWeight', header: 'Weight', width: '90px' },
    { key: 'status', header: 'Status', width: '160px' },
    { key: 'receivedAt', header: 'Received', width: '140px' }
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Hub Operations' }, { label: 'Shipments At Hub' }]);
    this.masterData.branchDirectory().subscribe((list) =>
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`]))));
    this.statusControl.valueChanges.subscribe((v) => {
      this.query = { ...this.query, status: v ?? undefined, page: 0 };
      this.load();
    });
    this.load();
  }

  load(): void {
    if (!this.myBranchId) return;
    this.loading.set(true);
    this.shipmentService.list({ ...this.query, currentLocationId: this.myBranchId }).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onSearch(t: string): void {
    this.query = { ...this.query, search: t || undefined, page: 0 };
    this.load();
  }

  onDate(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.date.set(v);
    this.query = { ...this.query, bookingDateFrom: v || undefined, bookingDateTo: v || undefined, page: 0 };
    this.load();
  }

  onPage(i: number): void {
    this.query = { ...this.query, page: i };
    this.load();
  }

  onSort(s: SortState): void {
    this.sort.set(s);
    this.query = { ...this.query, sort: `${s.active},${s.direction}` };
    this.load();
  }
}
