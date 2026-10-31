import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { NavigationService } from '@core/navigation/navigation.service';
import { AuthService } from '@core/auth/auth.service';
import { environment } from '@env/environment';

/**
 * Dark, collapsible, permission-filtered sidebar. The menu, its visibility, collapse and
 * per-group expand state all come from {@link NavigationService} — this component makes no
 * authorisation decision. Three responsive modes are driven by the shell:
 *  - desktop expanded (default), desktop collapsed (icon rail),
 *  - mobile drawer (`mobileOpen`, slides in over a shell-rendered backdrop).
 * Groups are real <button>s and leaves real <a>s, so it is keyboard-navigable out of the box;
 * `aria-expanded` / `aria-current` expose group and active state to assistive tech.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  template: `
    <nav class="sb app-scroll" [class.sb--collapsed]="rail()" [class.sb--open]="mobileOpen()"
         [style.--sb-fs.px]="fontSize()" aria-label="Primary">
      <div class="sb__brand">
        @if (logoUrl() && !logoBroken()) {
          <div class="sb__logo sb__logo--img"><img [src]="logoUrl()" alt="" (error)="logoBroken.set(true)" /></div>
        } @else {
          <div class="sb__logo"><span>{{ initials() }}</span></div>
        }
        @if (!rail()) { <span class="sb__brand-name">{{ companyName() }}</span> }
      </div>

      <ul class="sb__list" role="list">
        @for (item of nav.menu(); track item.id) {
          <li>
            @if (item.children?.length) {
              <!-- Expandable section/group -->
              <button type="button" class="sb__item sb__item--group"
                      [class.sb__item--active]="nav.isGroupActive(item)"
                      [attr.aria-expanded]="!rail() && nav.isExpanded(item)"
                      [attr.aria-controls]="'grp-' + item.id"
                      [attr.data-tour]="item.id"
                      [title]="rail() ? item.title : ''"
                      (click)="nav.toggle(item)">
                <mat-icon class="sb__icon">{{ item.icon }}</mat-icon>
                @if (!rail()) {
                  <span class="sb__label">{{ item.title }}</span>
                  <mat-icon class="sb__chevron" [class.sb__chevron--open]="nav.isExpanded(item)">expand_more</mat-icon>
                }
              </button>
              @if (!rail() && nav.isExpanded(item)) {
                <ul class="sb__children" role="list" [id]="'grp-' + item.id">
                  @for (child of item.children ?? []; track child.id) {
                    <li>
                      <a class="sb__item sb__item--child" [routerLink]="child.route"
                         routerLinkActive="sb__item--active" [routerLinkActiveOptions]="{ exact: true }"
                         #rla="routerLinkActive" [attr.aria-current]="rla.isActive ? 'page' : null"
                         (click)="navigate.emit()">
                        <span class="sb__dot"></span>
                        <span class="sb__label">{{ child.title }}</span>
                        @if (child.badge) { <span class="sb__badge">{{ child.badge }}</span> }
                      </a>
                    </li>
                  }
                </ul>
              }
            } @else {
              <!-- Top-level leaf -->
              <a class="sb__item" [routerLink]="item.route" routerLinkActive="sb__item--active"
                 [routerLinkActiveOptions]="{ exact: true }"
                 #rla="routerLinkActive" [attr.aria-current]="rla.isActive ? 'page' : null"
                 [attr.data-tour]="item.id"
                 [title]="rail() ? item.title : ''" (click)="navigate.emit()">
                <mat-icon class="sb__icon">{{ item.icon }}</mat-icon>
                @if (!rail()) { <span class="sb__label">{{ item.title }}</span> }
                @if (item.badge && !rail()) { <span class="sb__badge">{{ item.badge }}</span> }
              </a>
            }
          </li>
        }
      </ul>

      @if (!rail()) {
        <div class="sb__size" role="group" aria-label="Menu text size">
          <button type="button" class="sb__size-btn" (click)="bump(-1)" [disabled]="fontIdx() === 0"
                  aria-label="Smaller menu text">A−</button>
          <span class="sb__size-val">{{ fontSize() }}px</span>
          <button type="button" class="sb__size-btn sb__size-btn--lg" (click)="bump(1)"
                  [disabled]="fontIdx() === SIZES.length - 1" aria-label="Larger menu text">A+</button>
        </div>
      }
    </nav>
  `,
  styles: [`
    /* Floating crimson panel: 16px off the top/left/bottom, scrolls inside itself. */
    .sb { width:calc(var(--sidebar-w) + (var(--sb-fs, 17px) - 17px) * 10); height:calc(100vh - 32px); margin:16px 0 16px 16px; position:sticky; top:16px;
      align-self:flex-start; overflow-y:auto; overflow-x:hidden; display:flex; flex-direction:column; flex-shrink:0;
      padding:20px 14px; border-radius:28px; color:#fff;
      background:linear-gradient(170deg, #e02550 0%, #b3123a 55%, #7a0e33 100%);
      box-shadow:0 10px 40px rgb(67 56 202 / 12%); transition:width .2s ease; }
    .sb--collapsed { width:var(--sidebar-w-collapsed); }
    .sb__brand { display:flex; align-items:center; gap:12px; padding:0 4px; margin-bottom:16px; min-height:38px; }
    .sb--collapsed .sb__brand { justify-content:center; padding:0; }
    .sb__logo { width:38px; height:38px; border-radius:50%; background:#fff; color:#b3123a;
      display:grid; place-items:center; font:700 15px var(--font-display); flex-shrink:0; overflow:hidden; }
    .sb__logo--img { border-radius:10px; }
    .sb__logo--img img { width:100%; height:100%; object-fit:contain; }
    .sb__brand-name { color:#fff; font:700 calc(var(--sb-fs, 17px) + 2px) var(--font-display); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .sb__list, .sb__children { list-style:none; margin:0; padding:0; }
    .sb__item { display:flex; align-items:center; gap:12px; min-height:44px; padding:0 14px; margin:4px 0; width:100%;
      border:0; border-radius:999px; background:transparent; color:rgb(255 255 255 / 88%); cursor:pointer; text-align:left;
      text-decoration:none; font:600 var(--sb-fs, 17px) 'Outfit', var(--font-sans); transition:background .12s, color .12s, box-shadow .12s; }
    .sb--collapsed .sb__item { justify-content:center; padding:0; }
    .sb__item:hover { background:rgb(255 255 255 / 14%); color:#fff; }
    .sb__item:focus-visible { outline:2px solid #4338ca; outline-offset:2px; }
    .sb__item--active, .sb__item--active:hover { background:#fff; color:var(--brand-700); box-shadow:0 4px 14px rgb(0 0 0 / 18%); }
    /* A group that only contains the active page stays subtle, so one pill is ever solid white. */
    .sb__item--group.sb__item--active { background:rgb(255 255 255 / 14%); color:#fff; box-shadow:none; }
    .sb__icon { font-size:calc(var(--sb-fs, 17px) + 5px); width:calc(var(--sb-fs, 17px) + 5px); height:calc(var(--sb-fs, 17px) + 5px); flex-shrink:0; }
    .sb__label { flex:1; min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .sb__chevron { font-size:20px; width:20px; height:20px; flex:0 0 auto; transition:transform .15s ease; }
    .sb__chevron--open { transform:rotate(180deg); }
    .sb__children { margin:0 0 4px; padding-left:14px; }
    .sb__item--child { min-height:40px; padding:0 14px; font-size:calc(var(--sb-fs, 17px) - 1px); }
    .sb__dot { width:6px; height:6px; border-radius:50%; background:currentColor; opacity:.6; flex-shrink:0; }
    .sb__badge { background:#fff; color:var(--brand-700); font:700 12px var(--font-sans); padding:1px 8px; border-radius:999px; }
    .sb__item--active .sb__badge { background:var(--brand-700); color:#fff; }

    .sb__size { margin-top:auto; padding-top:12px; display:flex; align-items:center; justify-content:center; gap:10px; }
    .sb__size-btn { min-width:44px; height:34px; border-radius:999px; border:1px solid rgb(255 255 255 / 45%); background:transparent;
      color:#fff; font:700 14px 'Outfit', var(--font-sans); cursor:pointer; }
    .sb__size-btn--lg { font-size:17px; }
    .sb__size-btn:hover:not(:disabled) { background:rgb(255 255 255 / 14%); }
    .sb__size-btn:disabled { opacity:.4; cursor:default; }
    .sb__size-btn:focus-visible { outline:2px solid #4338ca; outline-offset:2px; }
    .sb__size-val { font:600 13px var(--font-sans); color:rgb(255 255 255 / 80%); min-width:38px; text-align:center; }

    /* Phone: a drawer 12px from top/left/bottom, over a scrim in the shell. Labels always show. */
    @media (max-width: 900px) {
      .sb { position:fixed; top:12px; left:12px; bottom:12px; margin:0; z-index:60; height:auto;
        width:min(18rem, 84%); transform:translateX(calc(-100% - 24px)); visibility:hidden;
        transition:transform .22s ease, visibility 0s linear .22s; }
      .sb--open { transform:translateX(0); visibility:visible; transition:transform .22s ease; }
    }
    @media (prefers-reduced-motion: reduce) { .sb, .sb--open, .sb__item, .sb__chevron { transition:none; } }
  `]
})
export class Sidebar {
  protected readonly nav = inject(NavigationService);
  /** User-chosen menu text size (px), persisted per browser. */
  protected readonly SIZES = [15, 16, 17, 18, 20, 22];
  protected readonly fontIdx = signal(this.loadIdx());
  protected readonly fontSize = computed(() => this.SIZES[this.fontIdx()]);
  private loadIdx(): number {
    try {
      const i = this.SIZES.indexOf(Number(localStorage.getItem('sidebar.fontSize')));
      if (i >= 0) return i;
    } catch { /* storage unavailable */ }
    return 2; // 17px
  }
  protected bump(d: number): void {
    const i = Math.min(this.SIZES.length - 1, Math.max(0, this.fontIdx() + d));
    this.fontIdx.set(i);
    try { localStorage.setItem('sidebar.fontSize', String(this.SIZES[i])); } catch { /* ignore */ }
  }
  private readonly auth = inject(AuthService);
  protected readonly companyName = computed(() => this.auth.companyName() || environment.appName);
  protected readonly logoUrl = computed(() => this.auth.companyLogo());
  protected readonly logoBroken = signal(false);
  protected readonly initials = computed(() =>
    this.companyName().split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase());
  readonly mobileOpen = input(false);
  /** Tablet/mobile viewport: the sidebar is a drawer, so the desktop icon-rail setting must not apply. */
  readonly drawerMode = input(false);
  /** Icon-only rail: a desktop-only state, never inside the mobile drawer. */
  protected readonly rail = computed(() => this.nav.collapsed() && !this.drawerMode());
  /** Emitted on a navigation click so the shell can close the mobile drawer. */
  readonly navigate = output<void>();
}
