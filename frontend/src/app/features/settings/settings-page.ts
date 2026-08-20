import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { SettingsService } from './settings.service';
import { RazorpayConfigService, RazorpayConfigResponse } from './razorpay-config.service';

interface Section { key: string; title: string; icon: string; desc: string; }

/** Company Settings — the eight sections from the backend, loaded from /company-settings.
 *  A section editor drawer would drop in here; this foundation renders the live values. */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, UiCard, UiLoader],
  template: `
    <div class="page">
      <header><h1 class="text-h1">Company Settings</h1><p class="text-caption">Configure how the platform behaves for your company.</p></header>
      @if (loading()) {
        <app-loader [minHeight]="240" caption="Loading settings…" />
      } @else {
        <div class="grid">
          @for (s of sections; track s.key) {
            <app-card [title]="s.title" [subtitle]="s.desc">
              <div card-actions><button class="edit"><mat-icon>edit</mat-icon></button></div>
              <div class="kv">
                @for (row of preview(s.key); track row.k) {
                  <div class="kv__row"><span class="text-caption">{{ row.k }}</span><span class="kv__v">{{ row.v }}</span></div>
                }
              </div>
              @if (s.key === 'shipment') {
                <div class="dcw">
                  <label class="text-caption" for="dcw-input">Default Chargeable Weight (kg)</label>
                  <div class="dcw__row">
                    <input id="dcw-input" class="dcw__i" type="number" step="0.001" min="0.001"
                           [value]="defaultWeightInput() ?? ''" (input)="onWeightInput($event)" />
                    <button type="button" class="dcw__save" [disabled]="savingWeight() || !defaultWeightInput()"
                            (click)="saveDefaultWeight()">{{ savingWeight() ? 'Saving…' : 'Save' }}</button>
                  </div>
                </div>
              }
              @if (s.key === 'finance') {
                <div class="rzp">
                  <div class="rzp__head">
                    <span class="text-caption">Payment Gateway (Razorpay)</span>
                    <span class="rzp__badge" [class.rzp__badge--on]="razorpay()?.keySecretConfigured">
                      {{ razorpay()?.keySecretConfigured ? 'Configured' : 'Not configured' }}
                    </span>
                  </div>
                  <label class="rzp__check">
                    <input type="checkbox" [checked]="razorpayEnabledInput()"
                           (change)="onRazorpayEnabledInput($event)" />
                    <span>Use this company's own Razorpay account</span>
                  </label>
                  <label class="text-caption" for="rzp-key-id">Key ID</label>
                  <input id="rzp-key-id" class="dcw__i" type="text" placeholder="rzp_live_…"
                         [value]="razorpayKeyIdInput()" (input)="onRazorpayKeyIdInput($event)" />
                  <label class="text-caption" for="rzp-key-secret">Key Secret</label>
                  <input id="rzp-key-secret" class="dcw__i" type="password"
                         [placeholder]="razorpay()?.keySecretConfigured ? 'Leave blank to keep existing' : 'rzp secret'"
                         [value]="razorpayKeySecretInput()" (input)="onRazorpayKeySecretInput($event)" />
                  <div class="dcw__row">
                    <button type="button" class="dcw__save" [disabled]="savingRazorpay()"
                            (click)="saveRazorpay()">{{ savingRazorpay() ? 'Saving…' : 'Save' }}</button>
                  </div>
                </div>
              }
            </app-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
    .kv{display:flex;flex-direction:column;gap:10px}
    .kv__row{display:flex;justify-content:space-between;gap:16px}
    .kv__v{font:600 13px var(--font-sans);color:var(--content-fg);text-align:right}
    .edit{border:1px solid var(--surface-border);background:var(--surface);border-radius:8px;width:32px;height:32px;display:grid;place-items:center;cursor:pointer;color:var(--content-muted)}
    .dcw{margin-top:12px;padding-top:12px;border-top:1px solid var(--surface-border);display:flex;flex-direction:column;gap:6px}
    .dcw__row{display:flex;gap:8px}
    .dcw__i{flex:1;height:36px;padding:0 10px;background:var(--surface);border:1px solid var(--surface-border);border-radius:8px;font:400 13px var(--font-sans);color:var(--content-fg)}
    .dcw__i:focus{outline:0;border-color:var(--brand-500)}
    .dcw__save{height:36px;padding:0 14px;border:0;border-radius:8px;background:var(--brand-600);color:#fff;font:600 13px var(--font-sans);cursor:pointer}
    .dcw__save:disabled{opacity:.5;cursor:not-allowed}
    .rzp{margin-top:12px;padding-top:12px;border-top:1px solid var(--surface-border);display:flex;flex-direction:column;gap:6px}
    .rzp__head{display:flex;justify-content:space-between;align-items:center}
    .rzp__badge{font:600 11px var(--font-sans);padding:2px 8px;border-radius:999px;background:var(--surface-muted);color:var(--content-muted)}
    .rzp__badge--on{background:var(--brand-100,#e0e7ff);color:var(--brand-700,#3730a3)}
    .rzp__check{display:flex;align-items:center;gap:8px;font:400 13px var(--font-sans);color:var(--content-fg);margin:4px 0}
    @media (max-width:900px){.grid{grid-template-columns:1fr}}
  `]
})
export class SettingsPage implements OnInit {
  private readonly service = inject(SettingsService);
  private readonly razorpayService = inject(RazorpayConfigService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  readonly loading = signal(true);
  readonly data = signal<Record<string, unknown>>({});

  /** The one editable field on this page so far — see `saveDefaultWeight`. */
  readonly defaultWeightInput = signal<number | null>(null);
  readonly savingWeight = signal(false);

  /** A company's own Razorpay account, loaded separately from the rest of settings —
   *  it's its own COMPANY_ADMIN-only backend resource, not part of /company-settings. */
  readonly razorpay = signal<RazorpayConfigResponse | null>(null);
  readonly razorpayEnabledInput = signal(false);
  readonly razorpayKeyIdInput = signal('');
  readonly razorpayKeySecretInput = signal('');
  readonly savingRazorpay = signal(false);

  readonly sections: Section[] = [
    { key: 'general', title: 'General', icon: 'business', desc: 'Identity, contact and regional defaults.' },
    { key: 'shipment', title: 'Shipment', icon: 'local_shipping', desc: 'AWB prefixes, units and booking rules.' },
    { key: 'finance', title: 'Finance', icon: 'payments', desc: 'GST, invoicing, wallet and COD.' },
    { key: 'sla', title: 'SLA', icon: 'schedule', desc: 'Hours before a stuck shipment auto-raises a ticket.' },
    { key: 'notification', title: 'Notification', icon: 'notifications', desc: 'SMS, email, WhatsApp and push.' },
    { key: 'security', title: 'Security', icon: 'shield', desc: 'Password policy, sessions and OTP.' },
    { key: 'branding', title: 'Branding', icon: 'palette', desc: 'Logo, colours and theme.' }
  ];

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Settings' }]);
    this.service.get().subscribe({
      next: (d) => {
        this.data.set(d ?? {});
        const shipment = (d as { shipment?: { defaultChargeableWeightKg?: number } })?.shipment;
        if (shipment?.defaultChargeableWeightKg != null) {
          this.defaultWeightInput.set(Number(shipment.defaultChargeableWeightKg));
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.razorpayService.get().subscribe({
      next: (r) => {
        this.razorpay.set(r);
        this.razorpayEnabledInput.set(r.enabled);
        this.razorpayKeyIdInput.set(r.keyId ?? '');
      },
      error: () => { /* Settings is COMPANY_ADMIN-only already; nothing further to show. */ }
    });
  }

  onWeightInput(e: Event): void {
    const v = (e.target as HTMLInputElement).value;
    this.defaultWeightInput.set(v === '' ? null : Number(v));
  }

  /** Only field on this page with real edit wiring so far — every other section is
   *  still preview-only (no edit dialog exists yet). Patches just the shipment
   *  section; the rest of the settings row is untouched. */
  saveDefaultWeight(): void {
    const value = this.defaultWeightInput();
    if (value == null || value <= 0) return;
    this.savingWeight.set(true);
    this.service.patchSection('shipment', { defaultChargeableWeightKg: value }).subscribe({
      next: (d) => {
        const shipment = (d as { shipment?: unknown })?.shipment;
        if (shipment) this.data.update((prev) => ({ ...prev, shipment }));
        this.savingWeight.set(false);
        this.notify.success('Default chargeable weight updated');
      },
      error: () => this.savingWeight.set(false)
    });
  }

  onRazorpayEnabledInput(e: Event): void {
    this.razorpayEnabledInput.set((e.target as HTMLInputElement).checked);
  }

  onRazorpayKeyIdInput(e: Event): void {
    this.razorpayKeyIdInput.set((e.target as HTMLInputElement).value);
  }

  onRazorpayKeySecretInput(e: Event): void {
    this.razorpayKeySecretInput.set((e.target as HTMLInputElement).value);
  }

  saveRazorpay(): void {
    this.savingRazorpay.set(true);
    this.razorpayService.update({
      enabled: this.razorpayEnabledInput(),
      keyId: this.razorpayKeyIdInput().trim(),
      keySecret: this.razorpayKeySecretInput().trim() || null
    }).subscribe({
      next: (r) => {
        this.razorpay.set(r);
        // Never leave the raw secret sitting in the DOM/memory longer than it takes to save.
        this.razorpayKeySecretInput.set('');
        this.savingRazorpay.set(false);
        this.notify.success('Razorpay configuration updated');
      },
      error: () => this.savingRazorpay.set(false)
    });
  }

  /** A few representative keys per section, read from the live settings object. */
  preview(section: string): { k: string; v: string }[] {
    const d = this.data() as Record<string, Record<string, unknown>>;
    const s = d[section] ?? {};
    return Object.entries(s).slice(0, 4).map(([k, v]) => ({
      k: k.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()),
      v: v === null || v === undefined || v === '' ? '—' : String(v)
    }));
  }
}
