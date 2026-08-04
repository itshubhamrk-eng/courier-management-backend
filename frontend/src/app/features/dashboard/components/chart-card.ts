import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import {
  ApexAxisChartSeries, ApexChart, ApexDataLabels, ApexFill, ApexGrid, ApexLegend,
  ApexStroke, ApexTooltip, ApexXAxis, ApexYAxis, NgApexchartsModule
} from 'ng-apexcharts';
import { MatIconModule } from '@angular/material/icon';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { ThemeService } from '@core/services/theme.service';
import { ChartSeries } from '../models/dashboard.model';

type ChartKind = 'line' | 'area' | 'bar';
type ColorKey = 'brand' | 'success' | 'warning' | 'info' | 'danger';

/** Theme-aware colours. Deterministic per mode so charts recolour on the theme toggle. */
const PALETTE: Record<'light' | 'dark', Record<ColorKey, string>> = {
  light: { brand: '#4f46e5', success: '#16a34a', warning: '#d97706', info: '#2563eb', danger: '#dc2626' },
  dark:  { brand: '#818cf8', success: '#22c55e', warning: '#f59e0b', info: '#3b82f6', danger: '#f87171' }
};
const INK   = { light: '#6b7280', dark: '#9ca3af' };
const GRID  = { light: '#e5e7eb', dark: '#1f2937' };

interface ChartVm {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  colors: string[];
  xaxis: ApexXAxis;
  yaxis: ApexYAxis;
  stroke: ApexStroke;
  fill: ApexFill;
  grid: ApexGrid;
  dataLabels: ApexDataLabels;
  legend: ApexLegend;
  tooltip: ApexTooltip;
}

/**
 * ApexCharts wrapper with the standard loading-skeleton / empty-state contract. Renders
 * nothing fabricated: an empty series shows the empty state, never a flat placeholder line.
 */
@Component({
  selector: 'app-chart-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgApexchartsModule, MatIconModule, UiCard],
  template: `
    <app-card [title]="title()" [subtitle]="subtitle()">
      @if (loading()) {
        <div class="chart-skeleton" [style.height.px]="height()"></div>
      } @else if (isEmpty()) {
        <div class="chart-empty" [style.min-height.px]="height()">
          <mat-icon>bar_chart</mat-icon>
          <p class="text-caption">No data for this period yet</p>
        </div>
      } @else {
        <apx-chart
          [series]="vm().series"
          [chart]="vm().chart"
          [colors]="vm().colors"
          [xaxis]="vm().xaxis"
          [yaxis]="vm().yaxis"
          [stroke]="vm().stroke"
          [fill]="vm().fill"
          [grid]="vm().grid"
          [dataLabels]="vm().dataLabels"
          [legend]="vm().legend"
          [tooltip]="vm().tooltip" />
      }
    </app-card>
  `,
  styles: [`
    .chart-empty { display:flex; flex-direction:column; align-items:center; justify-content:center;
      gap:8px; color:var(--content-muted); }
    .chart-empty mat-icon { font-size:36px; width:36px; height:36px; opacity:.45; }
    .chart-skeleton { border-radius:12px;
      background:linear-gradient(90deg,var(--surface-muted),var(--surface-border),var(--surface-muted));
      background-size:200% 100%; animation:sh 1.3s infinite; }
    @keyframes sh { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
  `]
})
export class ChartCard {
  private readonly theme = inject(ThemeService);

  readonly title = input('');
  readonly subtitle = input('');
  readonly type = input<ChartKind>('area');
  readonly data = input<ChartSeries[]>([]);
  readonly colorKeys = input<ColorKey[]>(['brand']);
  readonly loading = input(false);
  readonly height = input(260);
  /** Prefixed to y-axis / tooltip values, e.g. a currency symbol. */
  readonly valuePrefix = input('');
  readonly stacked = input(false);

  readonly isEmpty = computed(() =>
    this.data().length === 0 || this.data().every((s) => s.points.length === 0)
  );

  readonly vm = computed<ChartVm>(() => {
    const mode = this.theme.mode();
    const kind = this.type();
    const prefix = this.valuePrefix();
    const series = this.data().map((s) => ({ name: s.name, data: s.points.map((p) => p.value) }));
    const categories = (this.data()[0]?.points ?? []).map((p) => p.label);
    const colors = this.colorKeys().map((k) => PALETTE[mode][k]);

    return {
      series,
      chart: {
        type: kind, height: this.height(), stacked: this.stacked(),
        fontFamily: 'inherit', foreColor: INK[mode], toolbar: { show: false },
        animations: { enabled: true, speed: 400 }
      },
      colors,
      xaxis: {
        categories,
        axisBorder: { show: false }, axisTicks: { show: false },
        labels: { style: { fontSize: '11px' } }
      },
      yaxis: {
        labels: {
          style: { fontSize: '11px' },
          formatter: (v: number) => prefix + compact(v)
        }
      },
      stroke: { curve: 'smooth', width: kind === 'bar' ? 0 : 3, lineCap: 'round' },
      fill: kind === 'area'
        ? { type: 'gradient', gradient: { shadeIntensity: 1, opacityFrom: 0.35, opacityTo: 0.02, stops: [0, 90] } }
        : { opacity: 1 },
      grid: { borderColor: GRID[mode], strokeDashArray: 4, padding: { left: 4, right: 8 } },
      dataLabels: { enabled: false },
      legend: { show: series.length > 1, position: 'top', horizontalAlign: 'right', fontSize: '12px' },
      tooltip: {
        theme: mode,
        y: { formatter: (v: number) => prefix + new Intl.NumberFormat('en-IN').format(v) }
      }
    };
  });
}

function compact(v: number): string {
  if (Math.abs(v) >= 1000) return new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(v);
  return String(v);
}
