import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PdpEarning, PdpStatementDetail } from '@core/models/pdp.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { PdpBillingService } from './pdp-billing.service';

/**
 * One partner billing statement: a pickup section and a delivery section, totals, net payable.
 * Draft -> Approved -> Paid, the last two manual marks (no wallet is touched). Export CSV and
 * Print (the browser's own print of this page) for the partner's copy.
 */
@Component({
  selector: 'app-partner-statement',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, ReactiveFormsModule, UiCard, UiButton, UiInput, UiLoader, StatusBadge],
  template: `
    <div class="page">
      @if (loading()) { <app-loader [minHeight]="240" caption="Loading…" /> }
      @else if (detail(); as d) {
        <header class="page__head">
          <div>
            <h1 class="text-h1">{{ d.statement.statementNo }}</h1>
            <p class="text-caption">{{ d.statement.branchName }} ({{ d.statement.branchCode }}) · {{ d.statement.periodFrom }} → {{ d.statement.periodTo }}</p>
          </div>
          <div class="page__actions no-print">
            <app-status-badge [value]="d.statement.status" [label]="d.statement.status" />
            <app-button variant="stroked" icon="download" (pressed)="exportCsv()">Export CSV</app-button>
            <app-button variant="stroked" icon="print" (pressed)="print()">Print</app-button>
            <app-button variant="stroked" (pressed)="back()">Back</app-button>
          </div>
        </header>

        @if (d.unratedInPeriod) { <p class="warn">{{ d.unratedInPeriod }} earning(s) in this period have no rate card and are not on this statement.</p> }

        <app-card title="Pickups" [subtitle]="d.statement.pickupCount + ' pickup(s) · ' + d.statement.pickupTotal">
          <ng-container *ngTemplateOutlet="tbl; context: { rows: pickups() }" />
        </app-card>
        <app-card title="Deliveries" [subtitle]="d.statement.deliveryCount + ' deliver(ies) · ' + d.statement.deliveryTotal">
          <ng-container *ngTemplateOutlet="tbl; context: { rows: deliveries() }" />
        </app-card>

        <app-card title="Summary">
          <dl class="kv">
            <dt>Pickup earnings</dt><dd>{{ d.statement.pickupTotal }}</dd>
            <dt>Delivery earnings</dt><dd>{{ d.statement.deliveryTotal }}</dd>
            <dt class="tot">Net payable</dt><dd class="tot">{{ d.statement.netPayable }}</dd>
            @if (d.statement.status === 'PAID') { <dt>Paid on</dt><dd>{{ d.statement.paidOn }} {{ d.statement.paymentRef ? '· ' + d.statement.paymentRef : '' }}</dd> }
            @if (d.statement.remarks) { <dt>Remarks</dt><dd>{{ d.statement.remarks }}</dd> }
          </dl>
        </app-card>

        <div class="act no-print">
          @if (isAdmin() && d.statement.status === 'DRAFT') {
            <app-button icon="check" [loading]="busy()" (pressed)="approve()">Approve</app-button>
            <app-button variant="stroked" icon="delete" (pressed)="remove()">Delete draft</app-button>
          }
          @if (isAdmin() && d.statement.status === 'APPROVED') {
            <app-input [control]="paidOn" label="Paid on" type="date" />
            <app-input [control]="payRef" label="Payment reference" [maxLength]="100" />
            <app-button icon="paid" [loading]="busy()" [disabled]="paidOn.invalid" (pressed)="pay()">Mark paid</app-button>
          }
        </div>
      }
    </div>

    <ng-template #tbl let-rows="rows">
      @if (rows.length) {
        <table class="lines">
          <thead><tr><th>Date</th><th>Reference</th><th>Pincode</th><th>Weight (kg)</th><th>Rate / kg</th><th>Weight charge</th><th>Extras</th><th>Amount</th></tr></thead>
          <tbody>
            @for (e of rows; track e.id) {
              <tr><td>{{ e.earnedOn }}</td><td>{{ e.referenceNo }}</td><td>{{ e.pincode || '' }}</td>
                <td>{{ e.chargeableWeightKg ?? e.actualWeightKg ?? '' }}</td><td>{{ e.ratePerKg ?? '' }}</td>
                <td>{{ e.weightCharge }}</td><td>{{ e.mathadiCharge + e.odaCharge }}</td><td><strong>{{ e.totalAmount }}</strong></td></tr>
            }
          </tbody>
        </table>
      } @else { <p class="empty">None in this period.</p> }
    </ng-template>
  `,
  styles: [`
    .lines { width:100%; border-collapse:collapse; font:400 16px var(--font-sans); }
    .lines th { text-align:left; font:600 15px var(--font-sans); color:var(--content-muted); text-transform:uppercase; padding:6px 8px; }
    .lines td { padding:7px 8px; border-top:1px solid var(--surface-border); }
    .kv { display:grid; grid-template-columns:max-content 1fr; gap:6px 24px; margin:0; font:400 17px var(--font-sans); }
    .kv dt { color:var(--content-muted); } .kv dd { margin:0; font-weight:600; }
    .kv .tot { color:var(--content-fg); font-weight:700; border-top:1px solid var(--surface-border); padding-top:8px; font-size:19px; }
    .act { display:flex; gap:12px; align-items:end; flex-wrap:wrap; margin-top:16px; }
    .warn { font:500 16px var(--font-sans); color:var(--danger); } .empty { font:400 17px var(--font-sans); color:var(--content-muted); margin:0; }
    app-card { display:block; margin-bottom:16px; }
    @media print { .no-print { display:none !important; } }
  `]
})
export class PartnerStatement implements OnInit {
  private readonly svc = inject(PdpBillingService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  protected readonly isAdmin = computed(() => this.perms.hasRole(AppRole.COMPANY_ADMIN));
  private readonly confirm = inject(DialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly detail = signal<PdpStatementDetail | null>(null);
  protected readonly pickups = computed(() => (this.detail()?.lines ?? []).filter((l) => l.earningType === 'PICKUP'));
  protected readonly deliveries = computed(() => (this.detail()?.lines ?? []).filter((l) => l.earningType === 'DELIVERY'));
  protected readonly paidOn = new FormControl<string>(new Date().toISOString().slice(0, 10), { nonNullable: true, validators: [Validators.required] });
  protected readonly payRef = new FormControl<string>('', { nonNullable: true });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Pickup Delivery Partner Management' }, { label: 'Partner Billing', route: '/pdp/billing' }, { label: 'Statement' }]);
    this.svc.statement(this.id).subscribe({
      next: (d) => { this.detail.set(d); this.loading.set(false); },
      error: () => { this.loading.set(false); this.back(); }
    });
  }

  private apply(call: import('rxjs').Observable<PdpStatementDetail>, ok: string): void {
    this.busy.set(true);
    call.subscribe({
      next: (d) => { this.busy.set(false); this.detail.set(d); this.notify.success(ok); },
      error: (e: HttpErrorResponse) => { this.busy.set(false); this.notify.error(e.error?.message ?? 'Action failed.'); }
    });
  }

  protected approve(): void { this.apply(this.svc.approve(this.id), 'Statement approved.'); }
  protected pay(): void { this.apply(this.svc.pay(this.id, this.paidOn.value, this.payRef.value), 'Statement marked paid.'); }

  protected remove(): void {
    this.confirm.confirm({ title: 'Delete this draft?', message: 'Its earnings go back to Accrued and can be billed again.', confirmLabel: 'Delete', danger: true })
      .subscribe((ok) => {
        if (!ok) return;
        this.svc.remove(this.id).subscribe({
          next: () => { this.notify.success('Statement deleted.'); this.back(); },
          error: (e: HttpErrorResponse) => this.notify.error(e.error?.message ?? 'Could not delete.')
        });
      });
  }

  protected exportCsv(): void {
    const d = this.detail();
    if (!d) return;
    const q = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const rows = d.lines.map((e: PdpEarning) => [e.earningType, e.earnedOn, e.referenceNo, e.pincode, e.chargeableWeightKg ?? e.actualWeightKg,
      e.ratePerKg, e.weightCharge, e.mathadiCharge + e.odaCharge, e.totalAmount].map(q).join(','));
    const csv = [`Statement,${q(d.statement.statementNo)},Partner,${q(d.statement.branchName)},Period,${d.statement.periodFrom} to ${d.statement.periodTo}`,
      ['Type', 'Date', 'Reference', 'Pincode', 'Weight kg', 'Rate/kg', 'Weight charge', 'Extras', 'Amount'].join(','), ...rows,
      `Net payable,${d.statement.netPayable}`].join('\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url; a.download = `${d.statement.statementNo}.csv`; a.click();
    URL.revokeObjectURL(url);
  }

  protected print(): void { window.print(); }
  protected back(): void { this.router.navigate(['/pdp/billing']); }
}
