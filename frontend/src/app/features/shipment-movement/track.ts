import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { TrackBox } from './components/track-box';

/** Track — the dedicated page wrapping `TrackBox` (also embedded directly on the
 *  Dashboard so tracking doesn't need a click-through first). Reads an optional `?q=`
 *  query param — the AI assistant's "track <AWB/LR>" command navigates here with it set,
 *  so the search runs immediately instead of landing on an empty box. */
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
        <app-track-box [initialQuery]="initialQuery()" />
      </app-card>
    </div>
  `
})
export class Track implements OnInit {
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly route = inject(ActivatedRoute);

  protected readonly initialQuery = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('q'))),
    { initialValue: null }
  );

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Track' }]);
  }
}
