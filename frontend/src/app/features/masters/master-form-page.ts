import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { MasterRecord } from '@core/models/master.model';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { MasterDataService } from './master-data.service';
import { MasterForm } from './components/master-form';
import { MasterDefinition, findMaster } from './master.config';

/**
 * Create and edit, for every master list.
 *
 * One component rather than two because the difference is three lines: edit fetches the
 * row first, sends a PUT carrying the version, and reloads on a 409. Splitting that into a
 * `master-create` and a `master-edit` would duplicate the form wiring twice over for all
 * twelve lists.
 *
 * A 409 means someone else saved while this form was open. The row is reloaded rather than
 * the edit being thrown away, so the user sees what changed and can reapply their part —
 * silently overwriting the other person is the one outcome that must not happen.
 */
@Component({
  selector: 'app-master-form-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UiLoader, MasterForm],
  template: `
    @if (def(); as d) {
      <div class="page page--narrow">
        <header class="page__head">
          <div>
            <h1 class="text-h1">{{ editing() ? 'Edit' : 'New' }} {{ d.singular.toLowerCase() }}</h1>
            <p class="text-caption">{{ d.description }}</p>
          </div>
        </header>

        @if (loading()) {
          <app-loader [minHeight]="280" caption="Loading…" />
        } @else {
          <app-master-form [def]="d" [record]="record()" [saving]="saving()"
                           (saved)="save($event)" (cancelled)="cancel()" />
        }
      </div>
    }
  `,
  styles: [`
    .page--narrow { max-width: 980px; }
  `]
})
export class MasterFormPage {
  private readonly service = inject(MasterDataService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);

  readonly def = toSignal(
    this.route.paramMap.pipe(map((p) => findMaster(p.get('master')))),
    { initialValue: null as MasterDefinition | null }
  );
  private readonly id = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('id'))),
    { initialValue: null as string | null }
  );

  readonly editing = computed(() => !!this.id());
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly record = signal<MasterRecord | null>(null);

  constructor() {
    effect(() => {
      const def = this.def();
      const id = this.id();
      if (!def) {
        this.router.navigate(['/masters/countries']);
        return;
      }
      this.breadcrumb.set([
        { label: 'Masters' },
        { label: def.plural, route: `/masters/${def.key}` },
        { label: id ? 'Edit' : 'New' }
      ]);
      if (id) this.fetch(def, id);
      else this.record.set(null);
    });
  }

  private fetch(def: MasterDefinition, id: string): void {
    this.loading.set(true);
    this.service.get(def, id).subscribe({
      next: (row) => { this.record.set(row); this.loading.set(false); },
      error: (e) => {
        this.loading.set(false);
        this.notify.error(e?.error?.message ?? `Could not load the ${def.singular.toLowerCase()}.`);
        this.router.navigate(['/masters', def.key]);
      }
    });
  }

  save(body: Record<string, unknown>): void {
    const def = this.def()!;
    const id = this.id();
    this.saving.set(true);

    const request = id
      ? this.service.update(def, id, body)
      : this.service.create(def, body);

    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notify.success(`${def.singular} ${id ? 'updated' : 'created'}.`);
        this.router.navigate(['/masters', def.key, saved.id]);
      },
      error: (e) => {
        this.saving.set(false);
        if (e?.status === 409 && id) {
          this.notify.error('Someone else saved this record first. It has been reloaded — reapply your changes.');
          this.fetch(def, id);
          return;
        }
        this.notify.error(e?.error?.message ?? `Could not save the ${def.singular.toLowerCase()}.`);
      }
    });
  }

  cancel(): void {
    const def = this.def()!;
    const id = this.id();
    this.router.navigate(id ? ['/masters', def.key, id] : ['/masters', def.key]);
  }
}
