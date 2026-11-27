import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface SelectOption { value: string; label: string; }

/** Labelled field over a FormControl. Options are plain {value,label}. Single-select is a
 *  type-to-filter autocomplete (the control holds the option `value`, never the typed text:
 *  an unmatched entry snaps back to the current selection on blur); `multiple` stays a
 *  checkbox dropdown. */
@Component({
  selector: 'app-select',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatSelectModule, MatAutocompleteModule, MatInputModule, MatFormFieldModule],
  template: `
    <label class="sel">
      @if (label()) { <span class="sel__label">{{ label() }}</span> }
      <mat-form-field appearance="outline" class="sel__field" subscriptSizing="dynamic">
        @if (multiple()) {
          <mat-select [formControl]="control()" [placeholder]="placeholder()" [multiple]="true">
            @if (allowEmpty()) { <mat-option [value]="null">{{ emptyLabel() }}</mat-option> }
            @for (opt of options(); track opt.value) {
              <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
            }
          </mat-select>
        } @else {
          <input #inputEl matInput autocomplete="off" [placeholder]="placeholder()" [matAutocomplete]="auto"
                 [disabled]="disabled()"
                 (focus)="onFocus()" (input)="onType($event)" (blur)="onBlur()" />
          <mat-autocomplete #auto="matAutocomplete" (optionSelected)="onPick($event)">
            @if (allowEmpty()) { <mat-option [value]="null">{{ emptyLabel() }}</mat-option> }
            @for (opt of filtered(); track opt.value) {
              <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
            }
            @if (filtered().length === 0) { <mat-option disabled>No match</mat-option> }
          </mat-autocomplete>
        }
      </mat-form-field>
    </label>
  `,
  styles: [`
    .sel { display:flex; flex-direction:column; gap:6px; }
    .sel__label { font:500 16px var(--font-sans); color:var(--content-fg); }
    .sel__field { width:100%; }
    // Material's outline appearance, restyled into a clay "well" to match app-input —
    // ::ng-deep since the outline/wrapper are Material internals, not this component's own DOM.
    .sel__field ::ng-deep .mdc-notched-outline > * { border-color:transparent !important; }
    .sel__field ::ng-deep .mat-mdc-text-field-wrapper {
      background:var(--surface-muted); border-radius:var(--r-field); box-shadow:var(--shadow-clay-inset);
    }
    .sel__field ::ng-deep .mat-mdc-form-field-flex { min-height:44px; align-items:center; }
    .sel__field.mat-focused ::ng-deep .mat-mdc-text-field-wrapper { box-shadow:var(--shadow-clay-inset), 0 0 0 3px var(--brand-100); }
  `]
})
export class UiSelect {
  readonly control = input.required<FormControl>();
  readonly options = input<SelectOption[]>([]);
  readonly label = input('');
  readonly placeholder = input('Select…');
  readonly multiple = input(false);
  readonly allowEmpty = input(false);
  readonly emptyLabel = input('All');

  private readonly inputEl = viewChild<ElementRef<HTMLInputElement>>('inputEl');
  private readonly query = signal('');
  protected readonly disabled = signal(false);
  /** Bumped on every control change so the displayed text re-syncs. */
  private readonly tick = signal(0);

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const opts = this.options();
    return q ? opts.filter((o) => o.label.toLowerCase().includes(q)) : opts;
  });

  private labelOf(value: unknown): string {
    return this.options().find((o) => o.value === value)?.label ?? '';
  }

  constructor() {
    effect((onCleanup) => {
      const ctrl = this.control();
      this.disabled.set(ctrl.disabled);
      const subs = [
        ctrl.valueChanges.subscribe(() => this.tick.update((n) => n + 1)),
        ctrl.statusChanges.subscribe(() => this.disabled.set(ctrl.disabled))
      ];
      onCleanup(() => subs.forEach((x) => x.unsubscribe()));
    });
    // Re-render the text from the control + options (options often arrive after the value).
    effect(() => {
      this.tick(); this.options();
      const el = this.inputEl()?.nativeElement;
      if (el && document.activeElement !== el) el.value = this.labelOf(this.control().value);
    });
  }

  protected onFocus(): void {
    this.query.set('');
    this.inputEl()?.nativeElement.select();
  }

  protected onType(e: Event): void {
    const text = (e.target as HTMLInputElement).value;
    this.query.set(text);
    // Clearing the text clears the value when the field allows "no selection".
    if (!text && this.allowEmpty() && this.control().value != null) {
      this.control().setValue(null);
      this.control().markAsDirty();
    }
  }

  protected onPick(e: MatAutocompleteSelectedEvent): void {
    const ctrl = this.control();
    ctrl.setValue(e.option.value);
    ctrl.markAsDirty();
    const el = this.inputEl()?.nativeElement;
    if (el) el.value = this.labelOf(e.option.value);
  }

  protected onBlur(): void {
    this.control().markAsTouched();
    const el = this.inputEl()?.nativeElement;
    if (el) el.value = this.labelOf(this.control().value);
    this.query.set('');
  }
}
