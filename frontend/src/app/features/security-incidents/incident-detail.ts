import { ChangeDetectionStrategy, Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { SecurityIncidentService } from './security-incident.service';
import { IncidentAction, SecurityIncident } from './security-incident.model';
import { SEVERITY_TONE, STATUS_TONE, evidenceText, humanKey, ruleDescription, ruleLabel } from './incident-labels';
import { IncidentActionDialog, IncidentActionDialogData, IncidentActionResult } from './incident-action-dialog';

interface ActionCopy extends IncidentActionDialogData { done: string; }

const ACTIONS: Record<IncidentAction, ActionCopy> = {
  ban: {
    title: 'Ban this user?', danger: true, confirmLabel: 'Ban user', done: 'User banned.',
    message: 'The user is signed out of every session and cannot sign in until the ban is lifted.'
  },
  dismiss: {
    title: 'Dismiss this incident?', confirmLabel: 'Dismiss', done: 'Incident dismissed.',
    message: 'Marks the incident as a false alarm or acceptable. You can still ban the user later.'
  },
  unban: {
    title: 'Unban this user?', confirmLabel: 'Unban user', done: 'User unbanned.',
    message: 'The user gets their access back and can sign in again immediately.'
  }
};

/** One incident as a printable report: who, what happened, what was done. */
@Component({
  selector: 'app-incident-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  // None: the print rules must reach the app shell outside this component.
  encapsulation: ViewEncapsulation.None,
  imports: [DatePipe, RouterLink, UiCard, UiButton, StatusBadge],
  template: `
    <div class="si-sheet">
      <header class="si-head">
        <div>
          <a class="si-back si-noprint" routerLink="/security/incidents">&larr; All incidents</a>
          <h1 class="text-h1">Security incident report</h1>
        </div>
        <div class="si-actions si-noprint">
          <app-button icon="refresh" variant="stroked" [loading]="loading()" (pressed)="load()">Refresh</app-button>
          <app-button icon="print" variant="stroked" (pressed)="print()">Print / Save as PDF</app-button>
          @if (canUpdate() && incident(); as i) {
            @if (i.status === 'OPEN') {
              <app-button variant="stroked" [loading]="busy()" (pressed)="act('dismiss')">Dismiss</app-button>
            }
            @if ((i.status === 'OPEN' || i.status === 'DISMISSED') && i.userId) {
              <app-button variant="danger" [loading]="busy()" (pressed)="act('ban')">Ban user</app-button>
            }
            @if (i.status === 'BANNED') {
              <app-button [loading]="busy()" (pressed)="act('unban')">Unban user</app-button>
            }
          }
        </div>
      </header>

      @if (error()) { <p class="si-error" role="alert">{{ error() }}</p> }

      @if (incident(); as i) {
        <app-card>
          <div class="si-title">
            <h2 class="text-h2">{{ ruleLabel(i.rule) }}</h2>
            <app-status-badge [value]="i.severity" [tone]="severityTone(i)" />
            <app-status-badge [value]="i.status" [tone]="statusTone(i)" />
          </div>
          <p class="text-caption">Detected {{ i.detectedAt | date: 'medium' }} &middot; Incident {{ i.id }}</p>
        </app-card>

        <app-card title="Who">
          @if (i.userId) {
            <dl class="si-kv">
              <dt>Name</dt><dd>{{ i.userDisplayName || '—' }}</dd>
              <dt>Email</dt><dd>{{ i.userEmail || '—' }}</dd>
              <dt>Roles</dt><dd>{{ i.userRoles.length ? i.userRoles.join(', ') : '—' }}</dd>
              <dt>Branch</dt><dd>{{ i.branchName || '—' }}</dd>
            </dl>
          } @else {
            <p class="text-body">This incident is not tied to a single user (for example, an attack from one network address).</p>
          }
        </app-card>

        <app-card title="What happened">
          <p class="text-body">{{ describe(i) }}</p>
          @if (i.evidenceUnreadable) {
            <p class="si-warn" role="status">The recorded evidence for this incident could not be read, so details are unavailable.</p>
          }
          @if (evidenceRows().length) {
            <dl class="si-kv">
              @for (row of evidenceRows(); track row.key) {
                <dt>{{ row.label }}</dt><dd>{{ row.value }}</dd>
              }
            </dl>
          } @else if (!i.evidenceUnreadable) {
            <p class="text-caption">No additional evidence was recorded.</p>
          }
        </app-card>

        <app-card title="Actions taken">
          <dl class="si-kv">
            <dt>Automatic action</dt><dd>{{ i.autoAction || 'None' }}</dd>
            <dt>Resolved by</dt><dd>{{ i.resolvedByName || (i.resolvedBy ? i.resolvedBy : '—') }}</dd>
            <dt>Resolved at</dt><dd>{{ i.resolvedAt ? (i.resolvedAt | date: 'medium') : '—' }}</dd>
            <dt>Note</dt><dd>{{ i.resolutionNote || '—' }}</dd>
          </dl>
        </app-card>

        <app-card title="Timeline">
          <ol class="si-timeline">
            @for (e of timeline(); track e.at + e.text) {
              <li><span class="si-timeline__at">{{ e.at | date: 'medium' }}</span> {{ e.text }}</li>
            }
          </ol>
        </app-card>
      } @else if (loading()) {
        <p class="text-caption" role="status">Loading…</p>
      }
    </div>
  `,
  styles: [`
    .si-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap; margin-bottom:16px; }
    .si-actions { display:flex; gap:10px; flex-wrap:wrap; }
    .si-back { color:var(--brand-600); font:500 16px var(--font-sans); }
    .si-title { display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
    .si-kv { display:grid; grid-template-columns:max-content 1fr; gap:8px 24px; margin:0; font:400 17px var(--font-sans); }
    .si-kv dt { color:var(--content-muted); }
    .si-kv dd { margin:0; font-weight:600; overflow-wrap:anywhere; }
    .si-warn { background:var(--warning-bg); color:var(--content-fg); padding:10px 12px; border-radius:10px; }
    .si-error { background:var(--danger-bg); color:var(--danger); padding:10px 12px; border-radius:10px; font:500 16px var(--font-sans); }
    .si-timeline { margin:0; padding-left:20px; font:400 17px var(--font-sans); display:flex; flex-direction:column; gap:6px; }
    .si-timeline__at { color:var(--content-muted); margin-right:8px; }
    .si-sheet app-card { display:block; margin-bottom:16px; }
    @media print {
      body:has(.si-sheet) * { visibility:hidden !important; }
      body:has(.si-sheet) .si-sheet, body:has(.si-sheet) .si-sheet * { visibility:visible !important; }
      body:has(.si-sheet) .si-sheet { position:absolute; left:0; top:0; width:100%; }
      body:has(.si-sheet) .si-noprint { display:none !important; }
      html:has(.si-sheet), body:has(.si-sheet), body:has(.si-sheet) .shell, body:has(.si-sheet) .shell__content {
        height:auto !important; overflow:visible !important; }
    }
  `]
})
export class IncidentDetail implements OnInit {
  private readonly service = inject(SecurityIncidentService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly breadcrumb = inject(BreadcrumbService);

  readonly incident = signal<SecurityIncident | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly ruleLabel = ruleLabel;
  readonly severityTone = (i: SecurityIncident) => SEVERITY_TONE[i.severity];
  readonly statusTone = (i: SecurityIncident) => STATUS_TONE[i.status];
  readonly describe = (i: SecurityIncident) => ruleDescription(i.rule);

  /** Mirrors the backend rule: SECURITY_INCIDENT_UPDATE, or the PLATFORM_ADMIN role. */
  readonly canUpdate = computed(() =>
    this.perms.hasRole(AppRole.PLATFORM_ADMIN) || this.perms.hasPermission('SECURITY_INCIDENT_UPDATE'));

  /** Evidence as plain text rows; values are only ever interpolated, never injected as HTML. */
  readonly evidenceRows = computed(() =>
    Object.entries(this.incident()?.evidence ?? {}).map(([key, value]) => ({
      key, label: humanKey(key), value: evidenceText(value)
    })));

  readonly timeline = computed(() => {
    const i = this.incident();
    if (!i) return [];
    const events = [{ at: i.detectedAt, text: `Detected by rule "${ruleLabel(i.rule)}".` }];
    if (i.autoAction) events.push({ at: i.detectedAt, text: `Automatic action: ${i.autoAction}.` });
    if (i.resolvedAt) {
      const by = i.resolvedByName || i.resolvedBy;
      events.push({ at: i.resolvedAt, text: `Marked ${i.status.toLowerCase()}${by ? ' by ' + by : ''}.` });
    }
    return events;
  });

  private id = '';

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Security' }, { label: 'Security Incidents' }, { label: 'Report' }]);
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.get(this.id).subscribe({
      next: (i) => { this.incident.set(i); this.loading.set(false); },
      error: () => { this.error.set('Could not load this incident.'); this.loading.set(false); }
    });
  }

  act(action: IncidentAction): void {
    const { done, ...data } = ACTIONS[action];
    this.dialog
      .open<IncidentActionDialog, IncidentActionDialogData, IncidentActionResult | undefined>(
        IncidentActionDialog, { data, autoFocus: true, panelClass: 'app-dialog' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        this.busy.set(true);
        this.service[action](this.id, result.note).subscribe({
          next: () => { this.busy.set(false); this.notify.success(done); this.load(); },
          error: (err) => {
            this.busy.set(false);
            // The global interceptor toasts everything except 400/422 (e.g. a wrong-state 422).
            if (err?.status === 400 || err?.status === 422) {
              this.notify.error(err?.error?.message ?? 'The action could not be completed.');
            }
            this.load();
          }
        });
      });
  }

  print(): void { window.print(); }
}
