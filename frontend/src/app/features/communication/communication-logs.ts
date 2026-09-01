import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { emptyPage, PageQuery } from '@core/models/page.model';
import {
  CommunicationChannel, CommunicationEventType, CommunicationLog, CommunicationStatus,
  COMMUNICATION_CHANNELS, COMMUNICATION_EVENT_TYPES
} from '@core/models/communication.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { CommunicationService } from './communication.service';

const CHANNEL_OPTIONS: SelectOption[] = COMMUNICATION_CHANNELS.map((c) => ({ value: c, label: label(c) }));
const EVENT_OPTIONS: SelectOption[] = COMMUNICATION_EVENT_TYPES.map((e) => ({ value: e, label: label(e) }));
const STATUS_OPTIONS: SelectOption[] = (['PENDING', 'SENT', 'DELIVERED', 'FAILED', 'CANCELLED'] as CommunicationStatus[])
  .map((s) => ({ value: s, label: label(s) }));

function label(value: string): string {
  return value.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

function statusTone(s: CommunicationStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  if (s === 'SENT' || s === 'DELIVERED') return 'success';
  if (s === 'FAILED') return 'danger';
  if (s === 'PENDING') return 'warning';
  return 'neutral';
}

/** Show shipment/customer/event/channel/recipient/status/provider/sent-time/failure-reason,
 *  with a Retry Failed action — the brief's own "Communication Log" section. */
@Component({
  selector: 'app-communication-logs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, UiCard, UiButton, UiSelect, UiTable, UiPagination, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Communication Logs</h1><p class="text-caption">Every notification attempt, one row per shipment/event/channel.</p></div>
      </header>

      <app-card>
        <div class="filters">
          <app-select [control]="channelControl" label="Channel" [options]="channelOptions" [allowEmpty]="true" />
          <app-select [control]="eventControl" label="Event" [options]="eventOptions" [allowEmpty]="true" />
          <app-select [control]="statusControl" label="Status" [options]="statusOptions" [allowEmpty]="true" />
        </div>
      </app-card>

      <app-table
        [columns]="columns" [rows]="page().content" [loading]="loading()"
        [startIndex]="page().page * page().size"
        emptyTitle="No communication attempts" emptyHint="Nothing matches these filters yet.">
        <ng-template #row let-l>
          <td class="mono">{{ l.recipient }}</td>
          <td>{{ eventLabel(l.eventType) }}</td>
          <td>{{ channelLabel(l.channel) }}</td>
          <td><app-status-badge [value]="l.status" [label]="statusLabel(l.status)" [tone]="tone(l.status)" /></td>
          <td class="text-caption">{{ l.errorMessage || '—' }}</td>
          <td>{{ l.attemptCount }}</td>
          <td class="text-caption">{{ l.sentAt ? (l.sentAt | date: 'medium') : '—' }}</td>
          <td>
            @if (l.status === 'FAILED') {
              <app-button variant="stroked" icon="refresh" [loading]="retrying() === l.id" (pressed)="retry(l)">Retry</app-button>
            }
          </td>
        </ng-template>
      </app-table>

      <app-pagination [page]="page()" (pageChange)="goToPage($event)" />
    </div>
  `,
  styles: [`
    .filters { display:flex; gap:16px; flex-wrap:wrap; }
    .filters > * { min-width:180px; flex:1 1 180px; }
    .mono { font-family:var(--font-mono, ui-monospace); }
  `]
})
export class CommunicationLogs implements OnInit {
  private readonly service = inject(CommunicationService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);

  readonly loading = signal(true);
  readonly page = signal(emptyPage<CommunicationLog>());
  readonly retrying = signal<string | null>(null);

  readonly channelOptions = CHANNEL_OPTIONS;
  readonly eventOptions = EVENT_OPTIONS;
  readonly statusOptions = STATUS_OPTIONS;
  readonly channelControl = new FormControl<CommunicationChannel | null>(null);
  readonly eventControl = new FormControl<CommunicationEventType | null>(null);
  readonly statusControl = new FormControl<CommunicationStatus | null>(null);

  readonly columns: TableColumn<CommunicationLog>[] = [
    { key: 'recipient', header: 'Recipient' },
    { key: 'eventType', header: 'Event' },
    { key: 'channel', header: 'Channel' },
    { key: 'status', header: 'Status' },
    { key: 'errorMessage', header: 'Failure Reason' },
    { key: 'attemptCount', header: 'Attempts' },
    { key: 'sentAt', header: 'Sent' },
    { key: 'actions', header: '' }
  ];

  private pageIndex = 0;

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Communication' }, { label: 'Logs' }]);
    const refresh = () => { this.pageIndex = 0; this.load(); };
    this.channelControl.valueChanges.subscribe(refresh);
    this.eventControl.valueChanges.subscribe(refresh);
    this.statusControl.valueChanges.subscribe(refresh);
    this.load();
  }

  goToPage(index: number): void { this.pageIndex = index; this.load(); }

  eventLabel(e: CommunicationEventType): string { return label(e); }
  channelLabel(c: CommunicationChannel): string { return label(c); }
  statusLabel(s: CommunicationStatus): string { return label(s); }
  tone(s: CommunicationStatus) { return statusTone(s); }

  retry(l: CommunicationLog): void {
    this.retrying.set(l.id);
    this.service.retry(l.id).subscribe({
      next: () => { this.retrying.set(null); this.notify.success('Requeued for retry.'); this.load(); },
      error: () => this.retrying.set(null)
    });
  }

  private load(): void {
    this.loading.set(true);
    const query: PageQuery = { page: this.pageIndex, size: 20, sort: 'createdAt,desc' };
    this.service.searchLogs({
      channel: this.channelControl.value ?? undefined,
      eventType: this.eventControl.value ?? undefined,
      status: this.statusControl.value ?? undefined
    }, query).subscribe({
      next: (p) => { this.page.set(p); this.loading.set(false); },
      error: () => { this.page.set(emptyPage()); this.loading.set(false); }
    });
  }
}
