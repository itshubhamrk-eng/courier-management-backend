import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { CommunicationTemplate } from '@core/models/communication.model';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { CommunicationService } from './communication.service';
import { TemplateEditDialog } from './components/template-edit-dialog';

function label(value: string): string {
  return value.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

/** One row per (event, channel) — e.g. "SHIPMENT_BOOKED + WHATSAPP". Create/Edit/Enable/
 *  Disable/Preview all happen through the edit dialog; the four default events are seeded
 *  server-side on first load. */
@Component({
  selector: 'app-communication-templates',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, UiTable, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Notification Templates</h1><p class="text-caption">One message per event and channel — {{ '{{variable}}' }} placeholders are filled in at send time.</p></div>
      </header>

      <app-table
        [columns]="columns" [rows]="templates()" [loading]="loading()"
        emptyTitle="No templates" emptyHint="Templates seed automatically on first load."
        (rowClick)="edit($event)">
        <ng-template #row let-t>
          <td>{{ eventLabel(t.eventType) }}</td>
          <td>{{ t.channel }}</td>
          <td>{{ t.templateName }}</td>
          <td><app-status-badge [value]="t.status" [tone]="t.status === 'ACTIVE' ? 'success' : 'neutral'" /></td>
          <td class="text-caption">{{ t.updatedAt | date: 'medium' }}</td>
        </ng-template>
      </app-table>
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
  `]
})
export class CommunicationTemplates implements OnInit {
  private readonly service = inject(CommunicationService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(true);
  readonly templates = signal<CommunicationTemplate[]>([]);

  readonly columns: TableColumn<CommunicationTemplate>[] = [
    { key: 'eventType', header: 'Event' },
    { key: 'channel', header: 'Channel' },
    { key: 'templateName', header: 'Template Name' },
    { key: 'status', header: 'Status' },
    { key: 'updatedAt', header: 'Updated' }
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Communication' }, { label: 'Templates' }]);
    this.load();
  }

  eventLabel(e: string): string { return label(e); }

  edit(template: CommunicationTemplate): void {
    this.dialog.open(TemplateEditDialog, {
      autoFocus: false, panelClass: 'app-dialog', data: { template }
    }).afterClosed().subscribe((saved) => { if (saved) this.load(); });
  }

  private load(): void {
    this.loading.set(true);
    this.service.listTemplates().subscribe({
      next: (rows) => { this.templates.set(rows); this.loading.set(false); },
      error: () => { this.templates.set([]); this.loading.set(false); }
    });
  }
}
