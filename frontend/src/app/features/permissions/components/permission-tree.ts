import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { PermissionGroup } from '@core/models/permission.model';
import { ModulePermissionCard, PermissionToggle } from './module-permission-card';

/**
 * Module → permission tree. A stack of expandable {@link ModulePermissionCard}s plus a
 * toolbar: expand/collapse all and (when selectable) select/deselect all. Expand state is
 * view-only and lives here; the selected `Set` is owned by the parent and only reflected.
 */
@Component({
  selector: 'app-permission-tree',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiButton, ModulePermissionCard],
  template: `
    <div class="tree">
      <div class="tree__bar">
        <div class="tree__bar-l">
          <app-button variant="text" icon="unfold_more" (pressed)="expandAll()">Expand all</app-button>
          <app-button variant="text" icon="unfold_less" (pressed)="collapseAll()">Collapse all</app-button>
        </div>
        @if (selectable()) {
          <div class="tree__bar-r">
            <span class="tree__count">{{ selected().size }} selected</span>
            <app-button variant="text" icon="done_all" (pressed)="selectAll()">Select all</app-button>
            <app-button variant="text" icon="remove_done" (pressed)="deselectAll()">Deselect all</app-button>
          </div>
        }
      </div>

      @if (groups().length) {
        <div class="tree__list">
          @for (g of groups(); track g.module) {
            <app-module-permission-card
              [group]="g" [selectable]="selectable()" [selected]="selected()" [blocked]="blocked()"
              [expanded]="isOpen(g.module)"
              (expandedChange)="setOpen(g.module, $event)"
              (toggle)="toggle.emit($event)" (toggleModule)="toggle.emit($event)" />
          }
        </div>
      } @else {
        <p class="tree__empty">No permissions match.</p>
      }
    </div>
  `,
  styles: [`
    .tree { display:flex; flex-direction:column; gap:12px; }
    .tree__bar { display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap; }
    .tree__bar-l, .tree__bar-r { display:flex; align-items:center; gap:4px; }
    .tree__count { font:600 12px var(--font-sans); color:var(--content-muted); margin-right:8px; }
    .tree__list { display:flex; flex-direction:column; gap:10px; }
    .tree__empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:32px; }
  `]
})
export class PermissionTree {
  readonly groups = input.required<PermissionGroup[]>();
  readonly selectable = input(false);
  readonly selected = input<ReadonlySet<string>>(new Set());
  readonly blocked = input<ReadonlySet<string>>(new Set());

  /** Bubbled up so the parent mutates the one owned selection Set. */
  readonly toggle = output<PermissionToggle>();

  private readonly open = signal<Set<string>>(new Set());

  /** Codes across every group that can actually be picked (grantable + not blocked). */
  private readonly allSelectable = computed(() =>
    this.groups().flatMap((g) =>
      g.permissions.filter((p) => p.status === 'ACTIVE' && !this.blocked().has(p.permissionCode))
        .map((p) => p.permissionCode)));

  isOpen(module: string): boolean { return this.open().has(module); }

  setOpen(module: string, on: boolean): void {
    const next = new Set(this.open());
    on ? next.add(module) : next.delete(module);
    this.open.set(next);
  }

  expandAll(): void { this.open.set(new Set(this.groups().map((g) => g.module))); }
  collapseAll(): void { this.open.set(new Set()); }

  selectAll(): void { this.toggle.emit({ codes: this.allSelectable(), checked: true }); }
  deselectAll(): void { this.toggle.emit({ codes: this.allSelectable(), checked: false }); }
}
