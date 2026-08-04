import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Right-side slide-over for create/edit forms. Controlled by an `open` input. */
@Component({
  selector: 'app-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    @if (open()) {
      <div class="dw__scrim" (click)="closed.emit()"></div>
      <aside class="dw" [style.width.px]="width()">
        <header class="dw__head">
          <div><h2 class="text-h2">{{ title() }}</h2>
            @if (subtitle()) { <p class="text-caption">{{ subtitle() }}</p> }</div>
          <button class="dw__x" (click)="closed.emit()"><mat-icon>close</mat-icon></button>
        </header>
        <div class="dw__body app-scroll"><ng-content /></div>
        <footer class="dw__foot"><ng-content select="[drawer-footer]" /></footer>
      </aside>
    }
  `,
  styles: [`
    .dw__scrim { position:fixed; inset:0; background:rgba(15,23,42,.45); z-index:60; animation:fade .15s; }
    .dw { position:fixed; top:0; right:0; bottom:0; max-width:92vw; background:var(--surface);
      z-index:61; display:flex; flex-direction:column; box-shadow:var(--shadow-pop); animation:slide .2s ease; }
    .dw__head { display:flex; align-items:flex-start; justify-content:space-between; padding:20px 24px; border-bottom:1px solid var(--surface-border); }
    .dw__x { border:0; background:transparent; cursor:pointer; color:var(--content-muted); display:flex; }
    .dw__body { flex:1; overflow:auto; padding:24px; }
    .dw__foot { padding:16px 24px; border-top:1px solid var(--surface-border); display:flex; justify-content:flex-end; gap:8px; }
    @keyframes slide { from{transform:translateX(24px);opacity:.6} to{transform:none;opacity:1} }
    @keyframes fade { from{opacity:0} to{opacity:1} }
  `]
})
export class UiDrawer {
  readonly open = input(false);
  readonly title = input('');
  readonly subtitle = input('');
  readonly width = input(460);
  readonly closed = output<void>();
}
