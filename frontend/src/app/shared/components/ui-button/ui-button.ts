import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

type Variant = 'primary' | 'stroked' | 'text' | 'danger';

/** App button — wraps Material with the enterprise palette, an icon slot and a loading state. */
@Component({
  selector: 'app-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatProgressSpinnerModule, MatIconModule],
  template: `
    <button
      [class]="'app-btn app-btn--' + variant()"
      [disabled]="disabled() || loading()"
      [type]="type()"
      (click)="pressed.emit()">
      @if (loading()) {
        <mat-progress-spinner diameter="16" mode="indeterminate" />
      } @else if (icon()) {
        <mat-icon class="app-btn__icon">{{ icon() }}</mat-icon>
      }
      <span><ng-content /></span>
    </button>
  `,
  styles: [`
    .app-btn { display:inline-flex; align-items:center; gap:8px; height:42px; padding:0 20px;
      border-radius:var(--r-field); font:600 14px/1 var(--font-sans); cursor:pointer;
      border:1px solid transparent; transition:background .15s ease, border-color .15s ease, box-shadow .15s ease; }
    .app-btn:disabled { opacity:.55; cursor:not-allowed; }
    .app-btn__icon { font-size:18px; width:18px; height:18px; }
    .app-btn--primary { background:var(--brand-600); color:#fff; }
    .app-btn--primary:hover:not(:disabled) { background:var(--brand-700); }
    .app-btn--stroked { background:var(--surface); color:var(--content-fg); border-color:var(--surface-border); }
    .app-btn--stroked:hover:not(:disabled) { background:var(--surface-muted); border-color:var(--content-muted); }
    .app-btn--text { background:transparent; color:var(--brand-600); padding:0 10px; box-shadow:none; }
    .app-btn--text:hover:not(:disabled) { background:var(--brand-50); }
    .app-btn--danger { background:var(--danger); color:#fff; }
    .app-btn--danger:hover:not(:disabled) { background:#b91c1c; }
  `]
})
export class UiButton {
  readonly variant = input<Variant>('primary');
  readonly icon = input<string | null>(null);
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly type = input<'button' | 'submit'>('button');
  readonly pressed = output<void>();
}
