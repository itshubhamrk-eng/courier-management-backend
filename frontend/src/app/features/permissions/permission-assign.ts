import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { RoleService } from '@features/roles/role.service';
import { CompanyRole } from '@core/models/role.model';
import {
  Permission, PermissionGroup, RolePermissionResult, groupByModule, prettyToken
} from '@core/models/permission.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiSearch } from '@shared/components/ui-search/ui-search';
import { PermissionTree } from './components/permission-tree';
import { PermissionMatrix } from './components/permission-matrix';
import { PermissionToggle } from './components/module-permission-card';
import { PermissionService } from './permission.service';

type ViewMode = 'tree' | 'matrix';

/**
 * Role → Permission assignment. Pick a role, then grant permissions as a tree or a matrix.
 * Selection is held here as one `Set` of codes; save submits the whole set in one bulk
 * call with `replaceExisting=true` (what "Save" means), and the backend's granted / revoked
 * / skipped / **rejected** result is surfaced — a plan-gated right silently useless is the
 * exact thing the four-list response exists to prevent. No mock data.
 */
@Component({
  selector: 'app-permission-assign',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule, MatIconModule, UiCard, UiLoader, UiButton, UiSelect, UiSearch,
    PermissionTree, PermissionMatrix
  ],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">Assign Permissions</h1><p class="text-caption">Grant a role exactly what its holders may do.</p></div>
      </header>

      <app-card>
        <div class="asg__pick">
          <div class="asg__pick-f">
            <app-select [control]="roleCtrl" label="Role" [options]="roleOptions()" placeholder="Select a role to configure…" />
          </div>
          @if (currentRole(); as r) {
            <div class="asg__pick-meta">
              <span class="tag mono">{{ r.roleCode }}</span>
              @if (r.isSystemRole) { <span class="tag">System</span> }
              @if (r.isDefault) { <span class="tag tag--brand">Default</span> }
            </div>
          }
        </div>
      </app-card>

      @if (loadingCatalogue()) {
        <app-loader [minHeight]="280" caption="Loading catalogue…" />
      } @else if (!currentRole()) {
        <app-card><p class="empty"><mat-icon>rule</mat-icon>Choose a role above to begin assigning permissions.</p></app-card>
      } @else {
        <app-card [title]="'Permissions for ' + currentRole()!.roleName"
                  [subtitle]="selected().size + ' of ' + grantable().length + ' granted' + (dirty() ? ' · unsaved changes' : '')">
          <div class="asg__toolbar">
            <app-search placeholder="Filter permissions…" (changed)="onSearch($event)" />
            <div class="asg__spacer"></div>
            <div class="asg__seg" role="tablist" aria-label="View">
              <button type="button" [class.on]="view()==='tree'" (click)="view.set('tree')" role="tab" [attr.aria-selected]="view()==='tree'">
                <mat-icon>account_tree</mat-icon>Tree</button>
              <button type="button" [class.on]="view()==='matrix'" (click)="view.set('matrix')" role="tab" [attr.aria-selected]="view()==='matrix'">
                <mat-icon>grid_on</mat-icon>Matrix</button>
            </div>
          </div>

          @if (loadingGrants()) {
            <app-loader [minHeight]="200" caption="Loading role permissions…" />
          } @else if (view() === 'tree') {
            <app-permission-tree [groups]="filteredGroups()" [selectable]="true" [selected]="selected()" (toggle)="onToggle($event)" />
          } @else {
            <div class="asg__matrix-bar">
              <span class="asg__count">{{ selected().size }} selected</span>
              <app-button variant="text" icon="done_all" (pressed)="selectAll()">Select all</app-button>
              <app-button variant="text" icon="remove_done" (pressed)="deselectAll()">Deselect all</app-button>
            </div>
            <app-permission-matrix [groups]="filteredGroups()" [selectable]="true" [selected]="selected()" (toggle)="onToggle($event)" />
          }
        </app-card>

        <footer class="asg__foot">
          <div class="asg__foot-info">
            @if (dirty()) { <mat-icon class="warn">edit</mat-icon><span>Unsaved changes — {{ delta() }}</span> }
            @else { <mat-icon>check_circle</mat-icon><span>All changes saved.</span> }
          </div>
          <div class="asg__foot-actions">
            <app-button variant="stroked" [disabled]="!dirty() || saving()" (pressed)="reset()">Reset</app-button>
            <app-button icon="save" [loading]="saving()" [disabled]="!dirty()" (pressed)="save()">Save permissions</app-button>
          </div>
        </footer>
      }
    </div>
  `,
  styles: [`
    .asg__pick { display:flex; align-items:flex-end; gap:16px; flex-wrap:wrap; }
    .asg__pick-f { flex:1; min-width:280px; max-width:460px; }
    .asg__pick-meta { display:flex; gap:6px; padding-bottom:8px; }
    .asg__toolbar { display:flex; align-items:center; gap:12px; margin-bottom:16px; flex-wrap:wrap; }
    .asg__spacer { flex:1; }
    .asg__seg { display:inline-flex; border:1px solid var(--surface-border); border-radius:9px; overflow:hidden; }
    .asg__seg button { display:inline-flex; align-items:center; gap:6px; border:0; background:var(--surface); cursor:pointer;
      padding:8px 14px; font:600 13px var(--font-sans); color:var(--content-muted); }
    .asg__seg button.on { background:var(--brand-50); color:var(--brand-700); }
    .asg__seg button mat-icon { font-size:17px; width:17px; height:17px; }
    .asg__matrix-bar { display:flex; align-items:center; gap:4px; margin-bottom:12px; }
    .asg__count { font:600 12px var(--font-sans); color:var(--content-muted); margin-right:auto; }
    .asg__foot { position:sticky; bottom:0; display:flex; align-items:center; justify-content:space-between; gap:16px;
      margin-top:16px; padding:14px 20px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-lg, 12px); box-shadow:var(--shadow-md, 0 -2px 12px rgba(0,0,0,.06)); flex-wrap:wrap; }
    .asg__foot-info { display:flex; align-items:center; gap:8px; font:500 13px var(--font-sans); color:var(--content-muted); }
    .asg__foot-info mat-icon { font-size:18px; width:18px; height:18px; }
    .asg__foot-info .warn { color:var(--warning); }
    .asg__foot-actions { display:flex; gap:10px; }
    .tag { display:inline-block; background:var(--surface-muted); border:1px solid var(--surface-border); color:var(--content-muted);
      font:600 11px var(--font-sans); padding:2px 8px; border-radius:6px; }
    .tag--brand { background:var(--brand-50); color:var(--brand-700); border-color:var(--brand-100); }
    .mono { font-family:var(--font-mono, ui-monospace); }
    .empty { display:flex; align-items:center; justify-content:center; gap:8px; font:400 14px var(--font-sans);
      color:var(--content-muted); padding:32px; }
  `]
})
export class PermissionAssign implements OnInit {
  private readonly service = inject(PermissionService);
  private readonly roleService = inject(RoleService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);

  readonly roleCtrl = new FormControl<string | null>(null);
  readonly roles = signal<CompanyRole[]>([]);
  readonly grantable = signal<Permission[]>([]);
  readonly selected = signal<Set<string>>(new Set());
  private original = signal<Set<string>>(new Set());

  readonly loadingCatalogue = signal(true);
  readonly loadingGrants = signal(false);
  readonly saving = signal(false);
  readonly view = signal<ViewMode>('tree');
  private readonly term = signal('');

  readonly roleOptions = computed<SelectOption[]>(() =>
    this.roles().map((r) => ({ value: r.id, label: `${r.roleName} (${r.roleCode})` })));

  readonly currentRole = computed(() => this.roles().find((r) => r.id === this.roleCtrl.value) ?? null);

  private readonly allGroups = computed<PermissionGroup[]>(() => groupByModule(this.grantable()));

  readonly filteredGroups = computed<PermissionGroup[]>(() => {
    const t = this.term().trim().toLowerCase();
    if (!t) return this.allGroups();
    return this.allGroups()
      .map((g) => ({
        module: g.module,
        permissions: g.permissions.filter((p) =>
          p.permissionCode.toLowerCase().includes(t) ||
          p.permissionName.toLowerCase().includes(t) ||
          g.module.toLowerCase().includes(t))
      }))
      .filter((g) => g.permissions.length);
  });

  readonly dirty = computed(() => !sameSet(this.selected(), this.original()));

  readonly delta = computed(() => {
    const [sel, orig] = [this.selected(), this.original()];
    const added = [...sel].filter((c) => !orig.has(c)).length;
    const removed = [...orig].filter((c) => !sel.has(c)).length;
    return `${added} to add, ${removed} to remove`;
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Access Control' }, { label: 'Permissions', route: '/permissions' }, { label: 'Assign' }]);

    // Roles for the picker + the full grantable catalogue, in parallel.
    this.roleService.assignable().subscribe({
      next: (rs) => {
        this.roles.set(rs);
        const pre = this.route.snapshot.queryParamMap.get('roleId');
        if (pre && rs.some((r) => r.id === pre)) { this.roleCtrl.setValue(pre); this.loadGrants(pre); }
      }
    });
    this.service.grantable().subscribe({
      next: (ps) => { this.grantable.set(ps); this.loadingCatalogue.set(false); },
      error: () => this.loadingCatalogue.set(false)
    });

    this.roleCtrl.valueChanges.subscribe((id) => { if (id) this.loadGrants(id); else this.clearSelection(); });
  }

  private loadGrants(roleId: string): void {
    this.loadingGrants.set(true);
    this.service.rolePermissions(roleId).subscribe({
      next: (perms) => {
        const codes = new Set((perms as Permission[]).map((p) => p.permissionCode));
        this.selected.set(new Set(codes));
        this.original.set(new Set(codes));
        this.loadingGrants.set(false);
      },
      error: () => { this.clearSelection(); this.loadingGrants.set(false); this.notify.error('Could not load the role permissions.'); }
    });
  }

  private clearSelection(): void { this.selected.set(new Set()); this.original.set(new Set()); }

  onSearch(t: string): void { this.term.set(t ?? ''); }

  onToggle({ codes, checked }: PermissionToggle): void {
    const next = new Set(this.selected());
    for (const c of codes) checked ? next.add(c) : next.delete(c);
    this.selected.set(next);
  }

  selectAll(): void {
    const next = new Set(this.selected());
    for (const g of this.filteredGroups())
      for (const p of g.permissions) if (p.status === 'ACTIVE') next.add(p.permissionCode);
    this.selected.set(next);
  }

  deselectAll(): void {
    const next = new Set(this.selected());
    for (const g of this.filteredGroups()) for (const p of g.permissions) next.delete(p.permissionCode);
    this.selected.set(next);
  }

  reset(): void { this.selected.set(new Set(this.original())); this.notify.info('Reverted to the saved set.'); }

  save(): void {
    const role = this.currentRole();
    if (!role || this.saving()) return;
    const codes = [...this.selected()];
    if (!codes.length) { this.notify.error('Select at least one permission, or the request is rejected.'); return; }

    this.saving.set(true);
    this.service.assign(role.id, { permissionCodes: codes, replaceExisting: true }).subscribe({
      next: (res) => { this.saving.set(false); this.applyResult(res); },
      error: (e) => { this.saving.set(false); this.notify.error(e?.error?.message ?? 'Could not save permissions.'); }
    });
  }

  /** Trust the server's effective set — a rejected code never actually stuck. */
  private applyResult(res: RolePermissionResult): void {
    const effective = new Set(res.effectivePermissions);
    this.selected.set(new Set(effective));
    this.original.set(new Set(effective));

    const parts: string[] = [];
    if (res.granted.length) parts.push(`${res.granted.length} granted`);
    if (res.revoked.length) parts.push(`${res.revoked.length} revoked`);
    if (res.skipped.length) parts.push(`${res.skipped.length} unchanged`);
    this.notify.success(`Saved — ${parts.join(', ') || 'no changes'}.`);

    if (res.rejected.length) {
      this.notify.error(`${res.rejected.length} rejected (inactive or outside your plan): ${res.rejected.slice(0, 6).map(prettyToken).join(', ')}${res.rejected.length > 6 ? '…' : ''}`);
    }
  }
}

/** Order-independent set equality on codes. */
function sameSet(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}
