import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors,
  Validators
} from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import {
  BranchResponse, BranchType, BRANCH_TYPES,
  BranchUserRequest, CreateBranchRequest, UpdateBranchRequest
} from '@core/models/branch.model';
import { Lookup } from '../branch.service';

const CODE = /^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[+]?[0-9 \-]{7,20}$/;
const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

const TYPE_OPTS: SelectOption[] = BRANCH_TYPES.map((t) => ({
  value: t, label: t.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}));
const DAY_OPTS: SelectOption[] = DAYS.map((d) => ({ value: d, label: d }));

/**
 * Email, checked on the trimmed value — the submit trims and so does the server, so
 * rejecting a pasted " a@b.test " would be an error about nothing.
 */
function emailish(control: AbstractControl): ValidationErrors | null {
  const value = (control.value ?? '').trim();
  return !value || EMAIL.test(value) ? null : { email: true };
}

/**
 * Reactive create/edit editor for a branch. Validators mirror CreateBranchRequest /
 * UpdateBranchRequest so a bad body is rejected before the API. `branchCode` is create-only
 * (immutable, shown read-only in edit). The manager is set on create but changed through
 * the assign-manager endpoint afterwards, so in edit it is read-only with a pointer to the
 * dialog; `status` likewise has its own lifecycle endpoints. Edit emits UpdateBranchRequest
 * carrying the last-read `version`.
 *
 * On create the form also carries the branch's **login account** (the `branchUser` block):
 * one submit creates the branch, its user and its wallet. Every field there is optional —
 * left empty, the backend derives `<branch-code>@<company>.local` and generates a password
 * it returns once. In edit the section is absent: the account exists and is managed from
 * Users.
 *
 * Honesty: the UI-10 spec's Owner/Contact-Person and GST/PAN fields have no column on the
 * backend branch (GST/PAN are company-level); they are omitted rather than faked. "Vendor
 * Type" maps to `branchType`, "Area" to `district`, "Pincode" to `postalCode`.
 */
