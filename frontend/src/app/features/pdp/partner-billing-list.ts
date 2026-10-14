import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { Page, PageQuery, emptyPage } from '@core/models/page.model';
import { PdpBillingFrequency, PdpBillingSchedule, PdpStatement, PdpStatementStatus } from '@core/models/pdp.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiTable, TableColumn } from '@shared/components/ui-table/ui-table';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { PdpBillingService } from './pdp-billing.service';
import { PdpService } from './pdp.service';

const STATUSES: SelectOption[] = [{ value: 'DRAFT', label: 'Draft' }, { value: 'APPROVED', label: 'Approved' }, { value: 'PAID', label: 'Paid' }];

/**
 * Partner Billing — the report the company pays partners against (the counterpart of Customer
 * Billing). Generate a statement for a partner and period from their accrued earnings; approve it,
 * then mark it paid once settled outside the system. Nothing here posts to a wallet.
 */
@Component({
  selector: 'app-partner-billing-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiButton, UiInput, UiSelect, UiTable, UiPagination, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Partner Billing</h1>
          <p class="text-caption">Statements of what each Pickup-Delivery Partner is owed for a period.</p></div>
      </header>

      @if (isAdmin()) {
      <app-card title="Generate a statement" subtitle="Bills every accrued, unbilled pickup and delivery earning of the partner in the period.">
        <div class="gen">
          <app-select [control]="gPartner" label="Partner" [options]="partnerOpts()" placeholder="Select a partner" />
          <app-input [control]="gFrom" label="From" type="date" />
          <app-input [control]="gTo" label="To" type="date" />
          <app-input [control]="gRemarks" label="Remarks (optional)" [maxLength]="500" />
          <app-button icon="receipt_long" [loading]="generating()" [disabled]="gPartner.invalid || gFrom.invalid || gTo.invalid" (pressed)="generate()">Generate</app-button>
        </div>
      </app-card>
      }

      @if (isAdmin()) {
      <app-card title="Billing schedule" subtitle="How often each partner is billed. A draft statement is created automatically when a cycle closes and the partner has unbilled earnings.">
        <div class="sched__bar">
          <app-button icon="bolt" variant="stroked" [loading]="running()" [disabled]="!dueCount()" (pressed)="runDue()">Generate due statements now ({{ dueCount() }})</app-button>
        </div>
        <table class="sched">
          <thead><tr><th>Partner</th><th>Frequency</th><th>Last billed to</th><th>Unbilled</th><th>Next statement due</th></tr></thead>
          <tbody>
            @for (r of schedule(); track r.branchId) {
              <tr>
                <td>{{ r.branchName }}<div class="text-caption">{{ r.branchCode }}</div></td>
                <td><select class="sched__sel" [value]="r.frequency" (change)="setFrequency(r, $any($event.target).value)">
                  @for (f of frequencies; track f.value) { <option [value]="f.value" [selected]="f.value === r.frequency">{{ f.label }}</option> }
                </select></td>
                <td>{{ r.lastBilledTo ?? '—' }}</td>
                <td>{{ r.unbilledEarnings }}</td>
                <td>@if (r.due) { <strong>{{ r.dueFrom }} → {{ r.dueTo }}</strong> } @else { <span class="text-caption">Nothing due</span> }</td>
              </tr>
            } @empty { <tr><td colspan="5" class="text-caption">No active partners.</td></tr> }
          </tbody>
        </table>
      </app-card>
      }

      <div class="filters">
        @if (isAdmin()) { <app-select [control]="partnerCtl" label="Partner" [options]="partnerOpts()" [allowEmpty]="true" emptyLabel="All partners" placeholder="All partners" /> }
        <app-select [control]="statusCtl" label="Status" [options]="statuses" [allowEmpty]="true" emptyLabel="All" placeholder="All" />
      </div>
      <app-table [columns]="columns" [rows]="page().content" [loading]="loading()" [startIndex]="page().page * page().size"
                 emptyTitle="No statements yet" emptyHint="Generate one above once a partner has earnings." idKey="id" (rowClick)="open($event)">
        <ng-template #row let-s>
          <td><strong>{{ s.statementNo }}</strong><div class="text-caption">{{ s.createdDate?.slice(0, 10) }}</div></td>
          <td>{{ s.branchName }}<div class="text-caption">{{ s.branchCode }}</div></td>
          <td>{{ s.periodFrom }} → {{ s.periodTo }}</td>
          <td>{{ s.pickupCount }} / {{ s.deliveryCount }}</td>
          <td><strong>{{ s.netPayable }}</strong><div class="text-caption">P {{ s.pickupTotal }} · D {{ s.deliveryTotal }}</div></td>
          <td><app-status-badge [value]="s.status" [label]="label(s.status)" />
            @if (s.paidOn) { <div class="text-caption">{{ s.paidOn }}{{ s.paymentRef ? ' · ' + s.paymentRef : '' }}</div> }</td>
        </ng-template>
      </app-table>
      <app-pagination [page]="page()" (pageChange)="onPage($event)" />
    </div>
  `,
  styles: [`
    .gen { display:grid; grid-template-columns:2fr 1fr 1fr 2fr auto; gap:16px; align-items:end; }
    .sched { width:100%; border-collapse:collapse; }
    .sched th, .sched td { text-align:left; padding:8px 12px; border-bottom:1px solid var(--border, #e5e7eb); }
    .sched__bar { display:flex; justify-content:flex-end; margin-bottom:12px; }
    .sched__sel { font:inherit; padding:6px 8px; border-radius:8px; }
    .filters { display:grid; grid-template-columns:minmax(0,300px) minmax(0,200px); gap:16px; margin:16px 0; }
    @media (max-width:980px){ .gen { grid-template-columns:1fr 1fr; } }
  `]
})
export class PartnerBillingList implements OnInit {
  private readonly svc = inject(PdpBillingService);
  private readonly pdp = inject(PdpService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly perms = inject(PermissionService);
  protected readonly isAdmin = computed(() => this.perms.hasRole(AppRole.COMPANY_ADMIN));

  protected readonly gPartner = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  protected readonly gFrom = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  protected readonly gTo = new FormControl<string>('', { nonNullable: true, validators: [Validators.required] });
  protected readonly gRemarks = new FormControl<string>('', { nonNullable: true });
  protected readonly partnerCtl = new FormControl<string>('', { nonNullable: true });
  protected readonly statusCtl = new FormControl<string>('', { nonNullable: true });
  protected readonly partnerOpts = signal<SelectOption[]>([]);
  protected readonly page = signal<Page<PdpStatement>>(emptyPage());
  protected readonly loading = signal(true);
  protected readonly generating = signal(false);
  protected readonly statuses = STATUSES;
  protected readonly frequencies: { value: PdpBillingFrequency; label: string }[] = [
    { value: 'WEEKLY', label: 'Weekly' }, { value: 'FIFTEEN_DAYS', label: 'Every 15 days' }, { value: 'MONTHLY', label: 'Monthly' }
  ];
  protected readonly schedule = signal<PdpBillingSchedule[]>([]);
  protected readonly running = signal(false);
  protected readonly dueCount = computed(() => this.schedule().filter((r) => r.due).length);
  private query: PageQuery = { page: 0, size: 20 };

  protected readonly columns: TableColumn<PdpStatement>[] = [
    { key: 'no', header: 'Statement' }, { key: 'partner', header: 'Partner' }, { key: 'period', header: 'Period' },
    { key: 'jobs', header: 'Pickups / Deliveries' }, { key: 'net', header: 'Net payable' }, { key: 'status', header: 'Status', width: '170px' }
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Pickup Delivery Partner Management' }, { label: 'Partner Billing' }]);
    if (this.isAdmin()) {
      this.pdp.partners({ page: 0, size: 200 }).subscribe((r) =>
        this.partnerOpts.set(r.content.map((b) => ({ value: b.id, label: `${b.branchName} (${b.branchCode})` }))));
      this.loadSchedule();
    }
    [this.partnerCtl, this.statusCtl].forEach((c) => c.valueChanges.subscribe(() => { this.query = { ...this.query, page: 0 }; this.load(); }));
    this.load();
  }

  protected label(s: PdpStatementStatus): string { return { DRAFT: 'Draft', APPROVED: 'Approved', PAID: 'Paid' }[s]; }

  private load(): void {
    this.loading.set(true);
    this.svc.statements({ ...this.query, branchId: this.partnerCtl.value || undefined, status: (this.statusCtl.value || undefined) as PdpStatementStatus | undefined })
      .subscribe({ next: (p) => { this.page.set(p); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  private loadSchedule(): void { this.svc.schedule().subscribe((r) => this.schedule.set(r)); }

  protected setFrequency(r: PdpBillingSchedule, frequency: PdpBillingFrequency): void {
    this.svc.setFrequency(r.branchId, frequency).subscribe({
      next: () => { this.notify.success(`${r.branchName} is now billed ${this.frequencies.find((f) => f.value === frequency)?.label.toLowerCase()}.`); this.loadSchedule(); },
      error: (e: HttpErrorResponse) => { this.notify.error(e.error?.message ?? 'Could not save the frequency.'); this.loadSchedule(); }
    });
  }

  protected runDue(): void {
    this.running.set(true);
    this.svc.generateDue().subscribe({
      next: (r) => { this.running.set(false); this.notify.success(`${r.created} statement(s) generated.`); this.loadSchedule(); this.load(); },
      error: (e: HttpErrorResponse) => { this.running.set(false); this.notify.error(e.error?.message ?? 'Could not generate statements.'); }
    });
  }

  protected onPage(i: number): void { this.query = { ...this.query, page: i }; this.load(); }
  protected open(s: PdpStatement): void { this.router.navigate(['/pdp/billing', s.id]); }

  protected generate(): void {
    this.generating.set(true);
    this.svc.generate({ branchId: this.gPartner.value, from: this.gFrom.value, to: this.gTo.value, remarks: this.gRemarks.value || null }).subscribe({
      next: (d) => {
        this.generating.set(false);
        this.notify.success(`Statement ${d.statement.statementNo} generated.`);
        if (d.unratedInPeriod) this.notify.error(`${d.unratedInPeriod} earning(s) in this period have no rate card and were left out.`);
        this.router.navigate(['/pdp/billing', d.statement.id]);
      },
      error: (e: HttpErrorResponse) => { this.generating.set(false); this.notify.error(e.error?.message ?? 'Could not generate the statement.'); }
    });
  }
}
