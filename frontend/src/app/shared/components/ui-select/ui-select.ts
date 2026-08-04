import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';

export interface SelectOption { value: string; label: string; }

/** Labelled dropdown over a FormControl. Options are plain {value,label}. */
@Component({
  selector: 'app-select',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatSelectModule, MatFormFieldModule],
  template: `
    <label class="sel">
      @if (label()) { <span class="sel__label">{{ label() }}</span> }
      <mat-form-field appearance="outline" class="sel__field" subscriptSizing="dynamic">
        <mat-select [formControl]="control()" [placeholder]="placeholder()" [multiple]="multiple()">
          @if (allowEmpty()) { <mat-option [value]="null">{{ emptyLabel() }}</mat-option> }
          @for (opt of options(); track opt.value) {
            <mat-option [value]="opt.value">{{ opt.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </label>
  `,
  styles: [`
    .sel { display:flex; flex-direction:column; gap:6px; }
    .sel__label { font:500 13px var(--font-sans); color:var(--content-fg); }
    .sel__field { width:100%; }
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
}
