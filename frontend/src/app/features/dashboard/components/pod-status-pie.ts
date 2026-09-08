import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ApexChart, ApexDataLabels, ApexLegend, ApexTooltip, NgApexchartsModule } from 'ng-apexcharts';
import { MatIconModule } from '@angular/material/icon';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { ThemeService } from '@core/services/theme.service';
import { PodOverview } from '../models/dashboard.model';

/** Theme-aware, one colour per bucket — same palette convention as ChartCard's own. */
const COLORS: Record<'light' | 'dark', string[]> = {
  light: ['#2563eb', '#d97706', '#16a34a', '#dc2626'],
  dark:  ['#3b82f6', '#f59e0b', '#22c55e', '#f87171']
};
const INK = { light: '#6b7280', dark: '#9ca3af' };
const LABELS = ['Pending Upload', 'Pending Verification', 'Approved', 'Rejected'];

interface PieVm {
  series: number[];
  labels: string[];
  chart: ApexChart;
  colors: string[];
  dataLabels: ApexDataLabels;
  legend: ApexLegend;
  tooltip: ApexTooltip;
}

/**
 * POD Dashboard pie — Pending Upload / Pending Verification / Approved / Rejected, reused
 * by both the company-wide overview (every branch) and the branch overview (this branch
 * only). Every figure comes from `PodOverviewResponse` — no client-side computation.
 */
@Component({
  selector: 'app-pod-status-pie',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgApexchartsModule, MatIconModule, UiCard, UiLoader],
  template: `
    <app-card [tone]="tone()" [title]="title()" [subtitle]="subtitle()">
      @if (loading()) {
        <app-loader [minHeight]="220" />
      } @else if (isEmpty()) {
        <div class="pod-pie-empty">
          <mat-icon>pie_chart</mat-icon>
          <p class="text-caption">No POD activity yet</p>
        </div>
      } @else {
        <apx-chart
          [series]="vm().series"
          [chart]="vm().chart"
          [labels]="vm().labels"
          [colors]="vm().colors"
          [dataLabels]="vm().dataLabels"
          [legend]="vm().legend"
          [tooltip]="vm().tooltip" />
      }
    </app-card>
  `,
  styles: [`
    .pod-pie-empty { display:flex; flex-direction:column; align-items:center; justify-content:center;
      gap:8px; min-height:220px; color:var(--content-muted); }
    .pod-pie-empty mat-icon { font-size:36px; width:36px; height:36px; opacity:.45; }
  `]
})
export class PodStatusPie {
  private readonly theme = inject(ThemeService);

  readonly title = input('POD Overview');
  readonly subtitle = input('Pending upload, pending verification, approved, rejected');
  readonly tone = input<'brand' | 'warning' | 'danger' | 'info' | 'success'>('info');
  readonly data = input<PodOverview | null>(null);
  readonly loading = input(false);

  readonly isEmpty = computed(() => {
    const d = this.data();
    if (!d) return true;
    return d.pendingUpload + d.pendingVerification + d.approved + d.rejected === 0;
  });

  readonly vm = computed<PieVm>(() => {
    const mode = this.theme.mode();
    const d = this.data();
    const series = d ? [d.pendingUpload, d.pendingVerification, d.approved, d.rejected] : [0, 0, 0, 0];
    return {
      series,
      labels: LABELS,
      chart: { type: 'donut', height: 260, fontFamily: 'inherit', foreColor: INK[mode] },
      colors: COLORS[mode],
      dataLabels: { enabled: true, style: { fontSize: '11px' } },
      legend: { show: true, position: 'bottom', fontSize: '12px' },
      tooltip: { theme: mode, y: { formatter: (v: number) => new Intl.NumberFormat('en-IN').format(v) } }
    };
  });
}
