import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { StatisticCard } from '@shared/components/statistic-card/statistic-card';
import { HubOperationsService } from './hub-operations.service';
import { HubDashboardStats } from './hub-operations.model';

/**
 * Hub Dashboard — the nine figures `HubOperationsService.dashboard()` returns, plus the
 * quick actions the spec asks for (In Scan / Sort / Create Load Sheet / Out Scan /
 * Dispatch / Exceptions). Scoped to the signed-in user's own hub branch, the same
 * "no picker, my own branch" pattern every other Hub Operations screen uses.
 */
@Component({
  selector: 'app-hub-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiCard, UiButton, StatisticCard],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Hub Dashboard</h1><p class="text-caption">Today's figures for your hub.</p></div>
        <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
      </header>

      @if (!myBranchId) {
        <app-card><p class="empty">No hub assigned — ask an admin.</p></app-card>
      } @else {
        <div class="grid">
          <app-statistic-card label="Today's Inbound" icon="call_received" tone="info" [value]="s().todaysInbound" [loading]="loading()" />
          <app-statistic-card label="Pending In Scan" icon="pending_actions" tone="warning" [value]="s().pendingInScan" [loading]="loading()" />
          <app-statistic-card label="Shipments At Hub" icon="inventory_2" tone="brand" [value]="s().shipmentsAtHub" [loading]="loading()" />
          <app-statistic-card label="Pending Sorting" icon="sort" tone="warning" [value]="s().pendingSorting" [loading]="loading()" />
          <app-statistic-card label="Ready For Dispatch" icon="local_shipping" tone="success" [value]="s().readyForDispatch" [loading]="loading()" />
          <app-statistic-card label="Dispatched Today" icon="send" tone="success" [value]="s().dispatchedToday" [loading]="loading()" />
          <app-statistic-card label="Pending Exceptions" icon="report_problem" tone="danger" [value]="s().pendingExceptions" [loading]="loading()" />
          <app-statistic-card label="Today's Load Sheets" icon="qr_code_scanner" tone="info" [value]="s().todaysLoadSheets" [loading]="loading()" />
        </div>

        <app-card title="Quick Actions">
          <div class="qa">
            <app-button icon="move_to_inbox" (pressed)="go('/hub-operations/in-scan')">In Scan</app-button>
            <app-button icon="sort" (pressed)="go('/hub-operations/sorting')">Sort Shipments</app-button>
            <app-button icon="qr_code_scanner" (pressed)="go('/hub-operations/load-sheet')">Create Load Sheet</app-button>
            <app-button icon="outbox" (pressed)="go('/hub-operations/out-scan')">Out Scan</app-button>
            <app-button icon="outbound" (pressed)="go('/hub-operations/dispatch')">Dispatch</app-button>
            <app-button icon="report_problem" (pressed)="go('/hub-operations/exceptions')">Exceptions</app-button>
          </div>
        </app-card>
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(200px, 1fr)); gap:16px; }
    .qa { display:flex; flex-wrap:wrap; gap:10px; }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
  `]
})
export class HubDashboard implements OnInit {
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly service = inject(HubOperationsService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;
  readonly loading = signal(true);
  readonly s = signal<HubDashboardStats>({
    todaysInbound: 0, pendingInScan: 0, shipmentsAtHub: 0, pendingSorting: 0,
    readyForDispatch: 0, dispatchedToday: 0, pendingExceptions: 0, todaysLoadSheets: 0
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Hub Operations' }, { label: 'Dashboard' }]);
    this.load();
  }

  load(): void {
    if (!this.myBranchId) return;
    this.loading.set(true);
    this.service.dashboard(this.myBranchId).subscribe({
      next: (stats) => { this.s.set(stats); this.loading.set(false); },
      error: () => { this.loading.set(false); this.notify.error('Could not load the hub dashboard.'); }
    });
  }

  protected go(path: string): void {
    this.router.navigateByUrl(path);
  }
}
