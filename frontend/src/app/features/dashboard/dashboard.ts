import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { environment } from '@env/environment';
import { StatisticCard } from '@shared/components/statistic-card/statistic-card';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { DashboardService } from './dashboard.service';
import { DASHBOARD_LAYOUTS, QuickActionDef, StatTileDef, resolveProfile } from './dashboard.roles';
import { DashboardStatistics, DashboardSummary } from './models/dashboard.model';
import { ChartCard } from './components/chart-card';
import { ActivityTimeline } from './components/activity-timeline';
import { RecentShipments } from './components/recent-shipments';
import { QuickActions } from './components/quick-actions';
import { BranchSummary } from './components/branch-summary';
import { TrackBox } from '@features/shipment-movement/components/track-box';
// import { HubSummary } from './components/hub-summary'; // hub module not built yet

const MONEY_KEYS: ReadonlySet<keyof DashboardStatistics> =
  new Set(['totalRevenue', 'walletBalance', 'todayCollection']);

/**
 * Role-based enterprise dashboard. The layout (which KPI tiles, charts, cards and quick
 * actions) is resolved from the signed-in user's role + scope (dashboard.roles.ts); the
 * figures come exclusively from DashboardService (API only). Loading, empty and error
 * states are all handled — no mock data anywhere.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, MatIconModule, StatisticCard, UiCard, ChartCard, ActivityTimeline,
    RecentShipments, QuickActions, BranchSummary, TrackBox /*, HubSummary */
  ],
  template: `
    <div class="dash">
      <!-- welcome / context -->
      <header class="dash__welcome">
        <div>
          <h1 class="text-h1">{{ greeting() }}, {{ auth.displayName() || 'there' }}</h1>
          <p class="text-caption">{{ companyName }} · {{ scopeLabel() }}</p>
        </div>
        <div class="dash__date">
          <mat-icon>calendar_today</mat-icon>
          <span>{{ now | date:'EEEE, d MMM y' }}</span>
        </div>
      </header>

      @if (profile() !== 'PLATFORM') {
        <app-card title="Track Shipment" subtitle="Enter an AWB (Tracking No.) or Shipment No.">
          <app-track-box />
        </app-card>
      }

      @if (error()) {
        <app-card>
          <div class="dash__error">
            <mat-icon>cloud_off</mat-icon>
            <div>
              <p class="text-h3">Couldn't load the dashboard</p>
              <p class="text-caption">The service is unreachable. Check your connection and try again.</p>
            </div>
            <button type="button" class="dash__retry" (click)="reload()">Retry</button>
          </div>
        </app-card>
      } @else {
        <!-- KPI tiles -->
        <section class="dash__grid" data-tour="dash-stats">
          @for (t of layout().stats; track t.key) {
            <app-statistic-card
              [label]="t.label" [icon]="t.icon" [tone]="t.tone" [prefix]="t.prefix ?? ''"
              [loading]="loading()" [value]="statValue(t)" />
          }
        </section>

        <app-quick-actions data-tour="dash-quick-actions" [actions]="layout().quickActions" (pick)="onAction($event)" />

        <!-- charts -->
        @if (hasCharts()) {
          <section class="dash__charts">
            @if (layout().sections.shipmentTrend) {
              <app-chart-card title="Shipment Trend" subtitle="Bookings over time" type="area"
                [colorKeys]="['brand']" [loading]="loading()" [data]="data()?.charts?.shipmentTrend ?? []" />
            }
            @if (layout().sections.deliveryPerformance) {
              <app-chart-card title="Delivery Performance" subtitle="Delivered vs in-transit vs pending" type="bar"
                [stacked]="true" [colorKeys]="['success','info','warning']"
                [loading]="loading()" [data]="data()?.charts?.deliveryPerformance ?? []" />
            }
            @if (layout().sections.revenueTrend) {
              <app-chart-card title="Revenue Trend" subtitle="Collections over time" type="area"
                [colorKeys]="['success']" valuePrefix="₹"
                [loading]="loading()" [data]="data()?.charts?.revenueTrend ?? []" />
            }
          </section>
        }

        <!-- content columns -->
        <section class="dash__cols">
          <div class="dash__main">
            @if (layout().sections.recentShipments) {
              <app-recent-shipments [loading]="loading()" [rows]="data()?.recentShipments ?? []" />
            }
            @if (layout().sections.recentActivity) {
              <app-activity-timeline [loading]="loading()" [rows]="data()?.recentActivity ?? []" />
            }
          </div>

          <aside class="dash__side">
            @if (layout().sections.branchSummary) {
              <app-branch-summary [loading]="loading()" [rows]="data()?.branchSummary ?? []" />
            }
            <!-- hub module not built yet
            @if (layout().sections.hubSummary) {
              <app-hub-summary [loading]="loading()" [rows]="data()?.hubSummary ?? []" />
            }
            -->
          </aside>
        </section>
      }
    </div>
  `,
  styles: [`
    .dash { display:flex; flex-direction:column; gap:20px; width:100%; }
    .dash__welcome { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; flex-wrap:wrap; }
    .dash__date { display:flex; align-items:center; gap:8px; padding:8px 14px; border-radius:var(--r-pill);
      background:var(--surface); border:1px solid var(--surface-border); font:600 13px var(--font-sans); color:var(--content-muted); }
    .dash__date mat-icon { font-size:18px; width:18px; height:18px; }
    .dash__grid { display:grid; grid-template-columns:repeat(4, minmax(0,1fr)); gap:16px; }
    .dash__charts { display:grid; grid-template-columns:repeat(auto-fit, minmax(340px,1fr)); gap:16px; }
    .dash__cols { display:grid; grid-template-columns:1.6fr 1fr; gap:16px; align-items:start; }
    .dash__main, .dash__side { display:flex; flex-direction:column; gap:16px; min-width:0; }
    .dash__error { display:flex; align-items:center; gap:16px; padding:8px; }
    .dash__error mat-icon { font-size:36px; width:36px; height:36px; color:var(--danger); }
    .dash__error div { flex:1; }
    .dash__retry { padding:8px 16px; border-radius:var(--r-pill); border:0; cursor:pointer;
      background:var(--brand-600); color:#fff; font:600 13px var(--font-sans); }
    @media (max-width:1100px){ .dash__grid{grid-template-columns:repeat(2,1fr)} .dash__cols{grid-template-columns:1fr} }
    @media (max-width:560px){ .dash__grid{grid-template-columns:1fr} }
  `]
})
export class Dashboard implements OnInit {
  private readonly dashboard = inject(DashboardService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  readonly now = new Date();
  readonly companyName = environment.appName;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly data = signal<DashboardSummary | null>(null);

  protected readonly profile = computed(() =>
    resolveProfile(this.auth.roles(), {
      branchId: this.auth.user()?.branchId, hubId: this.auth.user()?.hubId
    })
  );
  readonly layout = computed(() => DASHBOARD_LAYOUTS[this.profile()]);
  readonly hasCharts = computed(() => {
    const s = this.layout().sections;
    return s.shipmentTrend || s.deliveryPerformance || s.revenueTrend;
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Dashboard' }]);
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(false);
    this.dashboard.load(this.profile()).subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }

  greeting(): string {
    const h = this.now.getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  }

  scopeLabel(): string {
    switch (this.profile()) {
      case 'PLATFORM': return 'Platform overview';
      case 'COMPANY': return 'Company-wide';
      case 'BRANCH_MANAGER':
      case 'BRANCH_OPERATOR': return 'Branch operations';
      case 'HUB_MANAGER':
      case 'HUB_OPERATOR': return 'Hub operations';
    }
  }

  /** Format a tile's value: money grouped with Intl, null shown as em dash, counts plain. */
  statValue(t: StatTileDef): string | number {
    const v = this.data()?.statistics[t.key];
    if (v == null) return '—';
    if (MONEY_KEYS.has(t.key)) return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 }).format(v);
    return v;
  }

  onAction(a: QuickActionDef): void {
    if (a.route) { this.router.navigate([a.route]); return; }
    // shipment-module actions (book/search/print/manifest/dispatch/receive) have no page yet
    this.notify.info(`${a.label} — available once the shipments module ships.`);
  }
}
