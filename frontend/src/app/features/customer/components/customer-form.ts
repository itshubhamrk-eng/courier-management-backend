import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors,
  Validators
} from '@angular/forms';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import {
  CustomerResponse, CustomerType, CUSTOMER_TYPES, CreateCustomerRequest, UpdateCustomerRequest
} from '@core/models/customer.model';

const CODE = /^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[+]?[0-9 \-]{7,20}$/;
const GSTIN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;
const PAN = /^[A-Z]{5}[0-9]{4}[A-Z]$/;

const TYPE_OPTS: SelectOption[] = CUSTOMER_TYPES.map((t) => ({
  value: t, label: t.charAt(0) + t.slice(1).toLowerCase()
}));

/** Checked on the trimmed value, same reasoning as BranchForm's `emailish`. */
function emailish(control: AbstractControl): ValidationErrors | null {
  const value = (control.value ?? '').trim();
  return !value || EMAIL.test(value) ? null : { email: true };
}

function upperPattern(pattern: RegExp) {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = (control.value ?? '').trim().toUpperCase();
    return !value || pattern.test(value) ? null : { pattern: true };
  };
}

/**
 * Reactive create/edit editor for a customer. Validators mirror CreateCustomerRequest /
 * UpdateCustomerRequest. `customerCode` is create-only (blank generates one server-side;
 * shown read-only in edit since it is immutable). GST is required only when `customerType`
 * is BUSINESS — enforced here too so the error surfaces before the API round-trip, mirroring
 * the server's `Customer.applyInvariants()` rule.
 */
