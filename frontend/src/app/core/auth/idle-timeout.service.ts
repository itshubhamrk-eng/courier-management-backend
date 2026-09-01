import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '@env/environment';
import { AuthService } from './auth.service';

const ACTIVITY_EVENTS = ['mousedown', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;

/**
 * Signs the user out after {@link environment}.idleTimeoutMinutes of no real activity
 * (mouse, keyboard, scroll, touch) — genuine work resets the clock indefinitely, since
 * every event restarts the same timer. Independent of the access/refresh-token
 * lifecycle: a token can still be technically valid when this fires. Started/stopped by
 * `AdminLayout`, the authenticated shell, so it only ever runs while signed in.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private timer: ReturnType<typeof setTimeout> | null = null;
  private readonly onActivity = () => this.restart();

  start(): void {
    for (const type of ACTIVITY_EVENTS) {
      document.addEventListener(type, this.onActivity, { passive: true });
    }
    this.restart();
  }

  stop(): void {
    for (const type of ACTIVITY_EVENTS) {
      document.removeEventListener(type, this.onActivity);
    }
    if (this.timer) { clearTimeout(this.timer); this.timer = null; }
  }

  private restart(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.expire(), environment.idleTimeoutMinutes * 60_000);
  }

  private expire(): void {
    this.stop();
    // Best-effort server-side revoke; navigate regardless of whether it lands.
    this.auth.logout().subscribe({ complete: () => this.toSessionExpired(), error: () => this.toSessionExpired() });
  }

  private toSessionExpired(): void {
    this.router.navigateByUrl('/session-expired');
  }
}
