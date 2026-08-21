import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '@core/auth/auth.service';
import { NotificationService } from '@core/services/notification.service';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { HttpErrorResponse } from '@angular/common/http';
import { environment } from '@env/environment';

/** Sign-in. Company slug (company code) is optional — the backend resolves it. */
@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, UiInput, UiButton],
  template: `
    <div class="login app-card">
      <div class="login__head">
        <h1 class="text-h1">Welcome back</h1>
        <p class="text-caption">Sign in to your Courier SaaS console.</p>
      </div>
      <form class="login__form" [formGroup]="form" (ngSubmit)="submit()">
        <app-input [control]="ctrl('companyCode')" label="Company code" placeholder="e.g. ACME_LOGISTICS"
                   icon="apartment" autocomplete="organization" [maxLength]="50" />
        <app-input [control]="ctrl('email')" label="Email" type="email" placeholder="you@company.com"
                   icon="mail" [required]="true" autocomplete="username" [maxLength]="255" />
        <app-input [control]="ctrl('password')" label="Password" type="password" placeholder="••••••••"
                   icon="lock" [required]="true" [togglePassword]="true" autocomplete="current-password" [maxLength]="72" />
        <div class="login__row">
          <label class="login__remember">
            <input type="checkbox" formControlName="rememberMe" /> <span>Remember me</span>
          </label>
          <a class="login__forgot" routerLink="/forgot-password">Forgot password?</a>
        </div>
        <app-button type="submit" [loading]="loading()" [disabled]="form.invalid">Sign in</app-button>
        @if (error()) { <p class="login__error">{{ error() }}</p> }
      </form>

      @if (devMode) {
        <div class="login__dev">
          <span class="login__dev-label">Dev quick fill</span>
          <div class="login__dev-btns">
            <button type="button" class="login__dev-btn" (click)="fill('admin')">Super Admin</button>
            <button type="button" class="login__dev-btn" (click)="fill('company')">Company Admin</button>
            <button type="button" class="login__dev-btn" (click)="fill('pune')">Pune Branch</button>
            <button type="button" class="login__dev-btn" (click)="fill('latur')">Latur Branch</button>
            <button type="button" class="login__dev-btn" (click)="fill('hub')">Hub</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .login { width:400px; max-width:100%; padding:36px; }
    .login__head { margin-bottom:24px; }
    .login__form { display:flex; flex-direction:column; gap:16px; }
    .login__row { display:flex; align-items:center; justify-content:space-between; }
    .login__remember { display:flex; align-items:center; gap:8px; font:500 13px var(--font-sans); color:var(--content-muted); cursor:pointer; }
    .login__forgot { font:500 13px var(--font-sans); color:var(--brand-600); text-decoration:none; }
    .login__error { background:var(--danger-bg); color:var(--danger); padding:10px 12px; border-radius:10px; font:500 13px var(--font-sans); margin:0; }
    app-button { display:block; }
    .login__dev { margin-top:20px; padding-top:16px; border-top:1px dashed var(--surface-border); }
    .login__dev-label { display:block; font:600 10px var(--font-sans); letter-spacing:.06em; text-transform:uppercase; color:var(--content-muted); margin-bottom:8px; }
    .login__dev-btns { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
    .login__dev-btn { flex:1; padding:8px 10px; border:0; border-radius:10px; background:var(--surface-muted);
      box-shadow:var(--shadow-clay-inset); color:var(--content-fg); font:600 12px var(--font-sans); cursor:pointer; transition:color .15s; }
    .login__dev-btn:hover { color:var(--brand-700); }
  `]
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notify = inject(NotificationService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Dev-only credential quick-fill; stripped from production builds. */
  readonly devMode = !environment.production;

  readonly form = this.fb.nonNullable.group({
    companyCode: ['', Validators.maxLength(50)],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    rememberMe: [false]
  });

  ctrl(name: 'companyCode' | 'email' | 'password') { return this.form.controls[name]; }

  /** Fills known dev credentials. Super admin signs in with no company code; the rest use
   *  the current dev company, COMPANY-C1 ("First Company") — LEGACY_CO's fixtures were
   *  wiped in the 2026-07-30 DB truncation and never rebuilt. */
  fill(kind: 'admin' | 'company' | 'pune' | 'latur' | 'hub'): void {
    this.error.set(null);
    const creds: Record<typeof kind, { companyCode: string; email: string; password: string }> = {
      admin:   { companyCode: '', email: 'super.admin@gmail.com', password: 'Pass@1234' },
      company: { companyCode: 'COMPANY-C1', email: 'first.admin@gmail.com', password: 'Password@1234' },
      pune:    { companyCode: 'COMPANY-C1', email: 'pune@gmail.com', password: 'Password@1234' },
      latur:   { companyCode: 'COMPANY-C1', email: 'latur@gmail.com', password: 'Password@1234' },
      hub:     { companyCode: 'COMPANY-C1', email: 'hub@legacy.test', password: 'Password@123' }
    };
    const { companyCode, email, password } = creds[kind];
    this.form.patchValue({ companyCode, email, password });
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true); this.error.set(null);
    const { companyCode, email, password, rememberMe } = this.form.getRawValue();
    this.auth.login({ companyCode: companyCode || undefined, email, password, rememberMe }).subscribe({
      next: () => {
        this.notify.success('Signed in.');
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(err.error?.message ?? 'Invalid credentials. Please try again.');
      }
    });
  }
}
