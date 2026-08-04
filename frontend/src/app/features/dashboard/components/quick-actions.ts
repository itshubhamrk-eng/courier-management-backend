import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { QuickActionDef } from '../dashboard.roles';

/** Grid of primary operator actions. Emits the picked action; the page routes or toasts. */
@Component({
  selector: 'app-quick-actions',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, UiCard],
  template: `
    <app-card title="Quick Actions" subtitle="Jump straight to a task">
      <div class="qa">
        @for (a of actions(); track a.id) {
          <button type="button" class="qa__btn" (click)="pick.emit(a)">
            <span class="qa__ic"><mat-icon>{{ a.icon }}</mat-icon></span>
            <span class="qa__label">{{ a.label }}</span>
          </button>
        }
      </div>
    </app-card>
  `,
  styles: [`
    .qa { display:grid; grid-template-columns:repeat(3, 1fr); gap:12px; }
    .qa__btn { display:flex; flex-direction:column; align-items:center; gap:8px; padding:16px 8px;
      border:1px solid var(--surface-border); border-radius:12px; background:var(--surface);
      cursor:pointer; transition:.15s ease; color:var(--content-fg); }
    .qa__btn:hover { border-color:var(--brand-300); background:var(--brand-50); transform:translateY(-1px); }
    .qa__ic { display:grid; place-items:center; width:40px; height:40px; border-radius:10px;
      background:var(--brand-50); color:var(--brand-600); }
    .qa__btn:hover .qa__ic { background:var(--brand-100); }
    .qa__label { font:600 12px var(--font-sans); text-align:center; }
    @media (max-width:520px){ .qa { grid-template-columns:repeat(2,1fr); } }
  `]
})
export class QuickActions {
  readonly actions = input<QuickActionDef[]>([]);
  readonly pick = output<QuickActionDef>();
}
