import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { DistrictLevelFreight, WEIGHT_SLABS } from '@core/models/district-level-freight.model';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { SortState } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { MasterDataService } from '@features/masters/master-data.service';
import { DistrictFreightTable, DistrictFreightPerms, DistrictFreightAction } from './components/district-freight-table';
import { DistrictFreightImportDialog } from './components/district-freight-import-dialog';
import { DistrictLevelFreightService } from './district-level-freight.service';

const WRITERS = [AppRole.COMPANY_ADMIN];

/**
 * District Level Freight directory — server pagination, sort, filter by From Station /
 * District / Status, Excel import and CSV export. Branch/district names arrive already
 * resolved on each row (DistrictLevelFreightResponse), so unlike RateList this page needs
 * no separate name-map lookups for the table itself — only for populating the filter and
 * form dropdowns, via the existing Branch/District masters.
 */
@Component({
  selector: 'app-district-freight-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiPagination, UiButton, UiSelect, DistrictFreightTable],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">District Level Freight</h1><p class="text-caption">Rate setup by From Station + District + weight slab — {{ page().totalElements }} in all.</p></div>
        <div class="page__actions">
          <app-button variant="stroked" icon="upload_file" (pressed)="openImport()">Import Excel</app-button>
          <app-button variant="stroked" icon="download" [loading]="exporting()" (pressed)="exportCsv()">Export</app-button>
          @if (can().create) { <app-button icon="add" (pressed)="create()">New Rate</app-button> }
        </div>
      </header>

      <div class="filters">
        <app-select [control]="branchFilter" label="From Station" [options]="branchOptions()" [allowEmpty]="true" placeholder="All stations" />
        <app-select [control]="districtFilter" label="District" [options]="districtOptions()" [allowEmpty]="true" placeholder="All districts" />
        <app-select [control]="statusFilter" label="Status" [options]="statusOptions" [allowEmpty]="true" placeholder="All statuses" />
      </div>

      <app-district-freight-table [rows]="page().content" [loading]="loading()" [sort]="sort()" [perms]="tablePerms()"
        [startIndex]="page().page * page().size"
        (sortChange)="onSort($event)" (action)="onAction($event)" />

      <app-pagination [page]="page()" (pageChange)="onPage($event)" />
    </div>
  `,
  styles: [`
    .filters { display:grid; grid-template-columns:repeat(3,minmax(0,220px)); gap:12px; margin-bottom:14px; }
    @media (max-width:760px){ .filters { grid-template-columns:1fr; } }
  `]
})
export class DistrictFreightList implements OnInit {
  private readonly service = inject(DistrictLevelFreightService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly dialogService = inject(DialogService);
  private readonly perms = inject(PermissionService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly page = signal<Page<DistrictLevelFreight>>(emptyPage<DistrictLevelFreight>());
  readonly sort = signal<SortState | null>({ active: 'createdDate', direction: 'desc' });

  readonly branchOptions = signal<SelectOption[]>([]);
  readonly districtOptions = signal<SelectOption[]>([]);
  readonly statusOptions: SelectOption[] = [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }];

  readonly branchFilter = new FormControl<string | null>(null);
  readonly districtFilter = new FormControl<string | null>(null);
  readonly statusFilter = new FormControl<string | null>(null);

  private query: PageQuery = { page: 0, size: 20, sort: 'createdDate,desc' };

  readonly can = computed(() => ({
    create: this.perms.canAccess({ roles: WRITERS }),
    update: this.perms.canAccess({ roles: WRITERS }),
    lifecycle: this.perms.canAccess({ roles: WRITERS }),
    delete: this.perms.canAccess({ roles: WRITERS })
  }));
  readonly tablePerms = computed<DistrictFreightPerms>(() => ({
    update: this.can().update, lifecycle: this.can().lifecycle, delete: this.can().delete
  }));

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'District Level Freight' }]);
    this.masters.options('branches').subscribe((o) => this.branchOptions.set(o));
    this.masters.options('districts').subscribe((o) => this.districtOptions.set(o));
    this.branchFilter.valueChanges.subscribe(() => { this.query = { ...this.query, page: 0 }; this.load(); });
    this.districtFilter.valueChanges.subscribe(() => { this.query = { ...this.query, page: 0 }; this.load(); });
    this.statusFilter.valueChanges.subscribe(() => { this.query = { ...this.query, page: 0 }; this.load(); });
    this.load();
  }

  private buildQuery(size?: number): PageQuery {
    return {
      ...this.query, ...(size ? { size, page: 0 } : {}),
      branchId: this.branchFilter.value ?? undefined,
      districtId: this.districtFilter.value ?? undefined,
      status: this.statusFilter.value ?? undefined
    };
  }

  private load(): void {
    this.loading.set(true);
    this.service.list(this.buildQuery()).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  onPage(i: number) { this.query = { ...this.query, page: i }; this.load(); }
  onSort(s: SortState) { this.sort.set(s); this.query = { ...this.query, sort: `${s.active},${s.direction}`, page: 0 }; this.load(); }

  create() { this.router.navigate(['/district-level-freight/new']); }

  openImport(): void {
    this.dialog.open(DistrictFreightImportDialog, { autoFocus: false, panelClass: 'app-dialog' })
      .afterClosed().subscribe((changed) => { if (changed) this.load(); });
  }

  onAction({ type, row }: { type: DistrictFreightAction; row: DistrictLevelFreight }): void {
    switch (type) {
      case 'view': return void this.router.navigate(['/district-level-freight', row.id]);
      case 'edit': return void this.router.navigate(['/district-level-freight', row.id, 'edit']);
      case 'activate': return this.lifecycle(row, 'activate');
      case 'deactivate': return this.lifecycle(row, 'deactivate');
      case 'delete': return this.remove(row);
    }
  }

  private lifecycle(row: DistrictLevelFreight, op: 'activate' | 'deactivate'): void {
    this.service[op](row.id).subscribe({
      next: () => { this.notify.success(`Rate ${op}d.`); this.load(); },
      error: (e) => this.notify.error(e?.error?.message ?? `Could not ${op} the rate.`)
    });
  }

  private remove(row: DistrictLevelFreight): void {
    this.dialogService.confirm({
      title: 'Delete this rate?',
      message: `${row.branchName ?? 'This From Station'} → ${row.districtName ?? 'this district'} will be removed.`,
      confirmLabel: 'Delete', danger: true
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.service.delete(row.id).subscribe({
        next: () => { this.notify.success('Rate deleted.'); this.load(); },
        error: (e) => this.notify.error(e?.error?.message ?? 'Could not delete the rate.')
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

  private download(rows: DistrictLevelFreight[]): void {
    const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const header = ['branchName', 'districtName', ...WEIGHT_SLABS.map((s) => s.key), 'odaApplicable', 'odaCharge', 'status'];
    const line = (r: DistrictLevelFreight) => [
      r.branchName, r.districtName, ...WEIGHT_SLABS.map((s) => (r as unknown as Record<string, unknown>)[s.key]),
      r.odaApplicable, r.odaCharge, r.status
    ].map(esc).join(',');
    const csv = [header.join(','), ...rows.map(line)].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url; a.download = `district-level-freight-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click(); URL.revokeObjectURL(url);
    this.notify.info(`Exported ${rows.length} rate(s).`);
  }
}