@Component({
  selector: 'app-branch-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatSlideToggleModule, UiCard, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="bform">
      <app-card title="Basic Information" subtitle="Identity and classification.">
        <div class="grid">
          @if (isCreate()) {
            <app-input [control]="c('branchCode')" label="Branch Code" [required]="true" placeholder="PUNE_MAIN" />
          } @else {
            <div class="stat"><span class="stat__l">Branch Code</span>
              <span class="stat__v mono">{{ branch()?.branchCode || '—' }}</span><span class="stat__h">Immutable</span></div>
          }
          <app-input [control]="c('branchName')" label="Branch Name" [required]="true" placeholder="Pune Main Branch" />
          <app-select [control]="c('branchType')" label="Vendor / Branch Type" [options]="types" placeholder="Select a type" />
          @if (isCreate()) {
            <app-select [control]="c('managerId')" label="Manager" [options]="managerOptions()"
                        [allowEmpty]="true" emptyLabel="Unassigned" placeholder="Select a company user" />
          } @else {
            <div class="stat"><span class="stat__l">Manager</span>
              <span class="stat__v">{{ managerName() }}</span><span class="stat__h">Change via “Assign Manager”</span></div>
          }
        </div>
      </app-card>

      <app-card title="Contact" subtitle="How to reach this branch.">
        <div class="grid">
          <app-input [control]="c('email')" label="Email" type="email" placeholder="pune@company.com" />
          <app-input [control]="c('mobile')" label="Mobile" type="tel" placeholder="+91 90000 00000" />
          <app-input [control]="c('alternateMobile')" label="Alternate Mobile" type="tel" placeholder="+91 80 0000 0000" />
        </div>
      </app-card>

      <app-card title="Address" subtitle="Where the branch is located.">
        <div class="grid">
          <app-input [control]="c('addressLine1')" label="Address Line 1" placeholder="Building, street" />
          <app-input [control]="c('addressLine2')" label="Address Line 2" placeholder="Landmark, area" />
          <app-input [control]="c('country')" label="Country" placeholder="India" />
          <app-input [control]="c('state')" label="State" placeholder="Maharashtra" />
          <app-input [control]="c('city')" label="City" placeholder="Pune" />
          <app-input [control]="c('district')" label="Area / District" placeholder="Shivajinagar" />
          <app-input [control]="c('taluka')" label="Taluka" placeholder="Haveli" />
          <app-input [control]="c('postalCode')" label="Pincode" placeholder="411005" />
        </div>
      </app-card>

      <app-card title="Working Hours" subtitle="Optional — closing must be after opening.">
        <div class="grid">
          <label class="dt"><span class="dt__l">Opening Time</span>
            <input class="dt__i" type="time" [formControl]="c('openingTime')" /></label>
          <label class="dt"><span class="dt__l">Closing Time</span>
            <input class="dt__i" type="time" [formControl]="c('closingTime')" /></label>
          <app-select [control]="c('workingDays')" label="Working Days" [options]="dayOpts" [multiple]="true" placeholder="Any" />
        </div>
      </app-card>

      <app-card title="Operations" subtitle="Which capabilities this branch is allowed.">
        <div class="flags">
          <label class="flag"><mat-slide-toggle [formControl]="c('allowBooking')" />
            <span><strong>Booking</strong><em>Accept new shipment bookings</em></span></label>
          <label class="flag"><mat-slide-toggle [formControl]="c('allowDelivery')" />
            <span><strong>Delivery</strong><em>Deliver shipments to consignees</em></span></label>
          <label class="flag"><mat-slide-toggle [formControl]="c('allowPickup')" />
            <span><strong>Pickup</strong><em>Field pickups from senders</em></span></label>
          <label class="flag"><mat-slide-toggle [formControl]="c('allowManifest')" />
            <span><strong>Manifest</strong><em>Build outbound manifests</em></span></label>
          <label class="flag"><mat-slide-toggle [formControl]="c('allowCashCollection')" />
            <span><strong>Cash Collection</strong><em>Collect COD at counter</em></span></label>
          <label class="flag"><mat-slide-toggle [formControl]="c('allowWallet')" />
            <span><strong>Wallet</strong><em>Prepaid wallet billing</em></span></label>
        </div>
      </app-card>

      @if (isCreate()) {
        <app-card title="Branch User"
                  subtitle="The login for this branch. Created with it, along with its wallet.">
          <div class="grid">
            <app-input [control]="u('email')" label="Login Email" type="email"
                       placeholder="Defaults to the branch email, then to a derived address" />
            <app-input [control]="u('mobile')" label="Mobile" type="tel"
                       placeholder="Defaults to the branch mobile" />
            <app-input [control]="u('firstName')" label="First Name"
                       placeholder="Defaults to the branch name" />
            <app-input [control]="u('lastName')" label="Last Name" placeholder="Branch" />
            <app-input [control]="u('password')" label="Password" type="password"
                       [togglePassword]="true" autocomplete="new-password"
                       placeholder="Leave blank to generate one" />
          </div>
          <p class="bform__hint">
            The account is created active with the <strong>Branch Manager</strong> role and is
            made this branch's manager unless you picked one above. Leave the password blank
            and the generated one is shown <strong>once</strong>, right after saving — it
            cannot be read back, only reset.
          </p>
        </app-card>
      }

      <app-card title="Notes" subtitle="Internal remarks.">
        <app-input [control]="c('remarks')" label="Remarks" placeholder="Any internal note about this branch" />
      </app-card>

      <div class="bform__bar">
        <span class="bform__note">@if (form.invalid && form.touched) { Fix the highlighted fields before saving. }</span>
        <div class="bform__actions">
          <app-button variant="stroked" (pressed)="cancelled.emit()">Cancel</app-button>
          <app-button type="submit" icon="save" [loading]="saving()" [disabled]="!isCreate() && form.pristine">
            {{ isCreate() ? 'Create Branch' : 'Save Changes' }}
          </app-button>
        </div>
      </div>
    </form>
  `,
  styles: [`
    .bform { display:flex; flex-direction:column; gap:16px; }
    .grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px 20px; }
    .stat { display:flex; flex-direction:column; gap:6px; justify-content:center; }
    .stat__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .stat__v { font:600 14px var(--font-sans); color:var(--content-fg); }
    .stat__h { font:400 12px var(--font-sans); color:var(--content-muted); }
    .mono { font-family:var(--font-mono, ui-monospace); }
    .dt { display:flex; flex-direction:column; gap:6px; }
    .dt__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .dt__i { height:42px; padding:0 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); }
    .dt__i:focus { outline:0; border-color:var(--brand-500); box-shadow:0 0 0 3px var(--brand-100); }
    .flags { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px 20px; }
    .flag { display:flex; align-items:center; gap:12px; cursor:pointer; }
    .flag span { display:flex; flex-direction:column; }
    .flag strong { font:600 14px var(--font-sans); color:var(--content-fg); }
    .flag em { font:400 12px var(--font-sans); color:var(--content-muted); font-style:normal; }
    .bform__bar { position:sticky; bottom:0; display:flex; align-items:center; justify-content:space-between;
      gap:16px; padding:14px 16px; background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .bform__note { font:500 13px var(--font-sans); color:var(--danger); }
    .bform__hint { margin:14px 0 0; font:400 12px var(--font-sans); color:var(--content-muted); line-height:1.55; }
    .bform__actions { display:flex; gap:10px; }
    @media (max-width:760px){ .grid, .flags { grid-template-columns:1fr; } }
  `]
})
export class BranchForm {
  private readonly fb = inject(FormBuilder);

  readonly mode = input<'create' | 'edit'>('create');
  readonly branch = input<BranchResponse | null>(null);
  readonly managers = input<Lookup[]>([]);
  readonly saving = input(false);

  readonly saved = output<CreateBranchRequest | UpdateBranchRequest>();
  readonly cancelled = output<void>();

  protected readonly types = TYPE_OPTS;
  protected readonly dayOpts = DAY_OPTS;
  protected readonly isCreate = computed(() => this.mode() === 'create');
  private readonly hydrated = signal(false);

  protected readonly form: FormGroup = this.build();

  protected readonly managerOptions = computed<SelectOption[]>(() =>
    this.managers().map((m) => ({ value: m.id, label: m.hint ? `${m.label} · ${m.hint}` : m.label })));

  protected readonly managerName = computed(() => {
    const id = this.branch()?.managerId;
    if (!id) return 'Unassigned';
    return this.managers().find((m) => m.id === id)?.label ?? '—';
  });

  constructor() {
    effect(() => {
      const editing = this.mode() === 'edit';
      const branchCode = this.form.get('branchCode') as FormControl;
      if (editing) {
        branchCode.disable({ emitEvent: false });
        branchCode.clearValidators();
      } else {
        branchCode.enable({ emitEvent: false });
        branchCode.setValidators([Validators.required, Validators.pattern(CODE)]);
      }
      branchCode.updateValueAndValidity({ emitEvent: false });
    });
    effect(() => { const b = this.branch(); if (b && this.mode() === 'edit') this.hydrate(b); });
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  /** A control inside the create-only `branchUser` group. */
  protected u(name: string): FormControl { return this.form.get(['branchUser', name]) as FormControl; }

  private hydrate(b: BranchResponse): void {
    if (this.hydrated()) return;
    this.form.patchValue({
      branchName: b.branchName ?? '', branchType: b.branchType,
      email: b.email ?? '', mobile: b.mobile ?? '', alternateMobile: b.alternateMobile ?? '',
      addressLine1: b.addressLine1 ?? '', addressLine2: b.addressLine2 ?? '',
      country: b.country ?? '', state: b.state ?? '', city: b.city ?? '',
      district: b.district ?? '', taluka: b.taluka ?? '', postalCode: b.postalCode ?? '',
      openingTime: hm(b.openingTime), closingTime: hm(b.closingTime),
      workingDays: b.workingDays ? b.workingDays.split(',').map((d) => d.trim().toUpperCase()).filter(Boolean) : [],
      allowBooking: b.allowBooking, allowDelivery: b.allowDelivery, allowPickup: b.allowPickup,
      allowManifest: b.allowManifest, allowCashCollection: b.allowCashCollection, allowWallet: b.allowWallet,
      remarks: b.remarks ?? ''
    }, { emitEvent: false });
    this.form.markAsPristine();
    this.hydrated.set(true);
  }

  private build(): FormGroup {
    return this.fb.group({
      branchCode: ['', [Validators.required, Validators.pattern(CODE)]],
      branchName: ['', [Validators.required, Validators.maxLength(150)]],
      branchType: [null as BranchType | null, Validators.required],
      managerId: [null as string | null],
      email: ['', [emailish, Validators.maxLength(255)]],
      mobile: ['', Validators.pattern(PHONE)],
      alternateMobile: ['', Validators.pattern(PHONE)],
      addressLine1: ['', Validators.maxLength(255)],
      addressLine2: ['', Validators.maxLength(255)],
      country: ['', Validators.maxLength(100)],
      state: ['', Validators.maxLength(100)],
      city: ['', Validators.maxLength(100)],
      district: ['', Validators.maxLength(100)],
      taluka: ['', Validators.maxLength(100)],
      postalCode: ['', Validators.maxLength(20)],
      openingTime: [''],
      closingTime: [''],
      workingDays: [[] as string[]],
      allowBooking: [true], allowDelivery: [true], allowPickup: [true],
      allowManifest: [true], allowCashCollection: [true], allowWallet: [false],
      remarks: ['', Validators.maxLength(500)],
      // Create-only. Kept as a group so the payload maps straight onto BranchUserRequest;
      // no minimum length on the password — the server's policy owns that rule and its
      // message names the one that failed.
      branchUser: this.fb.group({
        email: ['', [emailish, Validators.maxLength(255)]],
        firstName: ['', Validators.maxLength(100)],
        lastName: ['', Validators.maxLength(100)],
        mobile: ['', Validators.pattern(PHONE)],
        password: ['', Validators.maxLength(128)]
      })
    });
  }

  protected submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const trim = (s: string | null) => (s && s.trim() ? s.trim() : null);
    const days: string[] = v.workingDays ?? [];

    const common = {
      branchName: v.branchName.trim(), branchType: v.branchType as BranchType,
      email: trim(v.email), mobile: trim(v.mobile), alternateMobile: trim(v.alternateMobile),
      addressLine1: trim(v.addressLine1), addressLine2: trim(v.addressLine2),
      country: trim(v.country), state: trim(v.state), city: trim(v.city),
      district: trim(v.district), taluka: trim(v.taluka), postalCode: trim(v.postalCode),
      openingTime: trim(v.openingTime), closingTime: trim(v.closingTime),
      workingDays: days.length ? days.join(',') : null,
      allowBooking: !!v.allowBooking, allowDelivery: !!v.allowDelivery, allowPickup: !!v.allowPickup,
      allowManifest: !!v.allowManifest, allowCashCollection: !!v.allowCashCollection, allowWallet: !!v.allowWallet,
      remarks: trim(v.remarks)
    };

    if (this.isCreate()) {
      this.saved.emit({
        branchCode: v.branchCode.trim(), managerId: v.managerId || null, ...common,
        branchUser: branchUser(v.branchUser, trim)
      });
    } else {
      this.saved.emit({ ...common, version: this.branch()!.version });
    }
  }
}

/**
 * The user block, or null when the administrator filled none of it in — an all-null object
 * and an absent one mean the same thing to the backend, and sending the object anyway would
 * suggest a choice nobody made.
 */
function branchUser(v: Record<string, string | null>,
                    trim: (s: string | null) => string | null): BranchUserRequest | null {
  const block: BranchUserRequest = {
    email: trim(v['email']), firstName: trim(v['firstName']), lastName: trim(v['lastName']),
    mobile: trim(v['mobile']), password: v['password']?.trim() || null
  };
  return Object.values(block).some((x) => x !== null) ? block : null;
}

/** Backend LocalTime often serialises as HH:mm:ss; the <input type=time> wants HH:mm. */
function hm(t?: string | null): string { return t ? t.slice(0, 5) : ''; }
