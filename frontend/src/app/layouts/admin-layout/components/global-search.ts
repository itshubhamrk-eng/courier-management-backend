import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { NavigationService } from '@core/navigation/navigation.service';
import { NavNode } from '@core/navigation/navigation.model';

interface SearchResult { label: string; route: string; icon: string; section: string; }

/**
 * Header quick-search. It searches the app's own navigation — already permission-filtered by
 * NavigationService — so it is real, useful and needs no backend: type a page name, Enter or
 * click to jump there. When a backend search endpoint exists, results can be merged in;
 * nothing here is fabricated.
 */
@Component({
  selector: 'app-global-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <div class="gs" [class.gs--open]="open()">
      <mat-icon class="gs__icon">search</mat-icon>
      <input class="gs__input" type="text" placeholder="Search pages…" [value]="query()"
             (input)="query.set($any($event.target).value)" (focus)="open.set(true)"
             (keydown.enter)="enter()" (keydown.escape)="close()" (blur)="onBlur()" aria-label="Search" />
      @if (query()) { <button class="gs__clear" (mousedown)="clear($event)" aria-label="Clear"><mat-icon>close</mat-icon></button> }

      @if (open() && query()) {
        <div class="gs__panel app-card">
          @if (results().length) {
            @for (r of results(); track r.route) {
              <button class="gs__result" (mousedown)="pick($event, r.route)">
                <mat-icon>{{ r.icon }}</mat-icon>
                <span class="gs__result-label">{{ r.label }}</span>
                <span class="gs__result-section">{{ r.section }}</span>
              </button>
            }
          } @else {
            <p class="gs__none">No pages match “{{ query() }}”.</p>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .gs { position:relative; display:flex; align-items:center; gap:8px; width:280px; height:40px; padding:0 10px;
      border:1px solid var(--surface-border); border-radius:10px; background:var(--surface-muted); transition:.15s; }
    .gs--open { border-color:var(--brand-400); background:var(--surface); box-shadow:0 0 0 3px var(--brand-50); }
    .gs__icon { color:var(--content-muted); font-size:20px; width:20px; height:20px; }
    .gs__input { flex:1; border:0; outline:0; background:transparent; font:500 14px var(--font-sans); color:var(--content-fg); }
    .gs__clear { display:grid; place-items:center; border:0; background:transparent; color:var(--content-muted); cursor:pointer; }
    .gs__clear mat-icon { font-size:18px; width:18px; height:18px; }
    .gs__panel { position:absolute; top:48px; left:0; right:0; padding:6px; z-index:40; max-height:360px; overflow-y:auto; }
    .gs__result { display:flex; align-items:center; gap:10px; width:100%; padding:9px 10px; border:0; border-radius:8px;
      background:transparent; cursor:pointer; text-align:left; }
    .gs__result:hover { background:var(--surface-muted); }
    .gs__result mat-icon { color:var(--content-muted); font-size:19px; width:19px; height:19px; }
    .gs__result-label { flex:1; font:500 14px var(--font-sans); color:var(--content-fg); }
    .gs__result-section { font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.04em; color:var(--content-muted); }
    .gs__none { margin:0; padding:12px 10px; font:400 13px var(--font-sans); color:var(--content-muted); }
    @media (max-width: 900px) { .gs { width:200px; } }
    @media (max-width: 720px) { .gs { display:none; } }
  `]
})
export class GlobalSearch {
  private readonly nav = inject(NavigationService);
  private readonly router = inject(Router);

  readonly query = signal('');
  readonly open = signal(false);

  /** All navigable leaf pages the user may see, flattened from the permission-filtered menu. */
  private readonly pages = computed<SearchResult[]>(() => {
    const out: SearchResult[] = [];
    const walk = (nodes: NavNode[], section: string) => {
      for (const n of nodes) {
        if (n.children?.length) walk(n.children, n.title);
        else if (n.route) out.push({ label: n.title, route: n.route, icon: n.icon ?? 'chevron_right', section });
      }
    };
    walk(this.nav.menu(), 'General');
    return out;
  });

  readonly results = computed<SearchResult[]>(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) return [];
    return this.pages().filter((p) => p.label.toLowerCase().includes(q)).slice(0, 8);
  });

  enter(): void {
    const first = this.results()[0];
    if (first) this.goto(first.route);
  }
  pick(e: Event, route: string): void { e.preventDefault(); this.goto(route); }
  clear(e: Event): void { e.preventDefault(); this.query.set(''); }
  close(): void { this.open.set(false); }
  onBlur(): void { setTimeout(() => this.open.set(false), 120); }

  private goto(route: string): void {
    this.query.set('');
    this.open.set(false);
    this.router.navigateByUrl(route);
  }
}
