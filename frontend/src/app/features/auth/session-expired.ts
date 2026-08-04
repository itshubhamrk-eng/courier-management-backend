import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '@core/auth/auth.service';
import { UiButton } from '@shared/components/ui-button/ui-button';

/** Terminal state after a refresh fails or the session is revoked. Clears any residue
 *  and sends the user back to sign in. */
@Component({
  selector: 'app-session-expired',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, UiButton],
  template: `
    <div class="state app-card">
      <div class="state__icon"><mat-icon>schedule</mat-icon></div>
      <h1 class="text-h1">Session expired</h1>
      <p class="text-caption">For your security you've been signed out after a period of inactivity.
        Please sign in again to continue.</p>
      <app-button icon="login" (pressed)="signIn()">Sign in again</app-button>
    </div>
  `,
  styles: [`
    .state { width:420px; max-width:100%; padding:40px 32px; text-align:center; display:flex;
      flex-direction:column; align-items:center; gap:12px; }
    .state__icon { width:64px; height:64px; border-radius:50%; display:grid; place-items:center;
      margin-bottom:8px; background:var(--brand-50); color:var(--brand-600); }
    .state__icon mat-icon { font-size:32px; width:32px; height:32px; }
    app-button { margin-top:12px; }
  `]
})
export class SessionExpired {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  signIn(): void {
    this.auth.clearSession();
    this.router.navigateByUrl('/login');
  }
}
