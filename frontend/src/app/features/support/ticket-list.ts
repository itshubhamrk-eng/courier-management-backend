import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { UserService } from '@features/users/user.service';
import { TicketService } from '@core/services/ticket.service';
import { PageQuery, emptyPage } from '@core/models/page.model';
import {
  SlaStatus, Ticket, TicketCategory, TicketPriority, TicketStatus, TICKET_PRIORITIES, TICKET_STATUSES
} from '@core/models/ticket.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiAutocomplete } from '@shared/components/ui-autocomplete/ui-autocomplete';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { StatusBadge } from '@shared/components/status-badge/status-badge';

const STATUS_OPTIONS: SelectOption[] = TICKET_STATUSES.map((s) => ({ value: s, label: label(s) }));
const PRIORITY_OPTIONS: SelectOption[] = TICKET_PRIORITIES.map((p) => ({ value: p, label: p }));

function label(status: TicketStatus): string {
  return status.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

function priorityTone(p: TicketPriority): 'success' | 'warning' | 'danger' | 'info' {
  if (p === 'CRITICAL') return 'danger';
  if (p === 'HIGH') return 'warning';
  if (p === 'MEDIUM') return 'info';
  return 'success';
}

function statusTone(s: TicketStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (s === 'RESOLVED' || s === 'CLOSED') return 'success';
  if (s === 'REOPENED' || s === 'WAITING_FOR_INTERNAL_TEAM') return 'danger';
  if (s === 'WAITING_FOR_USER') return 'warning';
  if (s === 'OPEN') return 'info';
  return 'neutral';
}

function slaTone(s: SlaStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (s === 'BREACHED') return 'danger';
  if (s === 'WARNING') return 'warning';
  if (s === 'MET' || s === 'ON_TRACK') return 'success';
  return 'neutral';
}

function slaLabel(s: SlaStatus): string {
  if (s === 'NO_SLA') return 'No SLA';
  return s.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Ticket list — search/filter/sort/paginate over every ticket the caller may see (backend
 *  scopes: requester/assignee/own branch for non-admins, whole company for admins, every
 *  tenant for SUPER_ADMIN). Row click opens the detail page. */
@Component({
  selector: 'app-ticket-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, UiCard, UiButton, UiSearch, UiSelect, UiAutocomplete,
    UiTable, UiPagination, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Tickets</h1><p class="text-caption">Support requests across shipments, customers, wallet and more.</p></div>
        <app-button icon="add" (pressed)="createTicket()">Raise Ticket</app-button>
      </header>

      <app-card>
        <div class="filters">
          <app-search label="Search" placeholder="Search ticket #/subject…" (changed)="onSearch($event)" />
          <app-select [control]="statusControl" label="Status" [options]="statusOptions" [allowEmpty]="true" />
          <app-select [control]="priorityControl" label="Priority" [options]="priorityOptions" [allowEmpty]="true" />
          <app-select [control]="categoryControl" label="Category" [options]="categoryOptions()" [allowEmpty]="true" />
          <app-autocomplete [control]="branchControl" label="Branch" [options]="branchOptions()" placeholder="All branches" />
          <app-autocomplete [control]="agentControl" label="Agent" [options]="agentOptions()" placeholder="All agents" />
        </div>
      </app-card>

      <app-table
        [columns]="columns" [rows]="page().content" [loading]="loading()"
        [startIndex]="page().page * page().size"
        emptyTitle="No tickets" emptyHint="Nothing matches these filters yet."
        (rowClick)="open($event)">
        <ng-template #row let-t>
          <td><strong>{{ t.ticketNumber }}</strong></td>
          <td>{{ t.subject }}</td>
          <td>{{ categoryLabel(t.categoryId) }}</td>
          <td><app-status-badge [value]="t.priority" [tone]="priorityTone(t.priority)" /></td>
          <td><app-status-badge [value]="t.status" [label]="statusLabel(t.status)" [tone]="statusTone(t.status)" /></td>
          <td><app-status-badge [value]="t.slaStatus" [label]="slaLabel(t.slaStatus)" [tone]="slaTone(t.slaStatus)" /></td>
          <td>{{ branchLabel(t.relatedBranchId) }}</td>
          <td>{{ agentLabel(t.assigneeUserId) }}</td>
          <td class="text-caption">{{ t.createdAt | date: 'medium' }}</td>
          <td class="text-caption">{{ t.updatedAt | date: 'medium' }}</td>
        </ng-template>
      </app-table>

      <app-pagination [page]="page()" (pageChange)="goToPage($event)" />
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .filters { display:flex; gap:16px; flex-wrap:wrap; }
    .filters > * { min-width:180px; flex:1 1 180px; }
  `]
})
export class TicketList implements OnInit {
  private readonly service = inject(TicketService);
  private readonly masters = inject(MasterDataService);
  private readonly users = inject(UserService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly page = signal(emptyPage<Ticket>());
  readonly categoryOptions = signal<SelectOption[]>([]);
  readonly branchOptions = signal<SelectOption[]>([]);
  readonly agentOptions = signal<SelectOption[]>([]);
  private readonly categoryNames = signal<Map<string, string>>(new Map());
  private readonly branchNames = signal<Map<string, string>>(new Map());
  private readonly agentNames = signal<Map<string, string>>(new Map());

  readonly statusOptions = STATUS_OPTIONS;
  readonly priorityOptions = PRIORITY_OPTIONS;
  readonly statusControl = new FormControl<string | null>(null);
  readonly priorityControl = new FormControl<string | null>(null);
  readonly categoryControl = new FormControl<string | null>(null);
  readonly branchControl = new FormControl<string | null>(null);
  readonly agentControl = new FormControl<string | null>(null);

  readonly columns: TableColumn<Ticket>[] = [
    { key: 'ticketNumber', header: 'Ticket ID' },
    { key: 'subject', header: 'Subject' },
    { key: 'category', header: 'Category' },
    { key: 'priority', header: 'Priority' },
    { key: 'status', header: 'Status' },
    { key: 'sla', header: 'SLA' },
    { key: 'branch', header: 'Branch' },
    { key: 'agent', header: 'Assigned Agent' },
    { key: 'createdAt', header: 'Created' },
    { key: 'updatedAt', header: 'Updated' }
  ];

  private search = '';
  private pageIndex = 0;

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Support' }, { label: 'Tickets' }]);

    this.service.categories().subscribe((cats: TicketCategory[]) => {
      this.categoryOptions.set(cats.map((c) => ({ value: c.id, label: c.name })));
      this.categoryNames.set(new Map(cats.map((c) => [c.id, c.name])));
    });
    this.masters.branchDirectory().subscribe((branches) => {
      this.branchOptions.set(branches.map((b) => ({ value: b.id, label: `${b.branchName} (${b.branchCode})` })));
      this.branchNames.set(new Map(branches.map((b) => [b.id, `${b.branchName} (${b.branchCode})`])));
    });
    this.users.list({ page: 0, size: 200, sort: 'displayName,asc' }).subscribe((p) => {
      this.agentOptions.set(p.content.map((u) => ({ value: u.id, label: u.displayName })));
      this.agentNames.set(new Map(p.content.map((u) => [u.id, u.displayName])));
    });

    for (const control of [this.statusControl, this.priorityControl, this.categoryControl,
      this.branchControl, this.agentControl]) {
      control.valueChanges.subscribe(() => this.load());
    }
    this.load();
  }

  onSearch(term: string): void { this.search = term; this.pageIndex = 0; this.load(); }
  goToPage(index: number): void { this.pageIndex = index; this.load(); }

  createTicket(): void { this.router.navigate(['/support/tickets/new']); }
  open(t: Ticket): void { this.router.navigate(['/support/tickets', t.id]); }

  categoryLabel(id: string): string { return this.categoryNames().get(id) ?? '—'; }
  branchLabel(id?: string | null): string { return id ? (this.branchNames().get(id) ?? '—') : '—'; }
  agentLabel(id?: string | null): string { return id ? (this.agentNames().get(id) ?? '—') : 'Unassigned'; }
  statusLabel(s: TicketStatus): string { return label(s); }
  priorityTone(p: TicketPriority) { return priorityTone(p); }
  statusTone(s: TicketStatus) { return statusTone(s); }
  slaTone(s: SlaStatus) { return slaTone(s); }
  slaLabel(s: SlaStatus) { return slaLabel(s); }

  private load(): void {
    this.loading.set(true);
    const query: PageQuery = {
      page: this.pageIndex, size: 20, sort: 'createdAt,desc',
      ...(this.search ? { search: this.search } : {}),
      ...(this.statusControl.value ? { status: this.statusControl.value } : {}),
      ...(this.priorityControl.value ? { priority: this.priorityControl.value } : {}),
      ...(this.categoryControl.value ? { categoryId: this.categoryControl.value } : {}),
      ...(this.branchControl.value ? { relatedBranchId: this.branchControl.value } : {}),
      ...(this.agentControl.value ? { assigneeUserId: this.agentControl.value } : {})
    };
    this.service.search(query).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => { this.page.set(emptyPage()); this.loading.set(false); }
    });
  }
}
