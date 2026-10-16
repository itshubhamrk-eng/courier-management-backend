import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PlatformSsoService } from '@core/auth/platform-sso.service';

/**
 * `/sso` — both ends of "Sign in with OCCPL". Opened from the platform with `?company=<platform company id>` it sends
 * the browser to the platform; when the platform sends it back with `?code=…&state=…` it completes the sign-in.
 */
@Component({
  selector: 'app-platform-sign-in',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="login-wrap">
      <div class="login app-card">
        <div class="login__head">
          <h1 class="text-h1">Signing you in</h1>
          @if (!error()) { <p class="text-caption">Connecting to the OCCPL platform…</p> }
        </div>
        @if (error()) {
          <p class="login__error" role="alert">{{ error() }}</p>
          <a class="login__forgot" routerLink="/login">Back to sign in</a>
        }
      </div>
    </div>
  `
})
export class PlatformSignIn implements OnInit {
  private readonly sso = inject(PlatformSsoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const failure = q.get('error_description') ?? q.get('error');
    if (failure) { this.error.set(`The platform sign-in was not completed (${failure}).`); return; }

    this.sso.config().subscribe((config) => {
      if (!config) { this.error.set('Sign in with OCCPL is not available here.'); return; }

      const code = q.get('code');
      const state = q.get('state');
      if (code && state) {
        this.sso.complete(config, code, state).subscribe({
          next: () => this.router.navigateByUrl('/dashboard', { replaceUrl: true }),
          error: (e: unknown) => this.error.set(messageOf(e))
        });
        return;
      }

      const company = q.get('company') ?? this.sso.lastCompany;
      if (!company) { this.error.set('Open Courier from the OCCPL platform to sign in.'); return; }
      this.sso.begin(config, company).catch(() => this.error.set('Could not start the platform sign-in.'));
    });
  }
}

function messageOf(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    return e.error?.message ?? 'Sign-in through the platform is not available for this account.';
  }
  return e instanceof Error ? e.message : 'Sign-in failed. Start again from the platform.';
}
