import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { CommunicationService } from './communication.service';
import { CommunicationChannel, CommunicationSetting } from '@core/models/communication.model';

interface ChannelCard {
  channel: CommunicationChannel;
  title: string;
  icon: string;
  configFields: { key: string; label: string; placeholder: string }[];
  secretLabel: string | null;
  providerPlaceholder: string;
}

const CARDS: ChannelCard[] = [
  {
    channel: 'WHATSAPP', title: 'WhatsApp', icon: 'chat',
    configFields: [
      { key: 'phoneNumberId', label: 'Phone Number ID', placeholder: '10-15 digit Meta phone number id' },
      { key: 'businessAccountId', label: 'Business Account ID', placeholder: 'Meta WABA id' }
    ],
    secretLabel: 'Access Token', providerPlaceholder: 'META_CLOUD_API'
  },
  {
    channel: 'SMS', title: 'SMS', icon: 'sms',
    configFields: [
      { key: 'apiUrl', label: 'API URL', placeholder: 'https://gateway.example.com/send' },
      { key: 'senderId', label: 'Sender ID', placeholder: 'ACMECR' }
    ],
    secretLabel: 'API Key', providerPlaceholder: 'e.g. MSG91, TEXTLOCAL, TWILIO'
  },
  {
    channel: 'EMAIL', title: 'Email', icon: 'email',
    configFields: [
      { key: 'fromName', label: 'From Name', placeholder: 'Acme Logistics' },
      { key: 'fromEmail', label: 'From Email', placeholder: 'notifications@acme.com' }
    ],
    secretLabel: null, providerPlaceholder: 'SMTP'
  }
];

/** One card per channel: Enable/Disable, Provider, Configuration, Test Connection. Never
 *  exposes a stored secret — only whether one is set (a blank secret field on save keeps
 *  it unchanged). */
@Component({
  selector: 'app-channel-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatSlideToggleModule, UiCard, UiInput, UiButton],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Channel Settings</h1><p class="text-caption">Enable each channel and configure its provider. Secrets are never shown back.</p></div>
      </header>

      <section class="cards">
        @for (card of cards; track card.channel) {
          <app-card [title]="card.title">
            <form [formGroup]="forms[card.channel]" (ngSubmit)="save(card)" class="csettings">
              <label class="flag"><mat-slide-toggle [formControl]="ctl(card.channel, 'enabled')" />
                <span><strong>Enabled</strong><em>Master switch for this channel, this company</em></span></label>

              <app-input [control]="ctl(card.channel, 'provider')" label="Provider" [placeholder]="card.providerPlaceholder" [maxLength]="50" />

              @for (field of card.configFields; track field.key) {
                <app-input [control]="ctl(card.channel, field.key)" [label]="field.label" [placeholder]="field.placeholder" />
              }

              @if (card.secretLabel) {
                <app-input [control]="ctl(card.channel, 'secret')" [label]="card.secretLabel" type="password"
                  autocomplete="new-password"
                  [placeholder]="secretConfigured(card.channel) ? 'Configured — leave blank to keep it' : 'Not set'" />
              }

              <div class="status">
                @if (testResult(card.channel); as r) {
                  <span [class.ok]="r.ok" [class.bad]="!r.ok">{{ r.message }}</span>
                }
              </div>

              <div class="csettings__actions">
                <app-button type="button" variant="stroked" [loading]="testing(card.channel)" (pressed)="test(card)">Test Connection</app-button>
                <app-button type="submit" icon="save" [loading]="saving(card.channel)">Save</app-button>
              </div>
            </form>
          </app-card>
        }
      </section>
    </div>
  `,
  styles: [`
    .cards { display:grid; grid-template-columns:repeat(auto-fit, minmax(320px, 1fr)); gap:20px; }
    .csettings { display:flex; flex-direction:column; gap:14px; }
    .flag { display:flex; align-items:center; gap:12px; }
    .flag span { display:flex; flex-direction:column; }
    .flag em { font-style:normal; font-size:12px; color:var(--content-muted); }
    .status { min-height:18px; font:500 12px var(--font-sans); }
    .status .ok { color:var(--success); }
    .status .bad { color:var(--danger); }
    .csettings__actions { display:flex; gap:10px; justify-content:flex-end; }
  `]
})
export class ChannelSettings implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CommunicationService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);

  protected readonly cards = CARDS;
  protected readonly forms: Record<CommunicationChannel, FormGroup> = {
    WHATSAPP: this.buildForm(), SMS: this.buildForm(), EMAIL: this.buildForm()
  };

  private readonly settings = signal<Map<CommunicationChannel, CommunicationSetting>>(new Map());
  private readonly savingState = signal<Set<CommunicationChannel>>(new Set());
  private readonly testingState = signal<Set<CommunicationChannel>>(new Set());
  private readonly testResults = signal<Map<CommunicationChannel, { ok: boolean; message: string }>>(new Map());

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Communication' }, { label: 'Channel Settings' }]);
    this.load();
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      enabled: [false],
      provider: ['', Validators.maxLength(50)],
      phoneNumberId: [''], businessAccountId: [''],
      apiUrl: [''], senderId: [''],
      fromName: [''], fromEmail: [''],
      secret: ['']
    });
  }

  protected ctl(channel: CommunicationChannel, name: string): FormControl {
    return this.forms[channel].get(name) as FormControl;
  }

  protected secretConfigured(channel: CommunicationChannel): boolean {
    return this.settings().get(channel)?.secretConfigured ?? false;
  }

  protected saving(channel: CommunicationChannel): boolean {
    return this.savingState().has(channel);
  }

  protected testing(channel: CommunicationChannel): boolean {
    return this.testingState().has(channel);
  }

  protected testResult(channel: CommunicationChannel) {
    return this.testResults().get(channel) ?? null;
  }

  private load(): void {
    this.service.listSettings().subscribe((rows) => {
      const map = new Map(rows.map((r) => [r.channel, r]));
      this.settings.set(map);
      for (const row of rows) {
        this.forms[row.channel].patchValue({
          enabled: row.enabled, provider: row.provider ?? '', ...row.config, secret: ''
        }, { emitEvent: false });
      }
    });
  }

  protected save(card: ChannelCard): void {
    const form = this.forms[card.channel];
    const v = form.getRawValue();
    const config: Record<string, string> = {};
    for (const field of card.configFields) {
      if (v[field.key]) config[field.key] = v[field.key];
    }
    this.savingState.update((s) => new Set(s).add(card.channel));
    this.service.upsertSetting(card.channel, {
      enabled: !!v.enabled, provider: v.provider || null, config,
      secret: v.secret ? v.secret.trim() : null
    }).subscribe({
      next: (saved) => {
        this.settings.update((m) => new Map(m).set(card.channel, saved));
        form.patchValue({ secret: '' }, { emitEvent: false });
        this.savingState.update((s) => { const n = new Set(s); n.delete(card.channel); return n; });
        this.notify.success(`${card.title} settings saved.`);
      },
      error: () => this.savingState.update((s) => { const n = new Set(s); n.delete(card.channel); return n; })
    });
  }

  protected test(card: ChannelCard): void {
    this.testingState.update((s) => new Set(s).add(card.channel));
    this.service.testConnection(card.channel).subscribe({
      next: (result) => {
        this.testResults.update((m) => new Map(m).set(card.channel, result));
        this.testingState.update((s) => { const n = new Set(s); n.delete(card.channel); return n; });
      },
      error: () => this.testingState.update((s) => { const n = new Set(s); n.delete(card.channel); return n; })
    });
  }
}
