import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { ShipmentLabelData, printLabels, renderLabelsHtml } from './shipment-label-print.util';

/** Print preview for a shipment's package labels — one label per package, all under the
 *  same AWB. Print all together, or reprint a single package. */
@Component({
  selector: 'app-shipment-labels-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, UiButton],
  template: `
    <h2 mat-dialog-title>Package Labels — {{ data.trackingNumber }} ({{ data.numberOfPackages }})</h2>
    <mat-dialog-content class="lbl">
      <iframe class="lbl__frame" title="Label preview" [srcdoc]="preview"></iframe>
      <div class="lbl__single">
        <span class="text-caption">Print one label:</span>
        @for (n of nums; track n) {
          <app-button variant="stroked" (pressed)="print([n])">{{ n }}/{{ data.numberOfPackages }}</app-button>
        }
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <app-button variant="stroked" mat-dialog-close>Close</app-button>
      <app-button icon="print" (pressed)="print()">Print All Labels</app-button>
    </mat-dialog-actions>
  `,
  styles: [`
    .lbl { min-width: 460px; }
    .lbl__frame { width: 100%; height: 48vh; border: 1px solid var(--border, #ccc); background: #e9e9e9; }
    .lbl__single { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin-top:10px; }
  `]
})
export class ShipmentLabelsDialog {
  protected readonly data = inject<ShipmentLabelData>(MAT_DIALOG_DATA);
  protected readonly nums = Array.from({ length: Math.max(1, this.data.numberOfPackages) }, (_, i) => i + 1);
  // Everything interpolated into the document is HTML-escaped by the renderer.
  protected readonly preview = inject(DomSanitizer).bypassSecurityTrustHtml(renderLabelsHtml(this.data)) as string;

  protected print(packages?: number[]): void { printLabels(this.data, packages); }
}
