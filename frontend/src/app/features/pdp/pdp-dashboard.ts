import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl } from '@angular/forms';
import { Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { PdpDashboard } from '@core/models/pdp.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { StatisticCard } from '@shared/components/statistic-card/statistic-card';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { PdpBillingService } from './pdp-billing.service';
import { PdpService } from './pdp.service';

const EMPTY: PdpDashboard = {
  branchId: null, branchName: null,
  pickups: { byStatus: {}, open: 0, unaccepted: 0, pickedUpThisMonth: 0 },
  deliveries: { atPartner: 0, outForDelivery: 0, delivered: 0, podPending: 0, deliveredThisMonth: 0 },
  earnings: { accruedAmount: 0, accruedCount: 0, unratedCount: 0, billedThisMonth: 0, earnedThisMonth: 0 },
  statements: { draft: 0, approved: 0, paid: 0, outstandingPayable: 0, paidThisMonth: 0 },
  waitingPickups: [], partners: []
};

/** Pipeline order of a pickup, left to right. Side exits (failed / cancelled) follow. */
const PICKUP_STAGES: { key: string; label: string }[] = [
  { key: 'REQUESTED', label: 'Requested' }, { key: 'ASSIGNED', label: 'Assigned' }, { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'OUT_FOR_PICKUP', label: 'Out for pickup' }, { key: 'PICKED_UP', label: 'Picked up' }, { key: 'BOOKED', label: 'Booked' },
  { key: 'FAILED', label: 'Failed' }, { key: 'CANCELLED', label: 'Cancelled' }
];

/**
 * Pickup-Delivery Partner dashboard. The server decides the scope: a partner login always gets its
 * own figures; a company admin gets every partner, or one through the picker (then no per-partner table).
 */
@Component({
  selector: 'app-pdp-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, UiCard, UiButton, UiSelect, StatisticCard, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Partner Operations Dashboard</h1>
          <p class="text-caption">{{ d().branchName ? d().branchName : (isAdmin() ? 'All pickup-delivery partners' : 'Your pickups, deliveries and earnings') }} — this month where dated.</p></div>
        <div class="head__actions">
          @if (isAdmin()) {
            <app-select [control]="partnerCtl" label="Partner" [options]="partnerOpts()" [allowEmpty]="true" emptyLabel="All partners" placeholder="All partners" />
          }
          <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
        </div>
      </header>

      <h2 class="sec">Pickups</h2>
      <div class="grid">
        <app-statistic-card label="Open pickups" icon="inventory_2" tone="brand" [value]="d().pickups.open" [loading]="loading()" />
        <app-statistic-card label="Awaiting acceptance" icon="hourglass_top" tone="warning" [value]="d().pickups.unaccepted" [loading]="loading()" />
        <app-statistic-card label="Picked up this month" icon="local_shipping" tone="success" [value]="d().pickups.pickedUpThisMonth" [loading]="loading()" />
        <app-statistic-card label="Failed" icon="error_outline" tone="danger" [value]="count('FAILED')" [loading]="loading()" />
      </div>

      <app-card title="Pickup pipeline" subtitle="Every request by where it stands now.">
        <div class="pipe">
          @for (s of stages; track s.key) {
            <div class="pipe__cell" [class.pipe__cell--zero]="!count(s.key)">
              <span class="pipe__n">{{ count(s.key) }}</span><span class="pipe__l">{{ s.label }}</span>
            </div>
          }
        </div>
      </app-card>

      <h2 class="sec">Deliveries</h2>
      <div class="grid">
        <app-statistic-card label="At partner (in scan)" icon="move_to_inbox" tone="info" [value]="d().deliveries.atPartner" [loading]="loading()" />
        <app-statistic-card label="Out for delivery" icon="moving" tone="brand" [value]="d().deliveries.outForDelivery" [loading]="loading()" />
        <app-statistic-card label="Delivered" icon="task_alt" tone="success" [value]="d().deliveries.delivered" [loading]="loading()" />
        <app-statistic-card label="POD pending approval" icon="photo_camera" tone="warning" [value]="d().deliveries.podPending" [loading]="loading()" />
      </div>

      <h2 class="sec">Money</h2>
      <div class="grid">
        <app-statistic-card label="Accrued, unbilled" icon="payments" tone="brand" prefix="₹" [value]="money(d().earnings.accruedAmount)" [loading]="loading()" />
        <app-statistic-card label="Earned this month" icon="trending_up" tone="success" prefix="₹" [value]="money(d().earnings.earnedThisMonth)" [loading]="loading()" />
        <app-statistic-card label="Payable (unpaid statements)" icon="receipt_long" tone="warning" prefix="₹" [value]="money(d().statements.outstandingPayable)" [loading]="loading()" />
        <app-statistic-card label="Paid this month" icon="account_balance_wallet" tone="success" prefix="₹" [value]="money(d().statements.paidThisMonth)" [loading]="loading()" />
      </div>
      <div class="notes">
        <span>Statements: <strong>{{ d().statements.draft }}</strong> draft · <strong>{{ d().statements.approved }}</strong> approved · <strong>{{ d().statements.paid }}</strong> paid</span>
        @if (d().earnings.unratedCount) {
          <span class="warn">{{ d().earnings.unratedCount }} earning(s) have no rate card yet (amount 0) —
            <a (click)="go('/pdp/earnings')">re-price</a></span>
        }
      </div>

      <app-card [title]="'Pickups waiting on a partner (' + d().pickups.unaccepted + ')'" subtitle="Oldest first. Requested = no partner yet; Assigned = partner has not accepted.">
        @if (!d().waitingPickups.length) { <p class="empty">Nothing is waiting.</p> } @else {
          <div class="tbl__wrap"><table class="tbl">
            <thead><tr><th>Pickup</th><th>Status</th><th>Contact</th><th>Where</th><th>Partner</th><th>Raised</th></tr></thead>
            <tbody>
              @for (p of d().waitingPickups; track p.id) {
                <tr class="click" (click)="go('/pdp/pickups')">
                  <td class="b">{{ p.pickupNo }}</td>
                  <td><app-status-badge [value]="p.status" [label]="p.status" /></td>
                  <td>{{ p.contactName }}</td>
                  <td>{{ p.city || '—' }} {{ p.pincode }}</td>
                  <td>{{ p.pdpBranchName || '—' }}</td>
                  <td>{{ p.createdAt.slice(0, 10) }}</td>
                </tr>
              }
            </tbody></table></div>
        }
      </app-card>

      @if (showPartners()) {
        <app-card title="Partners" subtitle="Sorted by accrued, unbilled earnings.">
          <div class="tbl__wrap"><table class="tbl">
            <thead><tr><th>Partner</th><th class="r">Open pickups</th><th class="r">Picked up (month)</th><th class="r">Parcels with partner</th>
              <th class="r">Delivered (month)</th><th class="r">Accrued ₹</th><th class="r">Payable ₹</th></tr></thead>
            <tbody>
              @for (p of d().partners; track p.branchId) {
                <tr class="click" (click)="partnerCtl.setValue(p.branchId)">
                  <td class="b">{{ p.branchName }}<div class="text-caption">{{ p.branchCode }}</div></td>
                  <td class="r">{{ p.openPickups }}</td><td class="r">{{ p.pickedUpThisMonth }}</td><td class="r">{{ p.atPartner }}</td>
                  <td class="r">{{ p.deliveredThisMonth }}</td>
                  <td class="r">{{ p.accruedAmount | number: '1.2-2' }}</td><td class="r">{{ p.outstandingPayable | number: '1.2-2' }}</td>
                </tr>
              }
            </tbody></table></div>
        </app-card>
      }

      <app-card title="Quick actions">
        <div class="qa">
          @if (isAdmin()) { <app-button icon="add" (pressed)="go('/pdp/pickups/new')">New pickup request</app-button> }
          <app-button variant="stroked" icon="inventory_2" (pressed)="go('/pdp/pickups')">Pickup requests</app-button>
          <app-button variant="stroked" icon="moving" (pressed)="go('/pdp/deliveries')">Deliveries</app-button>
          <app-button variant="stroked" icon="payments" (pressed)="go('/pdp/earnings')">Earnings</app-button>
          <app-button variant="stroked" icon="receipt_long" (pressed)="go('/pdp/billing')">Billing</app-button>
        </div>
      </app-card>
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; flex-wrap:wrap; }
    .head__actions { display:flex; align-items:flex-end; gap:12px; }
    .head__actions app-select { min-width:240px; }
    .sec { font:600 14px var(--font-sans); color:var(--content-muted); text-transform:uppercase; letter-spacing:.05em; margin:24px 0 10px; }
    .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(220px, 1fr)); gap:16px; margin-bottom:16px; }
    .pipe { display:grid; grid-template-columns:repeat(auto-fit, minmax(110px, 1fr)); gap:10px; }
    .pipe__cell { display:flex; flex-direction:column; gap:2px; padding:10px 12px; border:1px solid var(--surface-border); border-radius:10px; }
    .pipe__cell--zero { opacity:.5; }
    .pipe__n { font:700 22px var(--font-sans); } .pipe__l { font:400 12px var(--font-sans); color:var(--content-muted); }
    .notes { display:flex; flex-wrap:wrap; gap:8px 24px; font:400 13px var(--font-sans); color:var(--content-muted); margin:0 0 16px; }
    .notes strong { color:var(--content-fg); } .warn { color:#b45309; } .notes a { cursor:pointer; text-decoration:underline; }
    app-card { display:block; margin-bottom:16px; }
    .tbl__wrap { overflow-x:auto; }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; font:500 11px var(--font-sans); color:var(--content-muted); text-transform:uppercase; letter-spacing:.04em; padding:0 10px 8px; }
    .tbl td { padding:8px 10px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl .b { font-weight:600; } .tbl .r { text-align:right; } .tbl .click { cursor:pointer; } .tbl .click:hover { background:var(--surface-hover, rgba(0,0,0,.03)); }
    .qa { display:flex; flex-wrap:wrap; gap:10px; }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
  `]
})
export class PdpDashboardPage implements OnInit {
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly router = inject(Router);
  private readonly billing = inject(PdpBillingService);
  private readonly pdp = inject(PdpService);

  protected readonly stages = PICKUP_STAGES;
  protected readonly partnerCtl = new FormControl<string>('', { nonNullable: true });
  protected readonly partnerOpts = signal<SelectOption[]>([]);
  protected readonly d = signal<PdpDashboard>(EMPTY);
  protected readonly loading = signal(true);
  protected readonly isAdmin = computed(() => this.perms.hasRole(AppRole.COMPANY_ADMIN));
  protected readonly showPartners = computed(() => this.isAdmin() && this.d().partners.length > 0);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Pickup Delivery Partner Management' }, { label: 'Dashboard' }]);
    if (this.isAdmin()) {
      this.pdp.partners({ page: 0, size: 200 }).subscribe((r) =>
        this.partnerOpts.set(r.content.map((b) => ({ value: b.id, label: `${b.branchName} (${b.branchCode})` }))));
      this.partnerCtl.valueChanges.subscribe(() => this.load());
    }
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.billing.dashboard(this.isAdmin() ? this.partnerCtl.value || undefined : undefined).subscribe({
      next: (r) => { this.d.set(r); this.loading.set(false); },
      error: () => { this.loading.set(false); this.notify.error('Could not load the partner dashboard.'); }
    });
  }

  protected count(key: string): number { return this.d().pickups.byStatus[key] ?? 0; }
  protected money(n: number): string { return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
  protected go(path: string): void { this.router.navigateByUrl(path); }
}
