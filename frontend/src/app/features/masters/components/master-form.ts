import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { MasterOption, MasterRecord } from '@core/models/master.model';
import { MasterDataService } from '../master-data.service';
import { LookupSource, MasterDefinition, MasterField } from '../master.config';
import { MasterFieldControl } from './master-field-control';

/**
 * The create and edit form for every master list.
 *
 * Controls, validators and layout are all derived from the definition's `fields`, so the
 * twelve forms cannot drift apart and adding a field is a one-line data change. Validators
 * mirror the DTOs deliberately: the user should be told that a code is malformed before
 * the round trip, not by a 400 afterwards.
 *
 * `code` is disabled in edit mode rather than hidden. Hiding it would leave the user
 * wondering where it went; showing it greyed out says "this is permanent", which is the
 * actual rule — operational records quote the code.
 */
@Component({
  selector: 'app-master-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, UiButton, UiCard, MasterFieldControl],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="mform">
      @for (group of groups(); track group.name) {
        <app-card [title]="group.name">
          <div class="mform__grid">
            @for (field of group.fields; track field.key) {
              <app-master-field [field]="field" [control]="controlFor(field.key)"
                                [options]="optionsFor(field)" />
            }
          </div>
        </app-card>
      }

      <div class="mform__actions">
        <app-button variant="stroked" type="button" (pressed)="cancelled.emit()">Cancel</app-button>
        <app-button type="submit" [loading]="saving()" [disabled]="saving()">
          {{ record() ? 'Save changes' : 'Create ' + def().singular.toLowerCase() }}
        </app-button>
      </div>
    </form>
  `,
  styles: [`
    .mform { display:flex; flex-direction:column; gap:16px; }
    .mform__grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(260px, 1fr)); gap:16px; }
    .mform__actions { display:flex; justify-content:flex-end; gap:8px; }
  `]
})
export class MasterForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MasterDataService);

  readonly def = input.required<MasterDefinition>();
  /** The row being edited, or null when creating. */
  readonly record = input<MasterRecord | null>(null);
  readonly saving = input(false);

  readonly saved = output<Record<string, unknown>>();
  readonly cancelled = output<void>();

  form!: FormGroup;

  private readonly lookupOptions = signal<Record<string, MasterOption[]>>({});

  /** Fields laid out by their `group`, in declaration order. */
  readonly groups = computed(() => {
    const ordered: { name: string; fields: MasterField[] }[] = [];
    for (const field of this.def().fields) {
      const name = field.group ?? 'Details';
      const existing = ordered.find((g) => g.name === name);
      if (existing) existing.fields.push(field);
      else ordered.push({ name, fields: [field] });
    }
    return ordered;
  });

  constructor() {
    // The record arrives after the form is built (the page fetches it), so patch when it lands.
    effect(() => {
      const record = this.record();
      if (record && this.form) this.patch(record);
    });
  }

  ngOnInit(): void {
    this.form = this.fb.group(
      Object.fromEntries(this.def().fields.map((field) => [field.key, this.buildControl(field)]))
    );

    const record = this.record();
    if (record) this.patch(record);

    const sources = this.def().fields
      .filter((f) => f.kind === 'lookup' && f.lookup)
      .map((f) => f.lookup as LookupSource);

    for (const [source, request] of this.service.optionsFor(sources)) {
      request.subscribe({
        next: (options) => this.lookupOptions.update((c) => ({ ...c, [source]: options })),
        // An empty picker with an explanation beats a form that will not open.
        error: () => this.lookupOptions.update((c) => ({ ...c, [source]: [] }))
      });
    }
  }

  controlFor(key: string): FormControl {
    return this.form.get(key) as FormControl;
  }

  optionsFor(field: MasterField): MasterOption[] {
    return field.lookup ? this.lookupOptions()[field.lookup] ?? [] : [];
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saved.emit(this.payload());
  }

  /**
   * The body to send. Blank strings become null so an emptied optional field is cleared
   * rather than stored as `""`, and the create-only code is omitted on edit — the update
   * DTO has no field for it.
   */
  private payload(): Record<string, unknown> {
    const editing = !!this.record();
    const body: Record<string, unknown> = {};

    for (const field of this.def().fields) {
      if (editing && field.createOnly) continue;
      const value = this.form.get(field.key)?.value;
      if (field.kind === 'boolean') {
        body[field.key] = !!value;
      } else if (value === '' || value === undefined) {
        body[field.key] = null;
      } else {
        body[field.key] = value;
      }
    }
    if (editing) body['version'] = this.record()!.version;
    return body;
  }

  private buildControl(field: MasterField): FormControl {
    const validators = [];
    if (field.required && field.kind !== 'boolean') validators.push(Validators.required);
    if (field.maxLength) validators.push(Validators.maxLength(field.maxLength));
    if (field.pattern) validators.push(Validators.pattern(field.pattern));
    if (field.min !== undefined) validators.push(Validators.min(field.min));
    if (field.max !== undefined) validators.push(Validators.max(field.max));

    // A toggle has no "unset" state to show, so its create-form default is declared in the
    // definition; everything else starts empty.
    const initial = field.kind === 'boolean' ? field.initial === true : null;
    return this.fb.control(initial, validators);
  }

  private patch(record: MasterRecord): void {
    for (const field of this.def().fields) {
      const control = this.form.get(field.key);
      if (!control) continue;

      const value = record[field.key];
      control.setValue(field.kind === 'boolean' ? !!value : value ?? null, { emitEvent: false });

      // Immutable once set, and shown rather than hidden so the rule is visible.
      if (field.createOnly) control.disable({ emitEvent: false });
    }
  }
}
