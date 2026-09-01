import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { NotificationService } from '@core/services/notification.service';
import { CommunicationTemplate } from '@core/models/communication.model';
import { CommunicationService } from '../communication.service';

export interface TemplateEditData {
  template: CommunicationTemplate;
}

const VARIABLES = ['customerName', 'companyName', 'shipmentNumber', 'trackingNumber', 'pickupLocation',
  'deliveryLocation', 'amount', 'deliveryDate', 'receiverName', 'trackingUrl', 'podUrl'];

/** Edit one (event, channel) template's content/subject, toggle it ACTIVE/INACTIVE — the
 *  brief's own "Company Admin can Create/Edit/Enable/Disable/Preview" — and preview it
 *  rendered against synthetic sample data. Closes with `true` when anything was saved, so
 *  the caller knows to reload its list. */
@Component({
  selector: 'app-template-edit-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatDialogModule, MatSlideToggleModule, UiInput, UiButton],
  template: `
    <div class="ted">
      <h2 class="text-h2">{{ current().eventType }} + {{ current().channel }}</h2>
      <p class="text-caption">{{ current().templateName }}</p>

      <form [formGroup]="form" (ngSubmit)="save()" class="ted__form">
        <label class="flag"><mat-slide-toggle [formControl]="c('enabled')" />
          <span><strong>Enabled</strong><em>Whether this event sends on this channel</em></span></label>

        @if (current().channel === 'EMAIL') {
          <app-input [control]="c('subject')" label="Subject" placeholder="Your shipment has been booked" [maxLength]="255" />
        }

        <label class="text-caption ted__label">Content</label>
        <textarea class="ted__textarea" [formControl]="c('content')" rows="6"></textarea>

        <div class="ted__vars">
          <span class="text-caption">Variables:</span>
          @for (v of variables; track v) {
            <button type="button" class="chip" (click)="insert(v)">{{ '{{' + v + '}}' }}</button>
          }
        </div>

        <div class="ted__actions">
          <app-button type="button" variant="stroked" [loading]="previewing()" (pressed)="preview()">Preview</app-button>
          <span class="spacer"></span>
          <app-button type="button" variant="stroked" (pressed)="close()">Cancel</app-button>
          <app-button type="submit" icon="save" [loading]="saving()">Save</app-button>
        </div>

        @if (previewResult(); as p) {
          <div class="ted__preview">
            @if (p.subject) { <p class="ted__preview-subject"><strong>Subject:</strong> {{ p.subject }}</p> }
            <p class="ted__preview-body">{{ p.content }}</p>
          </div>
        }
      </form>
    </div>
  `,
  styles: [`
    .ted { display:flex; flex-direction:column; gap:12px; min-width:420px; max-width:560px; }
    .ted__form { display:flex; flex-direction:column; gap:14px; }
    .flag { display:flex; align-items:center; gap:12px; }
    .flag span { display:flex; flex-direction:column; }
    .flag em { font-style:normal; font-size:12px; color:var(--content-muted); }
    .ted__label { margin-bottom:-6px; }
    .ted__textarea { width:100%; box-sizing:border-box; padding:10px 12px; border-radius:var(--r-field);
      border:1px solid var(--surface-border); background:var(--surface); font:400 14px var(--font-sans);
      color:var(--content-fg); resize:vertical; }
    .ted__vars { display:flex; flex-wrap:wrap; align-items:center; gap:6px; }
    .chip { font:500 11px var(--font-mono, ui-monospace); padding:3px 8px; border-radius:999px;
      border:1px solid var(--surface-border); background:var(--surface-muted); cursor:pointer; color:var(--content-fg); }
    .chip:hover { background:var(--brand-50); }
    .ted__actions { display:flex; align-items:center; gap:10px; }
    .spacer { flex:1; }
    .ted__preview { border:1px solid var(--surface-border); border-radius:var(--r-field); padding:12px 14px;
      background:var(--surface-muted); }
    .ted__preview-subject { margin:0 0 6px; font:600 13px var(--font-sans); }
    .ted__preview-body { margin:0; white-space:pre-wrap; font:400 13px var(--font-sans); }
  `]
})
export class TemplateEditDialog {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CommunicationService);
  private readonly notify = inject(NotificationService);
  private readonly ref = inject(MatDialogRef<TemplateEditDialog>);
  private readonly data = inject<TemplateEditData>(MAT_DIALOG_DATA);

  /** The template as currently known — refreshed after every save so a later save in the
   *  same session never sends a stale `version` and 409s against its own prior write. */
  protected readonly current = signal<CommunicationTemplate>(this.data.template);
  private savedAnything = false;

  protected readonly variables = VARIABLES;
  protected readonly saving = signal(false);
  protected readonly previewing = signal(false);
  protected readonly previewResult = signal<{ subject?: string | null; content: string } | null>(null);

  protected readonly form: FormGroup = this.fb.group({
    enabled: [this.data.template.status === 'ACTIVE'],
    subject: [this.data.template.subject ?? '', Validators.maxLength(255)],
    content: [this.data.template.content, Validators.required]
  });

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  protected insert(variable: string): void {
    const control = this.c('content');
    const value = (control.value ?? '') as string;
    control.setValue(`${value}{{${variable}}}`);
  }

  protected preview(): void {
    this.previewing.set(true);
    // Persist first so the preview reflects the content actually being edited — the
    // backend renders from the stored row, not the unsent form value.
    this.doSave(() => {
      this.service.previewTemplate(this.current().id).subscribe({
        next: (p) => { this.previewResult.set(p); this.previewing.set(false); },
        error: () => this.previewing.set(false)
      });
    });
  }

  protected save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.doSave(() => { this.notify.success('Template saved.'); this.ref.close(true); });
  }

  private doSave(onSuccess: () => void): void {
    this.saving.set(true);
    const v = this.form.getRawValue();
    const template = this.current();
    this.service.updateTemplate(template.id, {
      templateName: template.templateName,
      subject: template.channel === 'EMAIL' ? (v.subject || null) : null,
      content: v.content,
      status: v.enabled ? 'ACTIVE' : 'INACTIVE',
      version: template.version
    }).subscribe({
      next: (updated) => {
        this.current.set(updated);
        this.savedAnything = true;
        this.saving.set(false);
        onSuccess();
      },
      error: () => this.saving.set(false)
    });
  }

  protected close(): void { this.ref.close(this.savedAnything); }
}
