import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NotificationService } from '@core/services/notification.service';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { ShipmentService } from '@features/shipment/shipment.service';
import { matchesScan } from '@features/shipment/shipment-label-print.util';

/** One box, AWB (Tracking No.) or Shipment No.; resolves to the shipment and navigates
 *  straight to its full details view (`ShipmentView`, `/shipments/:id`) — that page shows
 *  Timeline and Charges inline itself, so there's nothing to duplicate here. Shared by the
 *  Track page and the Dashboard. */
@Component({
  selector: 'app-track-box',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiButton, UiInput],
  template: `
    <div class="row">
      <app-input [control]="searchControl" label="AWB / Shipment No." placeholder="AWB… / SHP-…" (keydown.enter)="track()" />
      <app-button icon="search" [loading]="searching()" (pressed)="track()">Track</app-button>
      @if (showPrint()) {
        <app-button icon="label" variant="stroked" [loading]="searching()" (pressed)="track('labels')">Print Labels (A4)</app-button>
        <app-button icon="label" variant="stroked" [loading]="searching()" (pressed)="track('labels', 'mobile')">Thermal Label Print</app-button>
        <app-button icon="print" variant="stroked" [loading]="searching()" (pressed)="track('copy')">Shipment Copy</app-button>
      }
    </div>
    @if (notFound()) {
      <p class="empty">No shipment matches "{{ lastQuery() }}".</p>
    }
  `,
  styles: [`
    .row { display:flex; gap:12px; align-items:flex-end; }
    .row app-input { flex:1; }
    .empty { font:400 17px var(--font-sans); color:var(--content-muted); text-align:center; padding:12px 0 0; }
  `]
})
export class TrackBox {
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly shipmentService = inject(ShipmentService);

  /** Prefilled from the AI assistant's `?q=` handoff (see `Track` page) — auto-runs the
   *  search once, so "track SHP-000123" lands here already resolved instead of just
   *  typed into the box. */
  readonly initialQuery = input<string | null>(null);

  /** Print Labels / Thermal Label / Shipment Copy buttons. Off on the dashboard, on in the Track page. */
  readonly showPrint = input(true);

  readonly searching = signal(false);
  readonly notFound = signal(false);
  readonly lastQuery = signal('');

  readonly searchControl = new FormControl('');

  constructor() {
    effect(() => {
      const q = this.initialQuery();
      if (q) { this.searchControl.setValue(q); this.track(); }
    });
  }

  /** `print`: land on the shipment with that print (A4 labels dialog / Shipment Copy) already triggered. */
  track(print?: 'labels' | 'copy', value: string = '1'): void {
    const raw = this.searchControl.value?.trim();
    if (!raw) return;
    this.searching.set(true);
    this.notFound.set(false);
    this.lastQuery.set(raw);

    this.shipmentService.list({ page: 0, size: 5, search: raw }).subscribe({
      next: (p) => {
        this.searching.set(false);
        const shipment = p.content.find((s) => matchesScan(s, raw));
        if (shipment) this.router.navigate(['/shipments', shipment.id], print ? { queryParams: { [print]: value } } : {});
        else this.notFound.set(true);
      },
      error: (e: HttpErrorResponse) => {
        this.searching.set(false);
        this.notFound.set(true);
        if (e.status !== 404) this.notify.error(e.error?.message ?? 'Track failed.');
      }
    });
  }
}
