import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { BusinessDashboard, RevenuePoint } from '@core/models/pricing-zone.model';
import { BILLING_FREQUENCY_LABELS, BillingFrequency, CustomerPaymentMode, PAYMENT_MODE_LABELS } from '@core/models/customer.model';
import { StatisticCard } from '@shared/components/statistic-card/statistic-card';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { BusinessCustomerService } from './business-customer.service';

interface Bar { label: string; count: number; pct: number; }

/**
 * TBB Customer dashboard: the company-wide picture of business accounts — how many, how
 * they pay and are billed, and per-customer booking activity. Every number comes from `GET /business-customers/dashboard`;
 * nothing is computed beyond bar widths. Bookings are matched on the consignor's mobile number.
 */
@Component({
  selector: 'app-business-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, StatisticCard, UiCard, UiLoader, UiButton],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">TBB Customer Dashboard</h1>
          <p class="text-caption">Business accounts, payment terms and booking activity at a glance.</p></div>
        <div class="page__actions">
          <app-button variant="stroked" icon="refresh" [loading]="loading()" (pressed)="load()">Refresh</app-button>
          <app-button icon="add" (pressed)="go('/customers/new', { type: 'BUSINESS' })">New Customer</app-button>
        </div>
      </header>

      @if (loading() && !data()) {
        <app-loader [minHeight]="300" caption="Loading overview…" />
      } @else if (data(); as d) {
        <div class="tiles">
          <app-statistic-card label="TBB Customers" icon="business_center" tone="brand" [value]="d.totals.customers" />
          <app-statistic-card label="Active Customers" icon="check_circle" tone="success" [value]="d.totals.activeCustomers" />
          <app-statistic-card label="Inactive Customers" icon="pause_circle" tone="warning" [value]="d.totals.inactiveCustomers" />
          <app-statistic-card label="Bookings This Month" icon="local_shipping" tone="brand" [value]="d.totals.bookingsThisMonth" />
          <app-statistic-card label="Total Bookings" icon="inventory_2" tone="info" [value]="d.totals.bookingsTotal" />
          <app-statistic-card label="Total Qty (Packages)" icon="widgets" tone="brand" [value]="d.totals.totalQuantity" />
          <app-statistic-card label="Total Weight (kg)" icon="scale" tone="info" [value]="kg(d.totals.totalWeight)" />
          <app-statistic-card label="Total Business" icon="payments" tone="success" prefix="₹" [value]="inr(d.totals.totalBusiness)" />
          <app-statistic-card label="Payment Pending" icon="hourglass_top" [tone]="d.totals.paymentPending > 0 ? 'danger' : 'success'" prefix="₹" [value]="inr(d.totals.paymentPending)" />
        </div>

        <div class="grid2">
          <app-card title="Payment Mode" subtitle="Default payment mode per TBB customer">
            @for (b of modeBars(); track b.label) {
              <div class="bar"><span class="bar__l">{{ b.label }}</span>
                <span class="bar__t"><span class="bar__f" [style.width.%]="b.pct"></span></span><span class="bar__n">{{ b.count }}</span></div>
            }
          </app-card>
          <app-card title="Billing Frequency" subtitle="How often customers are invoiced">
            @for (b of freqBars(); track b.label) {
              <div class="bar"><span class="bar__l">{{ b.label }}</span>
                <span class="bar__t"><span class="bar__f bar__f--alt" [style.width.%]="b.pct"></span></span><span class="bar__n">{{ b.count }}</span></div>
            }
          </app-card>
        </div>

        <app-card title="Revenue Trend" subtitle="Net booking amount per month (cancelled excluded)">
          <div class="trend__ctl">
            <label>Customer
              <select (change)="setCustomer($any($event.target).value)">
                <option value="">All TBB customers</option>
                @for (c of d.customers; track c.customerId) { <option [value]="c.customerId">{{ c.displayName }}</option> }
              </select></label>
            <label>Period
              <select (change)="setMonths(+$any($event.target).value)">
                @for (m of [3, 6, 12]; track m) { <option [value]="m" [selected]="m === months()">Last {{ m }} months</option> }
              </select></label>
          </div>
          @if (!trend().length) {
            <p class="empty">No data.</p>
          } @else {
            <svg class="trend" viewBox="0 0 600 220" role="img" aria-label="Monthly revenue bar chart">
              @for (p of bars2(); track p.month) {
                <rect [attr.x]="p.x" [attr.y]="p.y" [attr.width]="p.w" [attr.height]="p.h" rx="3" class="trend__bar">
                  <title>{{ p.month }}: ₹{{ p.amount | number: '1.2-2' }} · {{ p.bookings }} bookings</title></rect>
                <text [attr.x]="p.x + p.w / 2" y="212" text-anchor="middle" class="trend__lbl">{{ p.label }}</text>
                <text [attr.x]="p.x + p.w / 2" [attr.y]="p.y - 5" text-anchor="middle" class="trend__val">{{ p.short }}</text>
              }
            </svg>
          }
        </app-card>

        <app-card title="This Month by Customer" [subtitle]="monthTitle + ' · net booking amount per TBB customer (cancelled excluded)'">
          @if (!monthHasData()) {
            <p class="empty">No bookings from TBB customers yet this month.</p>
          }
          <svg class="trend" [attr.viewBox]="'0 0 ' + monthW() + ' 250'" role="img" aria-label="This month's revenue per TBB customer">
            <line x1="0" [attr.x2]="monthW()" y1="195" y2="195" class="trend__axis" />
            @for (p of monthBars(); track p.id) {
              <rect [attr.x]="p.x" [attr.y]="p.y" [attr.width]="p.w" [attr.height]="p.h" rx="3" class="trend__bar">
                <title>{{ p.name }}: ₹{{ p.amount | number: '1.2-2' }} · {{ p.bookings }} bookings</title></rect>
              <text [attr.x]="p.x + p.w / 2" [attr.y]="p.y - 5" text-anchor="middle" class="trend__val">{{ p.short }}</text>
              <text [attr.x]="p.x + p.w / 2" y="212" text-anchor="middle" class="trend__lbl">{{ p.l1 }}</text>
              <text [attr.x]="p.x + p.w / 2" y="226" text-anchor="middle" class="trend__lbl">{{ p.l2 }}</text>
            }
          </svg>
        </app-card>

        <app-card title="Customers" [subtitle]="'Matched on the consignor mobile number. Pending = unpaid invoice balances + un-invoiced To Be Billed / To Pay / COD bookings.'">
          @if (!d.customers.length) {
            <p class="empty">No TBB customers yet.</p>
          } @else {
            <div class="tw"><table class="t">
              <thead><tr><th>Customer</th><th>Payment</th><th>Billing</th><th class="n">This month</th><th class="n">Bookings</th><th class="n">Qty</th><th class="n">Weight (kg)</th><th class="n">Business</th><th class="n">Pending</th><th>Last booking</th><th></th></tr></thead>
              <tbody>
                @for (c of d.customers; track c.customerId) {
                  <tr>
                    <td><a [routerLink]="['/customers', c.customerId]"><strong>{{ c.displayName }}</strong></a>
                      <div class="sub">{{ c.customerCode }} · {{ c.mobile }}@if (c.status !== 'ACTIVE') { · Inactive }</div></td>
                    <td>{{ mode(c.paymentMode) }}</td><td>{{ freq(c.billingFrequency) }}</td>
                    <td class="n">{{ c.bookingsThisMonth }}</td><td class="n">{{ c.bookingsTotal }}</td>
                    <td class="n">{{ c.quantity }}</td><td class="n">{{ c.weight | number: '1.0-3' }}</td>
                    <td class="n">₹{{ c.businessAmount | number: '1.2-2' }}</td>
                    <td class="n" [class.due]="c.pendingAmount > 0">₹{{ c.pendingAmount | number: '1.2-2' }}</td>
                    <td>{{ c.lastBookingAt ? (c.lastBookingAt | date: 'mediumDate') : '—' }}</td>
                    <td class="n"><a routerLink="/business-customer/booking">Book</a></td>
                  </tr>
                }
              </tbody>
            </table></div>
          }
        </app-card>
      } @else {
        <app-card><p class="empty">Could not load the overview.</p></app-card>
      }
    </div>
  `,
  styles: [`
    .tiles { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:16px; margin-bottom:16px; }
    .grid2 { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; margin-bottom:16px; }
    app-card { display:block; margin-bottom:16px; } .grid2 app-card { margin-bottom:0; }
    .bar { display:grid; grid-template-columns:110px 1fr 36px; align-items:center; gap:12px; padding:6px 0; font:500 17px var(--font-sans); }
    .bar--wide { grid-template-columns:200px 1fr 90px; } .bar--wide .bar__l { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .bar__t { height:10px; background:var(--surface-muted); border-radius:999px; overflow:hidden; }
    .bar__f { display:block; height:100%; background:var(--brand-600); border-radius:999px; min-width:2px; }
    .bar__f--alt { background:var(--info, var(--brand-400)); }
    .bar__n { text-align:right; font-weight:700; }
    .t a { color:var(--brand-700); font-weight:600; text-decoration:none; } .t a:hover { text-decoration:underline; }
    .empty { font:400 17px var(--font-sans); color:var(--content-muted); text-align:center; padding:12px; margin:0; }
    .tw { overflow-x:auto; } .t { width:100%; border-collapse:collapse; font:400 17px var(--font-sans); }
    .t th { text-align:left; font:600 15px var(--font-sans); color:var(--content-muted); text-transform:uppercase; letter-spacing:.04em; padding:8px 10px; border-bottom:1px solid var(--surface-border); }
    .t td { padding:10px; border-bottom:1px solid var(--surface-border); vertical-align:top; } .t .n { text-align:right; }
    .trend__ctl { display:flex; gap:16px; flex-wrap:wrap; margin-bottom:12px; font:500 15px var(--font-sans); color:var(--content-muted); }
    .trend__ctl select { display:block; margin-top:4px; padding:6px 10px; font:500 16px var(--font-sans); border:1px solid var(--surface-border); border-radius:8px; background:var(--surface); color:inherit; }
    .trend { width:100%; height:auto; }
    .trend__axis { stroke:var(--surface-border); stroke-width:1; }
    .trend__bar { fill:var(--brand-600); }
    .trend__lbl { font:500 12px var(--font-sans); fill:var(--content-muted); }
    .trend__val { font:600 12px var(--font-sans); fill:currentColor; }
    .due { color:var(--danger); font-weight:700; }
    .sub { font:400 15px var(--font-sans); color:var(--content-muted); }
    @media (max-width:860px){ .grid2 { grid-template-columns:1fr; } }
  `]
})
export class BusinessDashboardPage implements OnInit {
  private readonly service = inject(BusinessCustomerService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly data = signal<BusinessDashboard | null>(null);

  protected readonly monthTitle = new Date().toLocaleString('en-IN', { month: 'long', year: 'numeric' });
  private readonly monthRows = computed(() => [...(this.data()?.customers ?? [])].sort((a, b) => b.monthAmount - a.monthAmount));
  protected readonly monthHasData = computed(() => this.monthRows().some((c) => c.monthAmount > 0));
  protected readonly monthW = computed(() => Math.max(600, this.monthRows().length * 100));
  protected readonly monthBars = computed(() => {
    const cs = this.monthRows();
    const max = Math.max(1, ...cs.map((c) => c.monthAmount));
    const slot = this.monthW() / Math.max(1, cs.length);
    const w = Math.min(56, slot * 0.6);
    return cs.map((c, i) => {
      const h = c.monthAmount > 0 ? Math.max(2, (c.monthAmount / max) * 160) : 0;
      const words = c.displayName.split(' ');
      const l1 = c.displayName.length <= 14 ? c.displayName : words.slice(0, Math.ceil(words.length / 2)).join(' ');
      const l2 = c.displayName.length <= 14 ? '' : words.slice(Math.ceil(words.length / 2)).join(' ');
      return {
        id: c.customerId, name: c.displayName, amount: c.monthAmount, bookings: c.bookingsThisMonth,
        w, h, x: i * slot + (slot - w) / 2, y: 195 - h,
        l1: l1.length > 16 ? l1.slice(0, 15) + '…' : l1, l2: l2.length > 16 ? l2.slice(0, 15) + '…' : l2,
        short: c.monthAmount > 0 ? this.compact(c.monthAmount) : ''
      };
    });
  });
  protected readonly modeBars = computed(() => this.bars(this.data()?.byPaymentMode, (k) => this.mode(k)));
  protected readonly freqBars = computed(() => this.bars(this.data()?.byBillingFrequency, (k) => this.freq(k)));

  protected readonly months = signal(6);
  protected readonly customerId = signal('');
  protected readonly trend = signal<RevenuePoint[]>([]);
  protected readonly bars2 = computed(() => {
    const pts = this.trend();
    const max = Math.max(1, ...pts.map((p) => p.amount));
    const slot = 600 / Math.max(1, pts.length);
    const w = Math.min(60, slot * 0.6);
    return pts.map((p, i) => {
      const h = p.amount > 0 ? Math.max(2, (p.amount / max) * 170) : 0;
      const [y, m] = p.month.split('-');
      return {
        ...p, w, h, x: i * slot + (slot - w) / 2, y: 195 - h,
        label: new Date(+y, +m - 1, 1).toLocaleString('en-IN', { month: 'short', year: '2-digit' }),
        short: p.amount > 0 ? this.compact(p.amount) : ''
      };
    });
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'TBB Customer' }, { label: 'Dashboard' }]);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadTrend();
    this.service.dashboard().subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  protected setCustomer(id: string): void { this.customerId.set(id); this.loadTrend(); }
  protected setMonths(m: number): void { this.months.set(m); this.loadTrend(); }
  private loadTrend(): void {
    this.service.revenueTrend(this.months(), this.customerId() || undefined).subscribe({ next: (t) => this.trend.set(t) });
  }
  private compact(n: number): string {
    return new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 }).format(n);
  }

  protected go(path: string, queryParams?: Record<string, string>): void { this.router.navigate([path], { queryParams }); }

  protected kg(n: number): string { return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 3 }).format(n ?? 0); }
  protected inr(n: number): string { return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(n ?? 0); }

  protected mode(k: string): string { return PAYMENT_MODE_LABELS[k as CustomerPaymentMode] ?? k; }
  protected freq(k: string): string { return BILLING_FREQUENCY_LABELS[k as BillingFrequency] ?? k; }

  private bars(map: Record<string, number> | undefined, label: (k: string) => string): Bar[] {
    const entries = Object.entries(map ?? {});
    const max = Math.max(1, ...entries.map(([, n]) => n));
    return entries.map(([k, n]) => ({ label: label(k), count: n, pct: (n / max) * 100 }));
  }
}
