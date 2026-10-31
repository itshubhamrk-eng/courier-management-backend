import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Page } from '@core/models/page.model';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PermissionService } from '@core/auth/permission.service';
import { BUS_DEPOT_TYPE_LABEL, BusTimetableBoard as Board, BusTimetableRow, parseTimes } from '@core/models/bus-depot.model';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiPagination } from '@shared/components/ui-pagination/ui-pagination';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { BusDepotService } from './bus-depot.service';

/** An editable board row: times are kept as the text the clerk types, parsed on save. */
interface DraftRow { placeName: string; major: boolean; departures: string; arrivals: string; }

/**
 * One depot / station's timetable board, laid out like the MSRTC sheet: Place | Departing
 * buses | Arriving buses, major places in red. Everyone on the MSRTC desk reads it; a
 * COMPANY_ADMIN can edit and save the whole board.
 */
@Component({
  selector: 'app-bus-timetable-board',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiCard, UiButton, UiLoader, UiPagination],
  template: `
    <div class="page">
      <header class="page__head">
        <div>
          <h1 class="text-h1">{{ board()?.depot?.depotName || 'Timetable' }}</h1>
          <p class="text-caption">
            @if (board(); as b) { {{ b.depot.depotCode }} · {{ typeLabel() }} }
            · वेळापत्रक — times buses leave for / arrive from each place
          </p>
        </div>
        <div class="page__actions">
          <app-button variant="stroked" (pressed)="back()">All timetables</app-button>
          @if (canEdit() && !editing() && board()) { <app-button icon="edit" (pressed)="startEdit()">Edit</app-button> }
        </div>
      </header>

      @if (loading()) {
        <app-loader [minHeight]="200" caption="Loading…" />
      } @else if (!board()) {
        <app-card><p class="empty">Depot / station not found.</p></app-card>
      } @else if (editing()) {
        <app-card title="Edit timetable">
          <p class="hint">Times as 06.30, 13:00 … separated by commas or spaces. Tick <b>Major</b> for long-distance places (shown in red).</p>
          <div class="grid grid--head"><span>Place</span><span>Departing (जाणाऱ्या गाड्या)</span><span>Arriving (येणाऱ्या गाड्या)</span><span>Major</span><span></span></div>
          @for (r of draft(); track r; let i = $index) {
            <div class="grid">
              <input class="in" [value]="r.placeName" (input)="set(r, 'placeName', $any($event.target).value)" placeholder="Place" aria-label="Place" />
              <textarea class="in" rows="2" [value]="r.departures" (input)="set(r, 'departures', $any($event.target).value)" placeholder="06.30, 12.00" aria-label="Departures"></textarea>
              <textarea class="in" rows="2" [value]="r.arrivals" (input)="set(r, 'arrivals', $any($event.target).value)" placeholder="11.45, 16.00" aria-label="Arrivals"></textarea>
              <input type="checkbox" [checked]="r.major" (change)="set(r, 'major', $any($event.target).checked)" aria-label="Major place" />
              <span class="mv">
                <button type="button" class="ic" (click)="move(i, -1)" [disabled]="i === 0" aria-label="Move up">↑</button>
                <button type="button" class="ic" (click)="move(i, 1)" [disabled]="i === draft().length - 1" aria-label="Move down">↓</button>
                <button type="button" class="ic ic--x" (click)="removeRow(i)" aria-label="Remove row">✕</button>
              </span>
            </div>
          }
          <app-button variant="stroked" (pressed)="addRow()">+ Add place</app-button>
        </app-card>
        <div class="bar">
          <app-button variant="stroked" (pressed)="cancelEdit()">Cancel</app-button>
          <app-button icon="save" [loading]="saving()" (pressed)="save()">Save Timetable</app-button>
        </div>
      } @else {
        <app-card>
          @if (!board()!.rows.length) {
            <p class="empty">No timetable saved for this depot / station yet.@if (canEdit()) { Press Edit to enter it. }</p>
          } @else {
            <div class="tt">
              <div class="tt__row tt__row--head"><span>गावाचे नाव · Place</span><span>जाणाऱ्या गाड्या · Departing</span><span>येणाऱ्या गाड्या · Arriving</span></div>
              @for (r of rowPage().content; track r.id) {
                <div class="tt__row" [class.tt__row--major]="r.major">
                  <span class="tt__place">{{ r.placeName }}</span>
                  <span>{{ r.departures.join(', ') || '—' }}</span>
                  <span>{{ r.arrivals.join(', ') || '—' }}</span>
                </div>
              }
            </div>
            <app-pagination [page]="rowPage()" (pageChange)="rowIndex.set($event)" />
          }
        </app-card>
      }
    </div>
  `,
  styles: [`
    .empty { font:400 17px var(--font-sans); color:var(--content-muted); text-align:center; padding:16px; }
    .hint { margin:0 0 10px; font:400 15px var(--font-sans); color:var(--content-muted); }
    .tt { border:1px solid var(--surface-border); border-radius:10px; overflow:hidden; }
    .tt__row { display:grid; grid-template-columns:minmax(120px,1fr) 2fr 2fr; gap:12px; padding:9px 12px; border-top:1px solid var(--surface-border); font:500 17px var(--font-sans); }
    .tt__row:first-child { border-top:0; }
    .tt__row--head { background:var(--surface-muted); font:600 15px var(--font-sans); color:var(--content-muted); }
    .tt__row--major { color:#b91c1c; font-weight:600; }
    .tt__place { font-weight:600; }
    .grid { display:grid; grid-template-columns:minmax(120px,1fr) 2fr 2fr 56px 112px; gap:8px; align-items:start; margin-bottom:8px; }
    .grid--head { font:600 15px var(--font-sans); color:var(--content-muted); margin-bottom:4px; }
    .in { width:100%; box-sizing:border-box; border:1px solid var(--surface-border); border-radius:8px; padding:8px 10px; font:400 17px var(--font-sans); background:var(--surface); color:inherit; resize:vertical; }
    .mv { display:flex; gap:4px; }
    .ic { height:34px; width:34px; border:1px solid var(--surface-border); background:transparent; border-radius:8px; cursor:pointer; color:var(--content-muted); }
    .ic:disabled { opacity:.4; cursor:default; } .ic--x:hover { color:var(--danger, #e11d48); border-color:currentColor; }
    .bar { display:flex; justify-content:flex-end; gap:10px; margin-top:8px; }
    @media (max-width:760px){ .tt__row, .grid { grid-template-columns:1fr; } .grid--head { display:none; } }
  `]
})
export class BusTimetableBoard implements OnInit {
  private readonly service = inject(BusDepotService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly permissions = inject(PermissionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly depotId = this.route.snapshot.paramMap.get('depotId')!;
  protected readonly board = signal<Board | null>(null);
  protected readonly loading = signal(true);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly draft = signal<DraftRow[]>([]);
  protected readonly rowIndex = signal(0);
  protected readonly rowPage = computed<Page<BusTimetableRow>>(() => {
    const all = (this.board()?.rows ?? []), size = 25;
    const totalPages = Math.max(1, Math.ceil(all.length / size));
    const page = Math.min(this.rowIndex(), totalPages - 1);
    return {
      content: all.slice(page * size, (page + 1) * size), page, size, totalElements: all.length, totalPages,
      first: page === 0, last: page >= totalPages - 1, hasNext: page < totalPages - 1
    };
  });
  protected readonly canEdit = computed(() => this.permissions.hasRole('COMPANY_ADMIN'));
  protected readonly typeLabel = computed(() => (this.board() ? BUS_DEPOT_TYPE_LABEL[this.board()!.depot.depotType] : ''));

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'MSRTC' }, { label: 'Bus Timetable', route: '/msrtc/timetable' }, { label: 'Board' }]);
    this.service.timetable(this.depotId).subscribe({
      next: (b) => { this.board.set(b); this.loading.set(false); if (this.canEdit() && !b.rows.length) this.startEdit(); },
      error: () => { this.board.set(null); this.loading.set(false); }
    });
  }

  protected startEdit(): void {
    const rows = this.board()?.rows ?? [];
    this.draft.set(rows.length ? rows.map((r) => ({
      placeName: r.placeName, major: r.major, departures: r.departures.join(', '), arrivals: r.arrivals.join(', ')
    })) : [this.blank()]);
    this.editing.set(true);
  }

  protected cancelEdit(): void { this.editing.set(false); }
  protected addRow(): void { this.draft.update((d) => [...d, this.blank()]); }
  protected removeRow(i: number): void { this.draft.update((d) => d.filter((_, k) => k !== i)); }

  protected set<K extends keyof DraftRow>(row: DraftRow, key: K, value: DraftRow[K]): void { row[key] = value; }

  protected move(i: number, by: number): void {
    this.draft.update((d) => {
      const next = [...d];
      const j = i + by;
      if (j < 0 || j >= next.length) return d;
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });
  }

  protected save(): void {
    const rows: BusTimetableRow[] = [];
    for (const r of this.draft()) {
      const name = r.placeName.trim();
      const dep = parseTimes(r.departures);
      const arr = parseTimes(r.arrivals);
      if (!name && !dep.times.length && !arr.times.length && !dep.bad.length && !arr.bad.length) continue; // untouched blank row
      if (!name) { this.notify.error('Every row needs a place name.'); return; }
      const bad = [...dep.bad, ...arr.bad];
      if (bad.length) { this.notify.error(`${name}: cannot read time(s) "${bad.join('", "')}" — use 06.30 or 06:30.`); return; }
      rows.push({ placeName: name, major: r.major, departures: dep.times, arrivals: arr.times });
    }
    this.saving.set(true);
    this.service.saveTimetable(this.depotId, rows).subscribe({
      next: (b) => { this.saving.set(false); this.board.set(b); this.editing.set(false); this.notify.success('Timetable saved.'); },
      error: (e: HttpErrorResponse) => { this.saving.set(false); this.notify.error(e.error?.message ?? 'Could not save the timetable.'); }
    });
  }

  protected back(): void { this.router.navigate(['/msrtc/timetable']); }
  private blank(): DraftRow { return { placeName: '', major: false, departures: '', arrivals: '' }; }
}
