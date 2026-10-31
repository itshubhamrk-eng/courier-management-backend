import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { AppRole } from '@core/models/role.model';
import {
  APPROVAL_LABELS, EMAIL_LABELS, INVOICE_STATUS_LABELS, Invoice, InvoicePayment, PAYMENT_METHODS, PAYMENT_METHOD_LABELS,
  PaymentMethod
} from '@core/models/customer-invoice.model';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { StatusBadge } from '@shared/components/status-badge/status-badge';
import { DialogService } from '@shared/components/ui-dialog/dialog.service';
import { AuthService } from '@core/auth/auth.service';
import { CompanyProfileService } from '@features/company/company-profile.service';
import { PRINT_HEADER_CSS, companyAddressLine, renderPrintHeader } from '@features/shipment/consignment-print.util';
import { BusinessBillingService } from './business-billing.service';

const METHOD_OPTS: SelectOption[] = PAYMENT_METHODS.map((m) => ({ value: m, label: PAYMENT_METHOD_LABELS[m] }));

/**
 * One invoice: the printable bill (customer, period, a line per shipment, totals) with the
 * receipts against it, a record-payment form and cancel. Print uses the browser's print on this
 * page — `.inv-sheet` is the only thing left visible, so there is no popup window.
 */
@Component({
  selector: 'app-billing-invoice',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, UiButton, UiCard, UiInput, UiSelect, UiLoader, StatusBadge],
  template: `
    <div class="page inv-page">
      <header class="page__head inv-noprint">
        <div><h1 class="text-h1">{{ inv()?.invoiceNumber || 'Invoice' }}</h1>
          <p class="text-caption">{{ inv()?.customer?.name }} — {{ inv() ? (inv()!.periodFrom | date: 'dd MMM') + ' to ' + (inv()!.periodTo | date: 'dd MMM yyyy') : '' }}</p></div>
        <div class="page__actions">
          <app-button variant="stroked" (pressed)="back()">Back</app-button>
          @if (inv() && inv()!.status !== 'CANCELLED') {
            @if (canApprove() && inv()!.approvalStatus === 'PENDING_APPROVAL') {
              <app-button icon="mark_email_read" [loading]="approving()" (pressed)="approve()">Approve &amp; send</app-button>
            }
            @if (canApprove() && inv()!.approvalStatus === 'APPROVED') {
              <app-button variant="stroked" icon="forward_to_inbox" [loading]="approving()" (pressed)="resend()">Resend e-mail</app-button>
            }
            <app-button variant="stroked" icon="print" (pressed)="print()">Print</app-button>
            @if (canCancel() && inv()!.payments.length === 0) { <app-button variant="stroked" icon="block" (pressed)="cancel()">Cancel invoice</app-button> }
          }
        </div>
      </header>

      @if (loading()) {
        <app-loader [minHeight]="240" caption="Loading…" />
      } @else if (!inv()) {
        <app-card><p class="inv-empty">Invoice not found or outside your scope.</p></app-card>
      } @else {
        @if (inv()!.status !== 'CANCELLED') {
          <div class="inv-banner inv-noprint" [class.inv-banner--warn]="inv()!.approvalStatus === 'PENDING_APPROVAL' || inv()!.emailStatus === 'FAILED' || inv()!.emailStatus === 'NO_EMAIL'">
            @if (inv()!.approvalStatus === 'PENDING_APPROVAL') {
              <strong>Pending approval.</strong> Approving sends this invoice to {{ inv()!.customer.email || 'the customer (no e-mail address on file yet)' }}. Payments can be recorded once it is approved.
            } @else {
              <strong>Approved</strong> {{ inv()!.approvedAt | date: 'dd MMM yyyy, HH:mm' }} ·
              {{ emailLabel() }}@if (inv()!.emailTo) { to {{ inv()!.emailTo }} }@if (inv()!.emailSentAt) { on {{ inv()!.emailSentAt | date: 'dd MMM yyyy, HH:mm' }} }
              @if (inv()!.emailError) { — {{ inv()!.emailError }} }
            }
          </div>
        }
        <div class="inv-sheet">
          <!-- Same masthead as the consignment note / THC / DRS prints: logo, company letterhead, identity box with a QR. -->
          <div class="inv-hdr" [innerHTML]="header()"></div>
          <div class="inv-head">
            <div><h2>TAX INVOICE</h2><p class="inv-muted">{{ inv()!.invoiceNumber }} · {{ inv()!.source === 'AUTO' ? 'Auto-generated' : 'Manual' }}</p></div>
            <div class="inv-right">
              <app-status-badge class="inv-noprint" [value]="inv()!.status" [label]="statusLabel()" />
              <p>Invoice date: <strong>{{ inv()!.invoiceDate | date: 'dd MMM yyyy' }}</strong></p>
              <p>Period: <strong>{{ inv()!.periodFrom | date: 'dd MMM yyyy' }} – {{ inv()!.periodTo | date: 'dd MMM yyyy' }}</strong></p>
            </div>
          </div>
          <div class="inv-bill">
            <span class="inv-muted">Bill to</span>
            <strong>{{ inv()!.customer.name }}</strong>
            <span>{{ inv()!.customer.code }} · {{ inv()!.customer.mobile }}</span>
            @if (inv()!.customer.gstNumber) { <span>GSTIN: {{ inv()!.customer.gstNumber }}</span> }
            @if (inv()!.customer.billingAddress) { <span>{{ inv()!.customer.billingAddress }}</span> }
          </div>

          <table class="inv-t">
            <thead><tr><th>#</th><th>Shipment</th><th>Date</th><th>Consignee</th><th>To</th><th class="n">Qty</th><th class="n">Wt (kg)</th>
              <th>Payment</th><th class="n">GST</th><th class="n">Amount</th></tr></thead>
            <tbody>
              @for (l of inv()!.lines; track l.id; let i = $index) {
                <tr><td>{{ i + 1 }}</td><td>{{ l.shipmentNumber }}</td><td>{{ l.bookingDate | date: 'dd MMM' }}</td><td>{{ l.consignee || '—' }}</td>
                  <td>{{ l.destinationPincode || '—' }}</td><td class="n">{{ l.quantity }}</td><td class="n">{{ l.actualWeight | number: '1.0-3' }}</td>
                  <td>{{ l.paymentMode || '—' }}@if (l.prepaid) { <em> (paid)</em> }</td>
                  <td class="n">{{ l.gstAmount | number: '1.2-2' }}</td><td class="n">{{ l.amount | number: '1.2-2' }}</td></tr>
              }
            </tbody>
            <tfoot>
              <tr><td colspan="5">Total ({{ inv()!.shipmentsCount }} booking(s))</td><td class="n">{{ inv()!.totalQuantity }}</td>
                <td class="n">{{ inv()!.totalWeight | number: '1.0-3' }}</td><td></td>
                <td class="n">{{ inv()!.gstAmount | number: '1.2-2' }}</td><td class="n">{{ inv()!.totalAmount | number: '1.2-2' }}</td></tr>
            </tfoot>
          </table>

          <div class="inv-totals">
            <p><span>Invoice total (incl. GST)</span><strong>₹{{ inv()!.totalAmount | number: '1.2-2' }}</strong></p>
            <p><span>Paid at booking</span><strong>− ₹{{ inv()!.prepaidAmount | number: '1.2-2' }}</strong></p>
            <p><span>Received since</span><strong>− ₹{{ inv()!.amountPaid | number: '1.2-2' }}</strong></p>
            <p class="inv-bal"><span>Balance due</span><strong>₹{{ inv()!.balance | number: '1.2-2' }}</strong></p>
          </div>
        </div>

        <app-card class="inv-noprint" title="Payments" subtitle="Receipts recorded against this invoice, with proof of payment.">
          @if (!inv()!.payments.length) { <p class="inv-empty">No payment recorded yet.</p> }
          @else {
            <table class="inv-t inv-t--plain"><thead><tr><th>Date</th><th>Method</th><th>Reference</th><th>Proof</th><th class="n">Amount</th><th></th></tr></thead>
              <tbody>@for (p of inv()!.payments; track p.id) {
                <tr><td>{{ p.paidOn | date: 'dd MMM yyyy' }}</td><td>{{ methodLabel(p.method) }}</td><td>{{ p.reference || '—' }}</td>
                  <td>
                    @if (p.proofLink) { <a class="inv-link" [href]="p.proofLink" target="_blank" rel="noopener">{{ p.proofName || 'View proof' }}</a> }
                    @else if (canPay()) {
                      <label class="inv-upload">Upload proof<input type="file" accept="image/*,application/pdf" (change)="attach(p, $any($event.target).files?.[0])" hidden /></label>
                    } @else { — }
                  </td>
                  <td class="n">₹{{ p.amount | number: '1.2-2' }}</td>
                  <td class="n">@if (canPay()) { <button type="button" class="inv-x" title="Reverse this payment" (click)="reverse(p)">✕</button> }</td></tr>
              }</tbody></table>
          }
          @if (canPay() && inv()!.approvalStatus === 'APPROVED' && inv()!.balance > 0) {
            <div class="inv-pay">
              <app-input [control]="amount" label="Amount received" type="number" [min]="0" [step]="0.01" />
              <app-input [control]="paidOn" label="Received on" type="date" />
              <app-select [control]="method" label="Method" [options]="methodOpts" />
              <app-input [control]="reference" label="Reference (UTR / cheque no.)" [maxLength]="100" />
              <label class="inv-file">Proof of payment (image / PDF)
                <input type="file" accept="image/*,application/pdf" (change)="proof.set($any($event.target).files?.[0] ?? null)" />
                @if (proof(); as f) { <span class="inv-file__n">{{ f.name }}</span> }
              </label>
              <div class="inv-pay__actions">
                <app-button icon="payments" [loading]="paying()" (pressed)="pay(false)">Record payment</app-button>
                <app-button variant="stroked" icon="task_alt" [loading]="paying()" (pressed)="pay(true)">Mark as paid (full balance)</app-button>
              </div>
            </div>
          } @else if (inv()!.approvalStatus === 'PENDING_APPROVAL') {
            <p class="inv-empty">Approve the invoice to start recording payments.</p>
          }
        </app-card>
      }
    </div>
  `,
  styles: [`
    .inv-hdr { margin-bottom:16px; color:#000; background:#fff; }
    .inv-sheet { background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); padding:24px; margin-bottom:16px; }
    .inv-head { display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; margin-bottom:16px; }
    .inv-head h2 { margin:0; font:700 25px var(--font-sans); letter-spacing:.04em; }
    .inv-right { text-align:right; } .inv-right p { margin:4px 0 0; font:400 17px var(--font-sans); }
    .inv-muted { font:400 16px var(--font-sans); color:var(--content-muted); margin:4px 0 0; }
    .inv-bill { display:flex; flex-direction:column; gap:2px; margin-bottom:16px; font:400 17px var(--font-sans); }
    .inv-t { width:100%; border-collapse:collapse; font:400 16px var(--font-sans); }
    .inv-t th { text-align:left; font:600 14px var(--font-sans); color:var(--content-muted); text-transform:uppercase; letter-spacing:.04em; padding:8px 8px; border-bottom:1px solid var(--surface-border); }
    .inv-t td { padding:8px; border-bottom:1px solid var(--surface-border); } .inv-t .n { text-align:right; }
    .inv-t tfoot td { font-weight:700; border-top:2px solid var(--surface-border); border-bottom:0; }
    .inv-t--plain td, .inv-t--plain th { font-size:17px; }
    .inv-totals { margin:16px 0 0 auto; width:min(340px,100%); display:flex; flex-direction:column; gap:6px; }
    .inv-totals p { display:flex; justify-content:space-between; margin:0; font:400 17px var(--font-sans); }
    .inv-bal { border-top:2px solid var(--surface-border); padding-top:8px; font-size:19px !important; } .inv-bal strong { color:var(--danger); }
    .inv-empty { font:400 17px var(--font-sans); color:var(--content-muted); text-align:center; padding:12px; margin:0; }
    .inv-pay { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:12px 16px; align-items:end; margin-top:16px; }
    .inv-pay__actions { grid-column:1 / -1; display:flex; gap:10px; flex-wrap:wrap; }
    .inv-file { display:flex; flex-direction:column; gap:6px; font:500 16px var(--font-sans); grid-column:span 2; }
    .inv-file__n { font:400 15px var(--font-sans); color:var(--content-muted); }
    .inv-banner { background:var(--success-bg); color:var(--success); border-radius:var(--r-field); padding:10px 14px; margin-bottom:12px; font:400 17px var(--font-sans); }
    .inv-banner--warn { background:var(--warning-bg); color:var(--content-fg); }
    .inv-link { color:var(--brand-700); font-weight:600; } .inv-upload { color:var(--brand-700); font-weight:600; cursor:pointer; }
    .inv-x { border:0; background:transparent; color:var(--content-muted); cursor:pointer; font-size:18px; } .inv-x:hover { color:var(--danger); }
    @media (max-width:960px){ .inv-pay { grid-template-columns:1fr 1fr; } .inv-file { grid-column:1 / -1; } }
    @media print {
      body * { visibility:hidden !important; }
      .inv-sheet, .inv-sheet * { visibility:visible !important; }
      .inv-sheet { position:absolute; left:0; top:0; width:100%; border:0; padding:0; }
      .inv-noprint { display:none !important; }
    }
  `]
})
export class BillingInvoice implements OnInit {
  private readonly billing = inject(BusinessBillingService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly perms = inject(PermissionService);
  private readonly confirm = inject(DialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly companyProfile = inject(CompanyProfileService);
  private readonly auth = inject(AuthService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly letterhead = signal<import('@features/company/company-profile.service').CompanyLetterhead | null>(null);
  /** The shared print masthead, QR = the invoice number. Built from escaped values + library-generated SVG only. */
  protected readonly header = computed<SafeHtml>(() => {
    const i = this.inv();
    const c = this.letterhead();
    if (!i) return '';
    return this.sanitizer.bypassSecurityTrustHtml(renderPrintHeader({
      companyName: c?.companyName ?? this.auth.user()?.companyName ?? 'Company',
      companyLogo: c?.logo ?? this.auth.companyLogo(),
      companyAddress: companyAddressLine(c),
      companyGst: c?.gstNumber ?? null,
      companyContact: c?.mobile ?? null,
      companyWebsite: c?.website ?? null
    }, { label: 'Invoice No', value: i.invoiceNumber, qrValue: i.invoiceNumber }));
  });
  protected readonly inv = signal<Invoice | null>(null);
  protected readonly loading = signal(true);
  protected readonly paying = signal(false);
  protected readonly approving = signal(false);
  protected readonly proof = signal<File | null>(null);
  protected readonly methodOpts = METHOD_OPTS;
  protected readonly amount = new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]);
  protected readonly paidOn = new FormControl<string>(new Date().toISOString().slice(0, 10), { nonNullable: true });
  protected readonly method = new FormControl<string>('BANK_TRANSFER', { nonNullable: true });
  protected readonly reference = new FormControl<string>('', { nonNullable: true });
  protected readonly statusLabel = computed(() => INVOICE_STATUS_LABELS[this.inv()!.status]);
  protected readonly canPay = computed(() => this.perms.canAccess({ roles: [AppRole.COMPANY_ADMIN], permissions: ['CUSTOMER_BILLING_UPDATE'] }));
  protected readonly canApprove = computed(() => this.perms.canAccess({ roles: [AppRole.COMPANY_ADMIN], permissions: ['CUSTOMER_BILLING_UPDATE'] }));
  protected readonly emailLabel = computed(() => EMAIL_LABELS[this.inv()!.emailStatus]);
  protected readonly canCancel = computed(() => this.perms.canAccess({ roles: [AppRole.COMPANY_ADMIN], permissions: ['CUSTOMER_BILLING_DELETE'] }));

  ngOnInit(): void {
    this.injectHeaderStyles();
    this.companyProfile.get().subscribe({ next: (c) => this.letterhead.set(c), error: () => this.letterhead.set(null) });
    this.breadcrumb.set([{ label: 'Business Customer' }, { label: 'Billing', route: '/business-customer/billing' }, { label: 'Invoice' }]);
    this.load();
  }

  /** The shared header CSS uses generic class names (.head, .logo, .co ...), so it is scoped under `.inv-hdr`
   *  in a <style> element that lives only as long as this page — it must not restyle the rest of the app. */
  private injectHeaderStyles(): void {
    const scoped = PRINT_HEADER_CSS.replace(/(^|\})\s*([^{}]+)\{/g, (_m, brace: string, sel: string) =>
      `${brace}\n${sel.split(',').map((s) => `.inv-hdr ${s.trim()}`).join(', ')}{`);
    const el = document.createElement('style');
    // The invoice header is borderless: drop the outline and the column dividers the shared CSS draws.
    el.textContent = scoped + '\n.inv-hdr .head{border:0} .inv-hdr .co,.inv-hdr .lrbox{border:0}';
    document.head.appendChild(el);
    this.destroyRef.onDestroy(() => el.remove());
  }

  private load(): void {
    this.billing.invoice(this.id).subscribe({
      next: (i) => { this.set(i); this.loading.set(false); },
      error: () => { this.inv.set(null); this.loading.set(false); }
    });
  }

  private set(i: Invoice): void {
    this.inv.set(i);
    this.amount.setValue(i.balance > 0 ? i.balance : null);
  }

  protected methodLabel(m: PaymentMethod): string { return PAYMENT_METHOD_LABELS[m] ?? m; }
  protected back(): void { this.router.navigate(['/business-customer/billing']); }
  protected print(): void { window.print(); }

  /** Uploads the chosen proof (if any) first, then records the receipt — `full` takes the whole balance. */
  protected pay(full: boolean): void {
    if (!full) {
      this.amount.markAsTouched();
      if (this.amount.invalid) return;
    }
    this.paying.set(true);
    const file = this.proof();
    const send = (proofUrl: string | null, proofName: string | null) => {
      const common = {
        paidOn: this.paidOn.value || null, method: this.method.value as PaymentMethod,
        reference: this.reference.value.trim() || null, proofUrl, proofName
      };
      const call = full ? this.billing.markPaid(this.id, common) : this.billing.pay(this.id, { ...common, amount: Number(this.amount.value) });
      call.subscribe({
        next: (i) => {
          this.paying.set(false); this.reference.setValue(''); this.proof.set(null); this.set(i);
          this.notify.success(full ? 'Invoice marked as paid.' : 'Payment recorded.');
        },
        error: (e) => { this.paying.set(false); this.notify.error(e?.error?.message ?? 'Could not record the payment.'); }
      });
    };
    if (!file) { send(null, null); return; }
    this.billing.uploadProof(this.id, file).subscribe({
      next: (up) => send(up.url, up.name),
      error: (e) => { this.paying.set(false); this.notify.error(e?.error?.message ?? 'Could not upload the proof file.'); }
    });
  }

  protected attach(p: InvoicePayment, file: File | undefined): void {
    if (!file) return;
    this.billing.uploadProof(this.id, file).subscribe({
      next: (up) => this.billing.attachProof(this.id, p.id, up.url, up.name).subscribe({
        next: (i) => { this.set(i); this.notify.success('Proof attached.'); },
        error: (e) => this.notify.error(e?.error?.message ?? 'Could not attach the proof.')
      }),
      error: (e) => this.notify.error(e?.error?.message ?? 'Could not upload the proof file.')
    });
  }

  protected reverse(p: InvoicePayment): void {
    this.confirm.confirm({
      title: 'Reverse this payment?', message: `₹${p.amount} will go back on the balance and the invoice status will follow.`,
      confirmLabel: 'Reverse payment', danger: true
    }).subscribe((ok) => {
      if (!ok) return;
      this.billing.reversePayment(this.id, p.id).subscribe({
        next: (i) => { this.set(i); this.notify.success('Payment reversed.'); },
        error: (e) => this.notify.error(e?.error?.message ?? 'Could not reverse the payment.')
      });
    });
  }

  protected approve(): void {
    this.approving.set(true);
    this.billing.approve(this.id).subscribe({
      next: (i) => { this.approving.set(false); this.set(i);
        this.notify.success(i.emailStatus === 'SENT' ? `Approved and e-mailed to ${i.emailTo}.` : `Approved. ${EMAIL_LABELS[i.emailStatus]}${i.emailError ? ' — ' + i.emailError : ''}`); },
      error: (e) => { this.approving.set(false); this.notify.error(e?.error?.message ?? 'Could not approve the invoice.'); }
    });
  }

  protected resend(): void {
    this.approving.set(true);
    this.billing.resendEmail(this.id).subscribe({
      next: (i) => { this.approving.set(false); this.set(i);
        this.notify.success(i.emailStatus === 'SENT' ? `E-mailed to ${i.emailTo}.` : `${EMAIL_LABELS[i.emailStatus]}${i.emailError ? ' — ' + i.emailError : ''}`); },
      error: (e) => { this.approving.set(false); this.notify.error(e?.error?.message ?? 'Could not e-mail the invoice.'); }
    });
  }

  protected cancel(): void {
    this.confirm.confirm({
      title: 'Cancel this invoice?', message: 'Its bookings become billable again. An invoice with payments recorded cannot be cancelled.',
      confirmLabel: 'Cancel invoice', danger: true
    }).subscribe((ok) => {
      if (!ok) return;
      this.billing.cancel(this.id).subscribe({
        next: (i) => { this.set(i); this.notify.success('Invoice cancelled.'); },
        error: (e) => this.notify.error(e?.error?.message ?? 'Could not cancel the invoice.')
      });
    });
  }
}
