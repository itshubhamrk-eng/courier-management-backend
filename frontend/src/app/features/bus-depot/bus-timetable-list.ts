import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Router } from '@angular/router';
import { Page } from '@core/models/page.model';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { PermissionService } from '@core/auth/permission.service';
import { BUS_DEPOT_TYPE_LABEL, BusDepot, BusTimetableSummary } from '@core/models/bus-depot.model';
import { UiAutocomplete } from '@shared/components/ui-autocomplete/ui-autocomplete';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { BusDepotService } from './bus-depot.service';

/** Depots / stations that have a timetable board (वेळापत्रक), plus a picker to open or start one. */
@Component({
  selector: 'app-bus-timetable-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiButton, UiCard, UiTable, UiPagination, UiAutocomplete],
  template: `
    <div class="page">
      <header class="page__head"><div>
        <h1 class="text-h1">Bus Timetable</h1>
        <p class="text-caption">Per depot / station: the places its buses go to, with the times they leave and arrive — {{ boards().length }} board(s) saved.</p>
      </div></header>

      <app-card [title]="canEdit() ? 'Open or start a timetable' : 'Open a timetable'">
        <div class="pick">
          <app-autocomplete [control]="depotCtl" label="Depot / station" [options]="depotOptions()" placeholder="Search depot or station…" />
          <app-button icon="open_in_new" [disabled]="!depotCtl.value" (pressed)="open(depotCtl.value)">Open</app-button>
        </div>
      </app-card>

      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [startIndex]="page().page * page().size"
                 emptyTitle="No timetables yet" emptyHint="Pick a depot or station above to enter its board."
                 (rowClick)="open($event.depot.id)">
        <ng-template #row let-b>
          <td><div class="nm">{{ b.depot.depotName }}</div><div class="sub">{{ b.depot.depotCode }} · {{ typeLabel(b.depot.depotType) }}</div></td>
          <td>{{ b.places }}</td>
          <td>{{ b.departures }}</td>
          <td>{{ b.arrivals }}</td>
        </ng-template>
      </app-table>
      <app-pagination [page]="page()" (pageChange)="pageIndex.set($event)" />
    </div>
  `,
  styles: [`
    .pick { display:flex; align-items:flex-end; gap:12px; } .pick app-autocomplete { flex:1; max-width:520px; }
    .nm { font:600 14px var(--font-sans); } .sub { font:400 12px var(--font-sans); color:var(--content-muted); }
  `]
})
export class BusTimetableList implements OnInit {
  private readonly service = inject(BusDepotService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly permissions = inject(PermissionService);

  protected readonly boards = signal<BusTimetableSummary[]>([]);
  protected readonly pageIndex = signal(0);
  protected readonly page = computed<Page<BusTimetableSummary>>(() => {
    const all = this.boards(), size = 20;
    const totalPages = Math.max(1, Math.ceil(all.length / size));
    const page = Math.min(this.pageIndex(), totalPages - 1);
    return {
      content: all.slice(page * size, (page + 1) * size), page, size, totalElements: all.length, totalPages,
      first: page === 0, last: page >= totalPages - 1, hasNext: page < totalPages - 1
    };
  });
  protected readonly depots = signal<BusDepot[]>([]);
  protected readonly loading = signal(true);
  protected readonly depotCtl = new FormControl<string>('', { nonNullable: true });
  protected readonly canEdit = computed(() => this.permissions.hasRole('COMPANY_ADMIN'));
  protected readonly depotOptions = computed(() =>
    this.depots().map((d) => ({ value: d.id, label: `${d.depotName} (${d.depotCode}) · ${BUS_DEPOT_TYPE_LABEL[d.depotType]}` })));

  protected readonly columns: TableColumn<BusTimetableSummary>[] = [
    { key: 'depot', header: 'Depot / Station' },
    { key: 'places', header: 'Places', width: '110px' },
    { key: 'departures', header: 'Departures', width: '130px' },
    { key: 'arrivals', header: 'Arrivals', width: '130px' }
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'MSRTC' }, { label: 'Bus Timetable' }]);
    this.service.timetables().subscribe({
      next: (b) => { this.boards.set(b ?? []); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
    this.service.list({ status: 'ACTIVE' }).subscribe((d) => this.depots.set(d ?? []));
  }

  protected typeLabel(t: BusDepot['depotType']): string { return BUS_DEPOT_TYPE_LABEL[t]; }
  protected open(depotId: string): void { if (depotId) this.router.navigate(['/msrtc/timetable', depotId]); }
}
