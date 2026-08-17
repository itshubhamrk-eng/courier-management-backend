import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { MasterDataService } from '@features/masters/master-data.service';
import { UserService } from '@features/users/user.service';
import { TicketService } from '@core/services/ticket.service';
import { TicketDashboardStats } from '@core/models/ticket.model';
import { StatisticCard } from '@shared/components/statistic-card/statistic-card';
import { ChartSeries } from '@features/dashboard/models/dashboard.model';
import { ChartCard } from '@features/dashboard/components/chart-card';

const SUPER_ADMIN = [AppRole.SUPER_ADMIN];

/** Ticket Support dashboard — stat tiles plus breakdown/trend charts, including SLA
 *  breach count and open-ticket SLA performance. */
@Component({
  selector: 'app-support-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatisticCard, ChartCard],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Support Dashboard</h1><p class="text-caption">Ticket volume, status and SLA performance snapshot.</p></div>
      </header>

      <section class="grid">
        <app-statistic-card label="Total Tickets" icon="confirmation_number" tone="brand" [loading]="loading()" [value]="stats()?.totalTickets ?? '—'" />
        <app-statistic-card label="Open" icon="inbox" tone="info" [loading]="loading()" [value]="stats()?.openTickets ?? '—'" />
        <app-statistic-card label="Assigned" icon="person_add" tone="info" [loading]="loading()" [value]="stats()?.assignedTickets ?? '—'" />
        <app-statistic-card label="In Progress" icon="pending_actions" tone="warning" [loading]="loading()" [value]="stats()?.inProgress ?? '—'" />
        <app-statistic-card label="Waiting for User" icon="hourglass_top" tone="warning" [loading]="loading()" [value]="stats()?.waitingForUser ?? '—'" />
        <app-statistic-card label="Critical Tickets" icon="priority_high" tone="danger" [loading]="loading()" [value]="stats()?.criticalTickets ?? '—'" />
        <app-statistic-card label="Resolved Today" icon="task_alt" tone="success" [loading]="loading()" [value]="stats()?.resolvedToday ?? '—'" />
        <app-statistic-card label="Closed Today" icon="check_circle" tone="success" [loading]="loading()" [value]="stats()?.closedToday ?? '—'" />
        <app-statistic-card label="Avg. Resolution" icon="speed" tone="brand" [loading]="loading()"
          [value]="avgResolutionLabel()" />
        <app-statistic-card label="SLA Breached" icon="report_problem" tone="danger" [loading]="loading()" [value]="stats()?.slaBreached ?? '—'" />
      </section>

      <section class="charts">
        <app-chart-card title="Tickets by Status" type="bar" [colorKeys]="['brand']" [loading]="loading()" [data]="byStatusSeries()" />
        <app-chart-card title="Tickets by Priority" type="bar" [colorKeys]="['warning']" [loading]="loading()" [data]="byPrioritySeries()" />
        <app-chart-card title="Tickets by Category" type="bar" [colorKeys]="['info']" [loading]="loading()" [data]="byCategorySeries()" />
        <app-chart-card title="Tickets by Branch" type="bar" [colorKeys]="['success']" [loading]="loading()" [data]="byBranchSeries()" />
        <app-chart-card title="Tickets by Agent" type="bar" [colorKeys]="['brand']" [loading]="loading()" [data]="byAgentSeries()" />
        <app-chart-card title="SLA Performance (open tickets)" type="bar" [colorKeys]="['success']" [loading]="loading()" [data]="slaPerformanceSeries()" />
        @if (isSuperAdmin()) {
          <app-chart-card title="Tickets by Tenant" type="bar" [colorKeys]="['info']" [loading]="loading()" [data]="byTenantSeries()" />
        }
        <app-chart-card title="Ticket Volume (30 days)" type="area" [colorKeys]="['brand']" [loading]="loading()" [data]="volumeSeries()" />
      </section>
    </div>
  `,
  styles: [`
    .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:16px; margin:20px 0; }
    .charts { display:grid; grid-template-columns:repeat(auto-fit, minmax(360px, 1fr)); gap:20px; }
  `]
})
export class SupportDashboard implements OnInit {
  private readonly service = inject(TicketService);
  private readonly masters = inject(MasterDataService);
  private readonly users = inject(UserService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly perms = inject(PermissionService);

  readonly isSuperAdmin = computed(() => this.perms.hasAnyRole(SUPER_ADMIN));
  readonly loading = signal(true);
  readonly stats = signal<TicketDashboardStats | null>(null);
  private readonly branchNames = signal<Map<string, string>>(new Map());
  private readonly categoryNames = signal<Map<string, string>>(new Map());
  private readonly agentNames = signal<Map<string, string>>(new Map());

  readonly avgResolutionLabel = computed(() => {
    const h = this.stats()?.averageResolutionHours;
    return h == null ? '—' : `${h.toFixed(1)}h`;
  });

  readonly byStatusSeries = computed(() => toSeries(this.stats()?.byStatus, (k) => k.replace(/_/g, ' ')));
  readonly byPrioritySeries = computed(() => toSeries(this.stats()?.byPriority));
  readonly byCategorySeries = computed(() => toSeries(this.stats()?.byCategory, (k) => this.categoryNames().get(k) ?? k));
  readonly byBranchSeries = computed(() => toSeries(this.stats()?.byBranch, (k) => this.branchNames().get(k) ?? k));
  readonly byAgentSeries = computed(() => toSeries(this.stats()?.byAgent, (k) => this.agentNames().get(k) ?? k));
  readonly byTenantSeries = computed(() => toSeries(this.stats()?.byTenant));
  readonly slaPerformanceSeries = computed(() => toSeries(this.stats()?.slaPerformance, (k) => k.replace(/_/g, ' ')));
  readonly volumeSeries = computed<ChartSeries[]>(() => {
    const trend = this.stats()?.volumeTrend ?? [];
    if (trend.length === 0) return [];
    return [{ name: 'Tickets', points: trend.map((d) => ({ label: d.date.slice(5), value: d.total })) }];
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Support' }, { label: 'Dashboard' }]);
    this.masters.branchDirectory().subscribe((b) => this.branchNames.set(new Map(b.map((x) => [x.id, x.branchName]))));
    this.service.categories().subscribe((c) => this.categoryNames.set(new Map(c.map((x) => [x.id, x.name]))));
    this.users.list({ page: 0, size: 200, sort: 'displayName,asc' }).subscribe((p) =>
      this.agentNames.set(new Map(p.content.map((u) => [u.id, u.displayName]))));

    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.service.dashboard().subscribe({
      next: (s) => { this.stats.set(s); this.loading.set(false); },
      error: () => { this.stats.set(null); this.loading.set(false); }
    });
  }
}

function toSeries(record: Record<string, number> | undefined, labelFor?: (key: string) => string): ChartSeries[] {
  if (!record) return [];
  const entries = Object.entries(record).filter(([, v]) => v > 0);
  if (entries.length === 0) return [];
  return [{
    name: 'Tickets',
    points: entries.map(([k, v]) => ({ label: labelFor ? labelFor(k) : k, value: v }))
  }];
}
