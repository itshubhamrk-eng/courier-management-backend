import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { NavigationService } from '@core/navigation/navigation.service';

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
    <nav class="sb app-scroll" [class.sb--collapsed]="nav.collapsed()" [class.sb--open]="mobileOpen()"
         aria-label="Primary">
      <div class="sb__brand">
        <div class="sb__logo"><span>CS</span></div>
        @if (!nav.collapsed()) { <span class="sb__brand-name">Courier SaaS</span> }
      </div>

      <ul class="sb__list" role="list">
        @for (item of nav.menu(); track item.id) {
          <li>
            @if (item.children?.length) {
              <!-- Expandable section/group -->
              <button type="button" class="sb__item sb__item--group"
                      [class.sb__item--active]="nav.isGroupActive(item)"
                      [attr.aria-expanded]="!nav.collapsed() && nav.isExpanded(item)"
                      [attr.aria-controls]="'grp-' + item.id"
                      [attr.data-tour]="item.id"
                      [title]="nav.collapsed() ? item.title : ''"
                      (click)="nav.toggle(item)">
                <mat-icon class="sb__icon">{{ item.icon }}</mat-icon>
                @if (!nav.collapsed()) {
                  <span class="sb__label">{{ item.title }}</span>
                  <mat-icon class="sb__chevron" [class.sb__chevron--open]="nav.isExpanded(item)">expand_more</mat-icon>
                }
              </button>
              @if (!nav.collapsed() && nav.isExpanded(item)) {
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
                 [title]="nav.collapsed() ? item.title : ''" (click)="navigate.emit()">
                <mat-icon class="sb__icon">{{ item.icon }}</mat-icon>
                @if (!nav.collapsed()) { <span class="sb__label">{{ item.title }}</span> }
                @if (item.badge && !nav.collapsed()) { <span class="sb__badge">{{ item.badge }}</span> }
              </a>
            }
          </li>
        }
      </ul>
    </nav>
  `,
  styles: [`
    .sb { width:var(--sidebar-w); background:var(--sidebar-bg); height:100vh; overflow-y:auto;
      display:flex; flex-direction:column; padding:12px; border-right:1px solid var(--sidebar-border);
      transition:width .18s ease; flex-shrink:0; }
    .sb--collapsed { width:var(--sidebar-w-collapsed); }
    .sb__brand { display:flex; align-items:center; gap:10px; padding:10px 8px 18px; }
    .sb__logo { width:34px; height:34px; border-radius:8px; background:var(--brand-600);
      display:grid; place-items:center; font:800 13px var(--font-display); color:#fff; flex-shrink:0; }
    .sb__brand-name { color:var(--sidebar-fg-strong); font:700 15px var(--font-display); }
    .sb__list, .sb__children { list-style:none; margin:0; padding:0; }
    .sb__item { display:flex; align-items:center; gap:12px; padding:9px 10px; border-radius:8px;
      color:var(--sidebar-fg); text-decoration:none; font:500 14px var(--font-sans); transition:background .12s, color .12s; margin:1px 0;
      width:100%; border:0; background:transparent; cursor:pointer; text-align:left; }
    .sb__item:hover { background:var(--sidebar-hover); color:var(--sidebar-fg-strong); }
    .sb__item:focus-visible { outline:2px solid var(--brand-400); outline-offset:-2px; }
    .sb__item--active { background:var(--sidebar-active); color:#fff; box-shadow:inset 3px 0 0 0 var(--brand-500); }
    .sb__item--group.sb__item--active { background:var(--sidebar-hover); color:var(--sidebar-fg-strong); }
    .sb__icon { font-size:20px; width:20px; height:20px; flex-shrink:0; }
    .sb__label { flex:1; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .sb__chevron { font-size:18px; width:18px; height:18px; flex:0 0 auto; transition:transform .15s ease; }
    .sb__chevron--open { transform:rotate(180deg); }
    .sb__children { margin:1px 0 4px; padding-left:6px; }
    .sb__item--child { padding-left:14px; }
    .sb__dot { width:6px; height:6px; border-radius:50%; background:currentColor; opacity:.5; flex-shrink:0; margin:0 7px; }
    .sb__item--child.sb__item--active .sb__dot { opacity:1; }
    .sb__badge { background:var(--brand-500); color:#fff; font:700 11px var(--font-sans);
      padding:1px 7px; border-radius:999px; }

    /* Mobile: off-canvas drawer over a shell-rendered backdrop. Collapse is a desktop concept only. */
    @media (max-width: 1024px) {
      .sb { position:fixed; inset:0 auto 0 0; z-index:60; width:var(--sidebar-w);
        transform:translateX(-100%); box-shadow:0 0 24px rgba(0,0,0,.25); }
      .sb--collapsed { width:var(--sidebar-w); }
      .sb--open { transform:translateX(0); }
    }
    @media (prefers-reduced-motion: reduce) { .sb { transition:none; } .sb__item { transition:background .12s, color .12s; } }
  `]
})
export class Sidebar {
  protected readonly nav = inject(NavigationService);
  readonly mobileOpen = input(false);
  /** Emitted on a navigation click so the shell can close the mobile drawer. */
  readonly navigate = output<void>();
}
