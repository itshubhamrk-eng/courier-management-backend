import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { TrackBox } from './components/track-box';

/** Track — the dedicated page wrapping `TrackBox` (also embedded directly on the
 *  Dashboard so tracking doesn't need a click-through first). */
@Component({
  selector: 'app-track',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiCard, TrackBox],
  template: `
    <div class="page">
      <header class="page__head" data-tour="track-head">
        <div><h1 class="text-h1">Track Shipment</h1>
          <p class="text-caption">Enter an AWB (Tracking No.) or Shipment No. to open its full details.</p></div>
      </header>

      <app-card title="Track">
        <app-track-box />
      </app-card>
    </div>
  `
})
export class Track implements OnInit {
  private readonly breadcrumb = inject(BreadcrumbService);

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Track' }]);
  }
}