@Component({
  selector: 'app-customer-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="cform">
      <app-card title="Basic Information" subtitle="Identity and classification.">
        <div class="grid">
          @if (isCreate()) {
            <app-input [control]="c('customerCode')" label="Customer Code" placeholder="Blank generates one" [maxLength]="50" />
          } @else {
            <div class="stat"><span class="stat__l">Customer Code</span>
              <span class="stat__v mono">{{ customer()?.customerCode || '—' }}</span><span class="stat__h">Immutable</span></div>
          }
          <app-select [control]="c('customerType')" label="Customer Type" [options]="types" placeholder="Select a type" />
          @if (isBusiness()) {
            <app-input [control]="c('companyName')" label="Company Name" placeholder="Acme Traders" [maxLength]="150" />
          }
        </div>
      </app-card>

      <app-card title="Contact Person" subtitle="Who to reach for this customer.">
        <div class="grid">
          <app-input [control]="c('firstName')" label="First Name" [required]="true" placeholder="Asha" [maxLength]="100" />
          <app-input [control]="c('middleName')" label="Middle Name" placeholder="" [maxLength]="100" />
          <app-input [control]="c('lastName')" label="Last Name" [required]="true" placeholder="Shah" [maxLength]="100" />
        </div>
      </app-card>

      <app-card title="Contact" subtitle="How to reach this customer.">
        <div class="grid">
          <app-input [control]="c('mobile')" label="Mobile" type="tel" [required]="true" placeholder="+91 90000 00000" [maxLength]="20" />
          <app-input [control]="c('alternateMobile')" label="Alternate Mobile" type="tel" placeholder="+91 80 0000 0000" [maxLength]="20" />
          <app-input [control]="c('email')" label="Email" type="email" placeholder="asha@company.com" [maxLength]="255" />
        </div>
      </app-card>

      <app-card title="Tax Details" [subtitle]="isBusiness() ? 'GST is mandatory for a business customer.' : 'Optional for an individual customer.'">
        <div class="grid">
          <app-input [control]="c('gstNumber')" label="GST Number" [required]="isBusiness()" placeholder="27ABCDE1234F1Z5" [maxLength]="15" />
          <app-input [control]="c('panNumber')" label="PAN Number" placeholder="ABCDE1234F" [maxLength]="10" />
        </div>
      </app-card>

      <div class="cform__bar">
        <span class="cform__note">@if (form.invalid && form.touched) { Fix the highlighted fields before saving. }</span>
        <div class="cform__actions">
          <app-button variant="stroked" (pressed)="cancelled.emit()">Cancel</app-button>
          <app-button type="submit" icon="save" [loading]="saving()" [disabled]="!isCreate() && form.pristine">
            {{ isCreate() ? 'Create Customer' : 'Save Changes' }}
          </app-button>
        </div>
      </div>
    </form>
  `,
  styles: [`
    .cform { display:flex; flex-direction:column; gap:16px; }
    .grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px 20px; }
    .stat { display:flex; flex-direction:column; gap:6px; justify-content:center; }
    .stat__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .stat__v { font:600 14px var(--font-sans); color:var(--content-fg); }
    .stat__h { font:400 12px var(--font-sans); color:var(--content-muted); }
    .mono { font-family:var(--font-mono, ui-monospace); }
    .cform__bar { position:sticky; bottom:0; display:flex; align-items:center; justify-content:space-between;
      gap:16px; padding:14px 16px; background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .cform__note { font:500 13px var(--font-sans); color:var(--danger); }
    .cform__actions { display:flex; gap:10px; }
    @media (max-width:760px){ .grid { grid-template-columns:1fr; } }
  `]
})
export class CustomerForm {
  private readonly fb = inject(FormBuilder);

  readonly mode = input<'create' | 'edit'>('create');
  readonly customer = input<CustomerResponse | null>(null);
  readonly saving = input(false);

  readonly saved = output<CreateCustomerRequest | UpdateCustomerRequest>();
  readonly cancelled = output<void>();

  protected readonly types = TYPE_OPTS;
  protected readonly isCreate = computed(() => this.mode() === 'create');
  private readonly hydrated = signal(false);

  protected readonly form: FormGroup = this.build();
  private readonly typeValue = signal<CustomerType>('INDIVIDUAL');
  protected readonly isBusiness = computed(() => this.typeValue() === 'BUSINESS');

  constructor() {
    effect(() => { const c = this.customer(); if (c && this.mode() === 'edit') this.hydrate(c); });
    this.form.get('customerType')!.valueChanges.subscribe((v) => this.typeValue.set(v));
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  private hydrate(cust: CustomerResponse): void {
    if (this.hydrated()) return;
    this.form.patchValue({
      customerType: cust.customerType, companyName: cust.companyName ?? '',
      firstName: cust.firstName, middleName: cust.middleName ?? '', lastName: cust.lastName,
      mobile: cust.mobile, alternateMobile: cust.alternateMobile ?? '', email: cust.email ?? '',
      gstNumber: cust.gstNumber ?? '', panNumber: cust.panNumber ?? ''
    }, { emitEvent: true });
    this.typeValue.set(cust.customerType);
    this.form.markAsPristine();
    this.hydrated.set(true);
  }

  private build(): FormGroup {
    return this.fb.group({
      customerCode: ['', [Validators.pattern(CODE), Validators.maxLength(50)]],
      customerType: ['INDIVIDUAL' as CustomerType, Validators.required],
      companyName: ['', Validators.maxLength(150)],
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      middleName: ['', Validators.maxLength(100)],
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      mobile: ['', [Validators.required, Validators.pattern(PHONE), Validators.maxLength(20)]],
      alternateMobile: ['', [Validators.pattern(PHONE), Validators.maxLength(20)]],
      email: ['', [emailish, Validators.maxLength(255)]],
      gstNumber: ['', [upperPattern(GSTIN), Validators.maxLength(15)]],
      panNumber: ['', [upperPattern(PAN), Validators.maxLength(10)]]
    });
  }

  protected submit(): void {
    if (this.isBusiness() && !this.c('gstNumber').value?.trim()) {
      this.c('gstNumber').setErrors({ required: true });
    }
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    const v = this.form.getRawValue();
    const trim = (s: string | null) => (s && s.trim() ? s.trim() : null);
    const upper = (s: string | null) => (s && s.trim() ? s.trim().toUpperCase() : null);

    const common = {
      customerType: v.customerType as CustomerType,
      companyName: trim(v.companyName),
      firstName: v.firstName.trim(), middleName: trim(v.middleName), lastName: v.lastName.trim(),
      mobile: v.mobile.trim(), alternateMobile: trim(v.alternateMobile), email: trim(v.email),
      gstNumber: upper(v.gstNumber), panNumber: upper(v.panNumber)
    };

    if (this.isCreate()) {
      this.saved.emit({ customerCode: trim(v.customerCode), ...common });
    } else {
      this.saved.emit({ ...common, version: this.customer()!.version });
    }
  }
}
