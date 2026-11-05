import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { emptyPage } from '@core/models/page.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { SecurityIncidentService } from './security-incident.service';
import { IncidentFilter, IncidentRule, IncidentSeverity, IncidentStatus, SecurityIncident } from './security-incident.model';
import { INCIDENT_RULE_CODES, INCIDENT_SEVERITIES, SEVERITY_TONE, INCIDENT_STATUSES, STATUS_TONE, ruleLabel } from './incident-labels';

const PAGE_SIZE = 20;

/** Security incidents raised by the detection rules, newest first. */
@Component({
  selector: 'app-incident-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, UiCard, UiButton, UiTable, UiPagination, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Security Incidents</h1>
          <p class="text-caption">Suspicious activity found by the automatic detection rules, and what was done about it.</p></div>
        <app-button icon="refresh" variant="stroked" [loading]="loading()" (pressed)="load()">Refresh</app-button>
      </header>

      <app-card>
        <div class="filters">
          <label class="f"><span class="f__l">Status</span>
            <select class="f__s" id="filter-status" [value]="status() ?? ''" (change)="setStatus($any($event.target).value)">
              <option value="">All</option>
              @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
            </select></label>
          <label class="f"><span class="f__l">Severity</span>
            <select class="f__s" id="filter-severity" [value]="severity() ?? ''" (change)="setSeverity($any($event.target).value)">
              <option value="">All</option>
              @for (s of severities; track s) { <option [value]="s">{{ s }}</option> }
            </select></label>
          <label class="f"><span class="f__l">Rule</span>
            <select class="f__s" id="filter-rule" [value]="rule() ?? ''" (change)="setRule($any($event.target).value)">
              <option value="">All</option>
              @for (r of rules; track r) { <option [value]="r">{{ ruleLabel(r) }}</option> }
            </select></label>
        </div>
      </app-card>

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      <app-table
        [columns]="columns" [rows]="page().content" [loading]="loading()"
        [startIndex]="page().page * page().size"
        emptyTitle="No incidents" emptyHint="Nothing matches these filters."
        (rowClick)="open($event)">
        <ng-template #row let-i>
          <td class="text-caption"><a [routerLink]="['/security/incidents', i.id]">{{ i.detectedAt | date: 'medium' }}</a></td>
          <td>
            <div>{{ i.userDisplayName || i.userEmail || '—' }}</div>
            <div class="text-caption">{{ i.userDisplayName ? i.userEmail : '' }}</div>
          </td>
          <td>{{ ruleLabel(i.rule) }}</td>
          <td><app-status-badge [value]="i.severity" [tone]="severityTone(i.severity)" /></td>
          <td><app-status-badge [value]="i.status" [tone]="statusTone(i.status)" /></td>
          <td class="text-caption">{{ i.autoAction || '—' }}</td>
        </ng-template>
      </app-table>

      <app-pagination [page]="page()" (pageChange)="goToPage($event)" />
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .filters { display:flex; gap:16px; flex-wrap:wrap; }
    .f { display:flex; flex-direction:column; gap:6px; flex:1 1 180px; }
    .f__l { font:500 16px var(--font-sans); color:var(--content-fg); }
    .f__s { height:42px; padding:0 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 17px var(--font-sans); color:var(--content-fg); }
    .error { background:var(--danger-bg); color:var(--danger); padding:10px 12px; border-radius:10px; font:500 16px var(--font-sans); }
    a { color:var(--brand-600); }
  `]
})
export class IncidentList implements OnInit {
  private readonly service = inject(SecurityIncidentService);
  private readonly router = inject(Router);
  private readonly breadcrumb = inject(BreadcrumbService);

  get statuses() { return INCIDENT_STATUSES; }
  get severities() { return INCIDENT_SEVERITIES; }
  get rules() { return INCIDENT_RULE_CODES; }
  ruleLabel(rule: string): string { return ruleLabel(rule); }
  readonly severityTone = (s: IncidentSeverity) => SEVERITY_TONE[s];
  readonly statusTone = (s: IncidentStatus) => STATUS_TONE[s];

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(emptyPage<SecurityIncident>());
  readonly status = signal<IncidentStatus | null>(null);
  readonly severity = signal<IncidentSeverity | null>(null);
  readonly rule = signal<IncidentRule | null>(null);

  readonly columns: TableColumn<SecurityIncident>[] = [
    { key: 'detectedAt', header: 'Detected' },
    { key: 'user', header: 'User' },
    { key: 'rule', header: 'Rule' },
    { key: 'severity', header: 'Severity' },
    { key: 'status', header: 'Status' },
    { key: 'autoAction', header: 'Auto action' }
  ];

  private pageIndex = 0;
  /** Guards against a slow earlier response overwriting a newer one. */
  private requestSeq = 0;

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Security' }, { label: 'Security Incidents' }]);
    this.load();
  }

  setStatus(v: string): void { this.status.set((v || null) as IncidentStatus | null); this.reset(); }
  setSeverity(v: string): void { this.severity.set((v || null) as IncidentSeverity | null); this.reset(); }
  setRule(v: string): void { this.rule.set((v || null) as IncidentRule | null); this.reset(); }
  goToPage(index: number): void { this.pageIndex = index; this.load(); }
  open(i: SecurityIncident): void { this.router.navigate(['/security/incidents', i.id]); }

  private reset(): void { this.pageIndex = 0; this.load(); }

  load(): void {
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.error.set(null);
    const filter: IncidentFilter = {
      page: this.pageIndex, size: PAGE_SIZE,
      ...(this.status() ? { status: this.status()! } : {}),
      ...(this.severity() ? { severity: this.severity()! } : {}),
      ...(this.rule() ? { rule: this.rule()! } : {})
    };
    this.service.list(filter).subscribe({
      next: (p) => { if (seq !== this.requestSeq) return; this.page.set(p); this.loading.set(false); },
      error: () => {
        if (seq !== this.requestSeq) return;
        this.page.set(emptyPage());
        this.error.set('Could not load security incidents. Please try again.');
        this.loading.set(false);
      }
    });
  }
}
