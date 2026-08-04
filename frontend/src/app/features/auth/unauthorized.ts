import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { UiButton } from '@shared/components/ui-button/ui-button';

/** Shown when an authenticated user hits a route their roles/permissions don't allow. */
@Component({
  selector: 'app-unauthorized',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, UiButton],
  template: `
    <div class="state app-card">
      <div class="state__icon state__icon--warn"><mat-icon>lock</mat-icon></div>
      <h1 class="text-h1">Access denied</h1>
      <p class="text-caption">You don't have permission to view this page. If you think this is a
        mistake, contact your administrator.</p>
      <app-button icon="home" (pressed)="goHome()">Back to dashboard</app-button>
    </div>
  `,
  styles: [`
    .state { width:420px; max-width:100%; padding:40px 32px; text-align:center; display:flex;
      flex-direction:column; align-items:center; gap:12px; }
    .state__icon { width:64px; height:64px; border-radius:50%; display:grid; place-items:center; margin-bottom:8px; }
    .state__icon mat-icon { font-size:32px; width:32px; height:32px; }
    .state__icon--warn { background:var(--warning-bg, #fef3c7); color:var(--warning, #d97706); }
    app-button { margin-top:12px; }
  `]
})
export class Unauthorized {
  private readonly router = inject(Router);
  goHome(): void { this.router.navigateByUrl('/dashboard'); }
}
