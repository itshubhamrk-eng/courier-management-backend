import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '@core/auth/auth.service';
import { ThemeService } from '@core/services/theme.service';
import { environment } from '@env/environment';
import { Breadcrumb } from './breadcrumb';
import { GlobalSearch } from './global-search';
import { NotificationMenu } from './notification-menu';
import { UserMenu } from './user-menu';

/**
 * Top bar: sidebar toggle · company brand · breadcrumb · global search · theme switch ·
 * notifications · user profile menu. Composed from small reusable components.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, Breadcrumb, GlobalSearch, NotificationMenu, UserMenu],
  template: `
    <header class="hd">
      <div class="hd__left">
        <button class="hd__icon-btn" (click)="toggleSidebar.emit()" aria-label="Toggle navigation">
          <mat-icon>menu</mat-icon>
        </button>
        <div class="hd__brand">
          @if (logoUrl() && !logoBroken()) {
            <div class="hd__logo hd__logo--img"><img [src]="logoUrl()" alt="" (error)="logoBroken.set(true)" /></div>
          } @else {
            <div class="hd__logo"><span>CS</span></div>
          }
          <span class="hd__company">{{ companyName() }}</span>
        </div>
        <span class="hd__divider"></span>
        <app-breadcrumb />
      </div>

      <div class="hd__right">
        <app-global-search />
        <button class="hd__icon-btn" (click)="theme.toggle()" [attr.aria-label]="theme.mode() === 'dark' ? 'Light mode' : 'Dark mode'">
          <mat-icon>{{ theme.mode() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
        </button>
        <app-notification-menu />
        <app-user-menu />
      </div>
    </header>
  `,
  styles: [`
    .hd { height:var(--header-h); display:flex; align-items:center; justify-content:space-between; gap:16px;
      padding:0 32px; background:var(--surface); border-bottom:1px solid var(--surface-border);
      position:sticky; top:0; z-index:20; }
    @media (max-width:1024px){ .hd{ padding:0 24px; } }
    @media (max-width:640px){ .hd{ padding:0 16px; gap:8px; } }
    .hd__left, .hd__right { display:flex; align-items:center; gap:8px; min-width:0; }
    .hd__brand { display:flex; align-items:center; gap:9px; }
    .hd__logo { width:30px; height:30px; border-radius:8px; background:var(--brand-600); color:#fff;
      display:grid; place-items:center; font:800 12px var(--font-sans); flex-shrink:0; overflow:hidden; }
    .hd__logo--img { background:var(--surface-muted); }
    .hd__logo--img img { width:100%; height:100%; object-fit:contain; }
    .hd__company { font:700 15px var(--font-sans); color:var(--content-fg); white-space:nowrap; }
    .hd__divider { width:1px; height:24px; background:var(--surface-border); margin:0 4px; }
    .hd__icon-btn { position:relative; display:grid; place-items:center; width:40px; height:40px;
      border-radius:10px; border:0; background:transparent; color:var(--content-muted); cursor:pointer; }
    .hd__icon-btn:hover { background:var(--surface-muted); color:var(--content-fg); }

    /* Below the breadcrumb-crowding point, drop brand text and divider + breadcrumb. */
    @media (max-width: 900px) { .hd__divider, app-breadcrumb { display:none; } }
    @media (max-width: 560px) { .hd__company { display:none; } }
  `]
})
export class Header {
  protected readonly theme = inject(ThemeService);
  private readonly auth = inject(AuthService);
  readonly toggleSidebar = output<void>();

  /** Falls back to the generic app name until the signed-in company sets its own. */
  protected readonly companyName = computed(() => this.auth.companyName() || environment.appName);
  protected readonly logoUrl = computed(() => this.auth.companyLogo());
  protected readonly logoBroken = signal(false);
}
