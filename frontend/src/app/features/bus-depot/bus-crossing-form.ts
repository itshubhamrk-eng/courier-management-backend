import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { BusCrossingPick } from '@core/models/bus-depot.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { BusCrossingEditor } from './bus-crossing-editor';
import { BusDepotService } from './bus-depot.service';

/** Create / edit one crossing route. Saving a From/To pair that already has one replaces its crossing points. */
@Component({
  selector: 'app-bus-crossing-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiCard, UiButton, UiLoader, BusCrossingEditor],
  template: `
    <div class="page">
      <header class="page__head">
        <div><h1 class="text-h1">{{ routeId ? 'Edit Crossing' : 'New Crossing' }}</h1>
          <p class="text-caption">Depot or station, in any combination — not tied to a branch.</p></div>
      </header>
      @if (loading()) {
        <app-loader [minHeight]="200" caption="Loading…" />
      } @else {
        <app-card title="Route">
          <app-bus-crossing-editor [optional]="false" [suggest]="!routeId" [initial]="initial()" [(value)]="pick" [(incomplete)]="incomplete" />
        </app-card>
        <div class="bar">
          <app-button variant="stroked" (pressed)="back()">Cancel</app-button>
          <app-button icon="save" [loading]="saving()" [disabled]="!pick()" (pressed)="save()">Save</app-button>
        </div>
      }
    </div>
  `,
  styles: [`.bar { display:flex; justify-content:flex-end; gap:10px; margin-top:8px; }`]
})
export class BusCrossingForm implements OnInit {
  private readonly service = inject(BusDepotService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly routeId = this.route.snapshot.paramMap.get('id');
  protected readonly loading = signal(!!this.routeId);
  protected readonly saving = signal(false);
  protected readonly initial = signal<BusCrossingPick | null>(null);
  protected readonly pick = signal<BusCrossingPick | null>(null);
  protected readonly incomplete = signal(false);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'MSRTC' }, { label: 'Crossings', route: '/msrtc/crossings' }, { label: this.routeId ? 'Edit' : 'New' }]);
    if (!this.routeId) return;
    this.service.crossing(this.routeId).subscribe({
      next: (r) => {
        this.initial.set({ fromDepotId: r.from.id, toDepotId: r.to.id, stops: r.stops.map((s) => s.id) });
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.back(); }
    });
  }

  protected save(): void {
    const body = this.pick();
    if (!body) return;
    this.saving.set(true);
    (this.routeId ? this.service.updateCrossing(this.routeId, body) : this.service.saveCrossing(body)).subscribe({
      next: () => { this.saving.set(false); this.notify.success('Crossing saved.'); this.back(); },
      error: (e: HttpErrorResponse) => { this.saving.set(false); this.notify.error(e.error?.message ?? 'Could not save the crossing.'); }
    });
  }

  protected back(): void { this.router.navigate(['/msrtc/crossings']); }
}
