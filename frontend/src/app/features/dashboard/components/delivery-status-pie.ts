import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ApexChart, ApexDataLabels, ApexLegend, ApexTooltip, NgApexchartsModule } from 'ng-apexcharts';
import { MatIconModule } from '@angular/material/icon';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { ThemeService } from '@core/services/theme.service';

const COLORS: Record<'light' | 'dark', string[]> = {
  light: ['#16a34a', '#d97706'],
  dark:  ['#22c55e', '#f59e0b']
};
const INK = { light: '#6b7280', dark: '#9ca3af' };
const LABELS = ['Delivered', 'Pending'];

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
 * Delivered vs Pending pie, month-to-date — same `statistics.delivered` /
 * `statistics.pending` figures as the KPI tiles, just plotted instead of counted.
 */
@Component({
  selector: 'app-delivery-status-pie',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgApexchartsModule, MatIconModule, UiCard, UiLoader],
  template: `
    <app-card [tone]="tone()" [title]="title()" [subtitle]="subtitle()">
      @if (loading()) {
        <app-loader [minHeight]="220" />
      } @else if (isEmpty()) {
        <div class="dsp-empty">
          <mat-icon>pie_chart</mat-icon>
          <p class="text-caption">No bookings yet this month</p>
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
    .dsp-empty { display:flex; flex-direction:column; align-items:center; justify-content:center;
      gap:8px; min-height:220px; color:var(--content-muted); }
    .dsp-empty mat-icon { font-size:36px; width:36px; height:36px; opacity:.45; }
  `]
})
export class DeliveryStatusPie {
  private readonly theme = inject(ThemeService);

  readonly title = input('Delivery Status');
  readonly subtitle = input('This month, delivered vs pending');
  readonly tone = input<'brand' | 'warning' | 'danger' | 'info' | 'success' | 'none'>('none');
  readonly delivered = input(0);
  readonly pending = input(0);
  readonly loading = input(false);

  readonly isEmpty = computed(() => this.delivered() + this.pending() === 0);

  readonly vm = computed<PieVm>(() => {
    const mode = this.theme.mode();
    return {
      series: [this.delivered(), this.pending()],
      labels: LABELS,
      chart: { type: 'donut', height: 260, fontFamily: 'inherit', foreColor: INK[mode] },
      colors: COLORS[mode],
      dataLabels: { enabled: true, style: { fontSize: '11px' } },
      legend: { show: true, position: 'bottom', fontSize: '12px' },
      tooltip: { theme: mode, y: { formatter: (v: number) => new Intl.NumberFormat('en-IN').format(v) } }
    };
  });
}
