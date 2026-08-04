import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { CreateUserRequest, UpdateUserRequest, UserProfile } from '@core/models/user.model';
import { CompanyRole } from '@core/models/role.model';
import { Lookup } from '../user.service';

const PHONE = /^[+]?[0-9 \-]{7,20}$/;
const USERNAME = /^[A-Za-z0-9._-]{3,100}$/;

const GENDERS: SelectOption[] = [
  { value: 'MALE', label: 'Male' }, { value: 'FEMALE', label: 'Female' },
  { value: 'OTHER', label: 'Other' }, { value: 'UNSPECIFIED', label: 'Unspecified' }
];

/**
 * Reactive create/edit editor for a user. Validators mirror CreateUserRequest /
 * UpdateUserRequest so a bad body is rejected before the API. In edit mode the identity
 * fields (employee code, email, username) are immutable and shown read-only, password is
 * omitted (own endpoint), and roles are managed through the assign-role dialog — so the
 * form emits UpdateUserRequest carrying the last-read `version`.
 */
@Component({
  selector: 'app-user-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiCard, UiInput, UiSelect, UiButton],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="uform">
      <app-card title="Basic Information" subtitle="Identity and name.">
        <div class="grid">
          @if (isCreate()) {
            <app-input [control]="c('employeeCode')" label="Employee Code" placeholder="EMP001" />
          } @else {
            <div class="stat"><span class="stat__l">Employee Code</span>
              <span class="stat__v">{{ user()?.employeeCode || '—' }}</span><span class="stat__h">Immutable</span></div>
          }
          <app-input [control]="c('employeeId')" label="Employee ID" placeholder="Payroll / HR id" />
          <app-input [control]="c('firstName')" label="First Name" [required]="true" placeholder="Asha" />
          <app-input [control]="c('middleName')" label="Middle Name" placeholder="—" />
          <app-input [control]="c('lastName')" label="Last Name" placeholder="Nair" />
          <app-input [control]="c('displayName')" label="Display Name" placeholder="Shown across the app" />
        </div>
      </app-card>

      <app-card title="Account & Contact" subtitle="Login handle and how to reach them.">
        <div class="grid">
          @if (isCreate()) {
            <app-input [control]="c('email')" label="Email" type="email" [required]="true" placeholder="asha@company.com" />
            <app-input [control]="c('username')" label="Username" placeholder="asha.nair" />
          } @else {
            <div class="stat"><span class="stat__l">Email</span>
              <span class="stat__v">{{ user()?.email }}</span><span class="stat__h">Immutable</span></div>
            <div class="stat"><span class="stat__l">Username</span>
              <span class="stat__v">{{ user()?.username || '—' }}</span><span class="stat__h">Immutable</span></div>
          }
          <app-input [control]="c('mobile')" label="Mobile" type="tel" placeholder="+91 90000 00000" />
          <app-input [control]="c('alternateMobile')" label="Alternate Mobile" type="tel" placeholder="+91 80 0000 0000" />
        </div>
      </app-card>

      @if (isCreate()) {
        <app-card title="Security" subtitle="Leave the password blank to create a PENDING account an admin later activates by resetting the password.">
          <div class="grid">
            <app-input [control]="c('password')" label="Initial Password" type="password" [togglePassword]="true"
                       autocomplete="new-password" placeholder="Optional — min 8 chars" />
          </div>
        </app-card>
      }

      <app-card title="HR Details" subtitle="Employment profile.">
        <div class="grid">
          <app-select [control]="c('gender')" label="Gender" [options]="genders" placeholder="Select" />
          <label class="dt"><span class="dt__l">Date of Birth</span>
            <input class="dt__i" type="date" [formControl]="c('dateOfBirth')" [max]="today" /></label>
          <app-input [control]="c('designation')" label="Designation" placeholder="Operations Executive" />
          <app-input [control]="c('department')" label="Department" placeholder="Operations" />
          <label class="dt"><span class="dt__l">Joining Date</span>
            <input class="dt__i" type="date" [formControl]="c('joiningDate')" /></label>
          <app-select [control]="c('reportingManagerId')" label="Reporting Manager" [options]="managerOptions()"
                      [allowEmpty]="true" emptyLabel="None" placeholder="Select a manager" />
        </div>
      </app-card>

      <app-card title="Assignment" subtitle="Placement within the company.">
        <div class="grid">
          <app-select [control]="c('branchId')" label="Branch" [options]="branchOptions()"
                      [allowEmpty]="true" emptyLabel="Unassigned" placeholder="Select a branch" />
          <app-select [control]="c('hubId')" label="Hub" [options]="hubOptions()"
                      [allowEmpty]="true" emptyLabel="Unassigned" placeholder="Select a hub" />
          @if (isCreate()) {
            <app-select [control]="c('roleIds')" label="Roles" [options]="roleOptions()" [multiple]="true"
                        placeholder="Default role if left empty" />
          }
        </div>
      </app-card>

      <app-card title="Notes" subtitle="Internal remarks.">
        <app-input [control]="c('remarks')" label="Remarks" placeholder="Any internal note about this user" />
      </app-card>

      <div class="uform__bar">
        <span class="uform__note">
          @if (form.invalid && form.touched) { Fix the highlighted fields before saving. }
        </span>
        <div class="uform__actions">
          <app-button variant="stroked" (pressed)="cancelled.emit()">Cancel</app-button>
          <app-button type="submit" icon="save" [loading]="saving()"
                      [disabled]="!isCreate() && form.pristine">
            {{ isCreate() ? 'Create User' : 'Save Changes' }}
          </app-button>
        </div>
      </div>
    </form>
  `,
  styles: [`
    .uform { display:flex; flex-direction:column; gap:16px; }
    .grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px 20px; }
    .stat { display:flex; flex-direction:column; gap:6px; justify-content:center; }
    .stat__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .stat__v { font:600 14px var(--font-sans); color:var(--content-fg); }
    .stat__h { font:400 12px var(--font-sans); color:var(--content-muted); }
    .dt { display:flex; flex-direction:column; gap:6px; }
    .dt__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .dt__i { height:42px; padding:0 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); }
    .dt__i:focus { outline:0; border-color:var(--brand-500); box-shadow:0 0 0 3px var(--brand-100); }
    .uform__bar { position:sticky; bottom:0; display:flex; align-items:center; justify-content:space-between;
      gap:16px; padding:14px 16px; background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .uform__note { font:500 13px var(--font-sans); color:var(--danger); }
    .uform__actions { display:flex; gap:10px; }
    @media (max-width:760px){ .grid{ grid-template-columns:1fr; } }
  `]
})
export class UserForm {
  private readonly fb = inject(FormBuilder);

  readonly mode = input<'create' | 'edit'>('create');
  readonly user = input<UserProfile | null>(null);
  readonly roles = input<CompanyRole[]>([]);
  readonly branches = input<Lookup[]>([]);
  readonly hubs = input<Lookup[]>([]);
  readonly managers = input<Lookup[]>([]);
  readonly saving = input(false);

  readonly saved = output<CreateUserRequest | UpdateUserRequest>();
  readonly cancelled = output<void>();

  protected readonly genders = GENDERS;
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly isCreate = computed(() => this.mode() === 'create');
  private hydrated = signal(false);

  protected readonly form: FormGroup = this.build();

  protected readonly roleOptions = computed<SelectOption[]>(() =>
    this.roles().filter((r) => r.status === 'ACTIVE').map((r) => ({ value: r.id, label: `${r.roleName} (${r.roleCode})` })));
  protected readonly branchOptions = computed<SelectOption[]>(() =>
    this.branches().map((b) => ({ value: b.id, label: b.hint ? `${b.label} · ${b.hint}` : b.label })));
  protected readonly hubOptions = computed<SelectOption[]>(() =>
    this.hubs().map((h) => ({ value: h.id, label: h.hint ? `${h.label} · ${h.hint}` : h.label })));
  protected readonly managerOptions = computed<SelectOption[]>(() =>
    this.managers().map((m) => ({ value: m.id, label: m.hint ? `${m.label} · ${m.hint}` : m.label })));

  constructor() {
    effect(() => { const u = this.user(); if (u && this.mode() === 'edit') this.hydrate(u); });
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  private hydrate(u: UserProfile): void {
    if (this.hydrated()) return;
    this.form.patchValue({
      firstName: u.firstName ?? '', middleName: u.middleName ?? '', lastName: u.lastName ?? '',
      displayName: u.displayName ?? '', mobile: u.mobile ?? '', alternateMobile: u.alternateMobile ?? '',
      gender: u.gender ?? null, dateOfBirth: u.dateOfBirth ?? '', designation: u.designation ?? '',
      department: u.department ?? '', joiningDate: u.joiningDate ?? '',
      reportingManagerId: u.reportingManagerId ?? null, branchId: u.branchId ?? null, hubId: u.hubId ?? null,
      remarks: u.remarks ?? ''
    }, { emitEvent: false });
    this.form.markAsPristine();
    this.hydrated.set(true);
  }

  private build(): FormGroup {
    return this.fb.group({
      // create-only identity
      employeeCode: ['', Validators.maxLength(50)],
      employeeId: ['', Validators.maxLength(50)],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      username: ['', Validators.pattern(USERNAME)],
      password: ['', Validators.minLength(8)],
      roleIds: [[] as string[]],
      // shared
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      middleName: ['', Validators.maxLength(100)],
      lastName: ['', Validators.maxLength(100)],
      displayName: ['', Validators.maxLength(150)],
      mobile: ['', Validators.pattern(PHONE)],
      alternateMobile: ['', Validators.pattern(PHONE)],
      gender: [null as string | null],
      dateOfBirth: [''],
      designation: ['', Validators.maxLength(100)],
      department: ['', Validators.maxLength(100)],
      joiningDate: [''],
      reportingManagerId: [null as string | null],
      branchId: [null as string | null],
      hubId: [null as string | null],
      remarks: ['', Validators.maxLength(500)]
    });
  }

  protected submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const trim = (s: string | null) => (s && s.trim() ? s.trim() : null);

    if (this.isCreate()) {
      this.saved.emit({
        employeeCode: trim(v.employeeCode), employeeId: trim(v.employeeId),
        firstName: v.firstName.trim(), middleName: trim(v.middleName), lastName: trim(v.lastName),
        displayName: trim(v.displayName), email: v.email.trim(), username: trim(v.username),
        mobile: trim(v.mobile), alternateMobile: trim(v.alternateMobile),
        password: trim(v.password), gender: v.gender || null, dateOfBirth: trim(v.dateOfBirth),
        designation: trim(v.designation), department: trim(v.department), joiningDate: trim(v.joiningDate),
        reportingManagerId: v.reportingManagerId || null, branchId: v.branchId || null, hubId: v.hubId || null,
        remarks: trim(v.remarks), roleIds: v.roleIds ?? []
      } as CreateUserRequest);
    } else {
      this.saved.emit({
        firstName: v.firstName.trim(), middleName: trim(v.middleName), lastName: trim(v.lastName),
        displayName: trim(v.displayName), mobile: trim(v.mobile), alternateMobile: trim(v.alternateMobile),
        gender: v.gender || null, dateOfBirth: trim(v.dateOfBirth), designation: trim(v.designation),
        department: trim(v.department), joiningDate: trim(v.joiningDate),
        reportingManagerId: v.reportingManagerId || null, branchId: v.branchId || null, hubId: v.hubId || null,
        remarks: trim(v.remarks), version: this.user()!.version
      } as UpdateUserRequest);
    }
  }
}
