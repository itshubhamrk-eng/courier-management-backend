import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { NAVIGATION } from '@core/navigation/navigation.config';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { CompanyService } from './company.service';

/** Menus a company admin can never lose: their way in and their own settings. */
const ALWAYS_ON = new Set(['dashboard', 'settings']);

interface MenuGroup { id: string; title: string; icon?: string; children: { id: string; title: string }[]; }

/**
 * Super-admin control over which sidebar menus a company may see. A ticked box is visible;
 * unticking hides it for everyone in that company, including its COMPANY_ADMIN, whose own
 * role/user permission screens can only grant within what is left.
 */
@Component({
  selector: 'app-company-menu-access',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiCard, UiButton, UiLoader],
  template: `
    <div class="page">
      <header class="page__head">
        <div>
          <h1 class="text-h1">Menu Access</h1>
          <p class="text-caption">{{ companyName() }} — untick a menu to hide it from every user of this company.</p>
        </div>
        <div class="actions">
          <app-button variant="stroked" icon="arrow_back" (pressed)="back()">Back</app-button>
          <app-button icon="save" [disabled]="saving() || loading()" (pressed)="save()">Save</app-button>
        </div>
      </header>

      @if (loading()) {
        <app-loader [minHeight]="240" caption="Loading menus…" />
      } @else {
        <div class="grid">
          @for (g of groups; track g.id) {
            <app-card>
              <label class="row row--group">
                <input type="checkbox" [checked]="visible(g.id)" [disabled]="always(g.id)"
                       (change)="toggle(g.id)" />
                <span>{{ g.title }}</span>
                @if (always(g.id)) { <small>always on</small> }
              </label>
              @for (c of g.children; track c.id) {
                <label class="row row--child">
                  <input type="checkbox" [checked]="visible(c.id)"
                         [disabled]="!visible(g.id)" (change)="toggle(c.id)" />
                  <span>{{ c.title }}</span>
                </label>
              }
            </app-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .actions { display:flex; gap:8px; }
    .grid { display:grid; gap:16px; margin-top:16px; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); }
    .row { display:flex; align-items:center; gap:10px; padding:6px 0; font:500 16px var(--font-sans); cursor:pointer; }
    .row--group { font-weight:700; font-size:17px; border-bottom:1px solid var(--surface-border); margin-bottom:4px; }
    .row--child { padding-left:22px; font-weight:400; color:var(--content-fg); }
    .row small { margin-left:auto; color:var(--content-muted); font-weight:400; }
    input[type=checkbox] { width:16px; height:16px; accent-color:var(--brand-600); }
  `]
})
export class CompanyMenuAccessPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly companies = inject(CompanyService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);

  /** Every authored sidebar group and its leaves — the same ids the sidebar filters on. */
  readonly groups: MenuGroup[] = [...NAVIGATION]
    .filter((n) => n.id !== 'platform') // super-admin's own console, never a company's
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
    .map((n) => ({
      id: n.id, title: n.title, icon: n.icon,
      children: (n.children ?? []).map((c) => ({ id: c.id, title: c.title }))
    }));

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly companyName = signal('');
  private readonly locked = signal<ReadonlySet<string>>(new Set());
  private companyId = '';
  readonly lockedCount = computed(() => this.locked().size);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.breadcrumb.set([{ label: 'Companies', route: '/companies' }, { label: 'Menu Access' }]);
    this.companies.getProfile(id).subscribe({
      next: (c) => {
        this.companyId = c.companyId;
        this.companyName.set(c.companyName);
        this.breadcrumb.set([{ label: 'Companies', route: '/companies' },
          { label: c.companyName, route: `/companies/${id}` }, { label: 'Menu Access' }]);
        this.api.get<{ lockedMenus: string[] }>(API.companyMenuLocks(this.companyId)).subscribe({
          next: (r) => { this.locked.set(new Set(r.lockedMenus ?? [])); this.loading.set(false); },
          error: () => { this.notify.error('Could not load menu access.'); this.loading.set(false); }
        });
      },
      error: () => { this.notify.error('Company not found.'); this.loading.set(false); }
    });
  }

  always(id: string): boolean { return ALWAYS_ON.has(id); }
  visible(id: string): boolean { return !this.locked().has(id); }

  toggle(id: string): void {
    const next = new Set(this.locked());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.locked.set(next);
  }

  save(): void {
    this.saving.set(true);
    this.api.put<{ lockedMenus: string[] }>(API.companyMenuLocks(this.companyId),
      { lockedMenus: [...this.locked()] }).subscribe({
      next: (r) => {
        this.locked.set(new Set(r.lockedMenus));
        this.saving.set(false);
        this.notify.success('Menu access saved.');
      },
      error: (e) => { this.saving.set(false); this.notify.error(e?.error?.message ?? 'Could not save menu access.'); }
    });
  }

  back(): void { this.router.navigate(['/companies', this.route.snapshot.paramMap.get('id')]); }
}
