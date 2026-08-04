import { ChangeDetectionStrategy, Component, DestroyRef, afterNextRender, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavigationService } from '@core/navigation/navigation.service';
import { TourService } from '@core/services/tour.service';
import { Sidebar } from './components/sidebar';
import { Header } from './components/header';
import { Footer } from './components/footer';

/**
 * The authenticated shell. One toggle drives two behaviours by viewport:
 *  - desktop (>1024px): the dark rail collapses to icons and back;
 *  - tablet/mobile (≤1024px): the rail becomes an off-canvas drawer over a backdrop.
 * `matchMedia` tracks the breakpoint; leaving mobile closes the drawer so the desktop
 * layout never inherits a stray open state.
 */
@Component({
  selector: 'app-admin-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Sidebar, Header, Footer],
  template: `
    <div class="shell">
      <app-sidebar [mobileOpen]="drawerOpen()" (navigate)="closeDrawer()" />
      @if (drawerOpen()) { <div class="shell__backdrop" (click)="closeDrawer()"></div> }
      <div class="shell__main">
        <app-header (toggleSidebar)="toggle()" />
        <main class="shell__content app-scroll"><router-outlet /></main>
        <app-footer />
      </div>
    </div>
  `,
  styles: [`
    .shell { display:flex; height:100vh; overflow:hidden; }
    .shell__main { flex:1; display:flex; flex-direction:column; min-width:0; }
    .shell__content { flex:1; overflow:auto; padding:28px 32px; background:var(--surface-muted); }
    .shell__backdrop { position:fixed; inset:0; z-index:50; background:rgba(15,23,42,.5); backdrop-filter:blur(1px); }
    @media (max-width: 1024px) { .shell__content { padding:24px; } }
    @media (max-width: 640px) { .shell__content { padding:16px; } }
  `]
})
export class AdminLayout {
  private readonly destroyRef = inject(DestroyRef);
  private readonly nav = inject(NavigationService);
  private readonly tour = inject(TourService);

  /** Mobile/tablet: drawer visible. Desktop collapse lives in NavigationService (persisted). */
  readonly drawerOpen = signal(false);
  private readonly isMobile = signal(false);

  constructor() {
    // Sidebar/header DOM must be painted before driver.js can measure tour-step anchors.
    afterNextRender(() => this.tour.maybeAutoStart());

    const mq = window.matchMedia('(max-width: 1024px)');
    const apply = (matches: boolean) => {
      this.isMobile.set(matches);
      if (!matches) this.drawerOpen.set(false); // leaving mobile: never keep the drawer open
    };
    apply(mq.matches);
    const onChange = (e: MediaQueryListEvent) => apply(e.matches);
    mq.addEventListener('change', onChange);
    this.destroyRef.onDestroy(() => mq.removeEventListener('change', onChange));
  }

  toggle(): void {
    if (this.isMobile()) this.drawerOpen.update((v) => !v);
    else this.nav.toggleCollapsed();
  }
  closeDrawer(): void { this.drawerOpen.set(false); }
}
