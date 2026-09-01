import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { CommunicationService } from './communication.service';
import { CommunicationDashboard as Dashboard, COMMUNICATION_CHANNELS } from '@core/models/communication.model';
import { StatisticCard } from '@shared/components/statistic-card/statistic-card';
import { UiCard } from '@shared/components/ui-card/ui-card';

/** "Total Sent / Delivered / Failed / Pending" plus today's per-channel breakdown, exactly
 *  as the brief's own worked example lays it out. */
@Component({
  selector: 'app-communication-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatisticCard, UiCard],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Communication Dashboard</h1><p class="text-caption">Today's WhatsApp/SMS/Email send statistics.</p></div>
      </header>

      <section class="grid">
        <app-statistic-card label="Total Sent" icon="send" tone="brand" [loading]="loading()" [value]="dashboard()?.totalSent ?? '—'" />
        <app-statistic-card label="Delivered" icon="mark_email_read" tone="success" [loading]="loading()" [value]="dashboard()?.totalDelivered ?? '—'" />
        <app-statistic-card label="Failed" icon="error" tone="danger" [loading]="loading()" [value]="dashboard()?.totalFailed ?? '—'" />
        <app-statistic-card label="Pending" icon="hourglass_top" tone="warning" [loading]="loading()" [value]="dashboard()?.totalPending ?? '—'" />
      </section>

      <section class="channels">
        @for (channel of channels; track channel) {
          <app-card [title]="label(channel)" [subtitle]="'Today'">
            <div class="channel">
              <div class="stat"><span class="stat__v">{{ dashboard()?.channels?.[channel]?.sent ?? 0 }}</span><span class="stat__l">Sent</span></div>
              <div class="stat"><span class="stat__v success">{{ dashboard()?.channels?.[channel]?.delivered ?? 0 }}</span><span class="stat__l">Delivered</span></div>
              <div class="stat"><span class="stat__v danger">{{ dashboard()?.channels?.[channel]?.failed ?? 0 }}</span><span class="stat__l">Failed</span></div>
              <div class="stat"><span class="stat__v warning">{{ dashboard()?.channels?.[channel]?.pending ?? 0 }}</span><span class="stat__l">Pending</span></div>
            </div>
          </app-card>
        }
      </section>
    </div>
  `,
  styles: [`
    .grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:16px; margin:20px 0; }
    .channels { display:grid; grid-template-columns:repeat(auto-fit, minmax(260px, 1fr)); gap:20px; }
    .channel { display:grid; grid-template-columns:repeat(4, 1fr); gap:12px; text-align:center; }
    .stat { display:flex; flex-direction:column; gap:4px; }
    .stat__v { font:700 22px var(--font-sans); color:var(--content-fg); }
    .stat__v.success { color:var(--success); }
    .stat__v.danger { color:var(--danger); }
    .stat__v.warning { color:var(--warning); }
    .stat__l { font:500 12px var(--font-sans); color:var(--content-muted); }
  `]
})
export class CommunicationDashboardPage implements OnInit {
  private readonly service = inject(CommunicationService);
  private readonly breadcrumb = inject(BreadcrumbService);

  protected readonly channels = COMMUNICATION_CHANNELS;
  readonly loading = signal(true);
  readonly dashboard = signal<Dashboard | null>(null);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Communication' }, { label: 'Dashboard' }]);
    this.loading.set(true);
    this.service.dashboard().subscribe({
      next: (d) => { this.dashboard.set(d); this.loading.set(false); },
      error: () => { this.dashboard.set(null); this.loading.set(false); }
    });
  }

  protected label(channel: string): string {
    return channel.charAt(0) + channel.slice(1).toLowerCase();
  }
}
