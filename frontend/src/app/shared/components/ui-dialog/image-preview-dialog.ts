import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface ImagePreviewData {
  url: string;
  title?: string;
}

/** A click-to-enlarge preview for one uploaded image (POD photo/signature, etc.) — the
 *  image at natural size, capped to the viewport, with a close button. */
@Component({
  selector: 'app-image-preview-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatIconModule],
  template: `
    <div class="ipd">
      <div class="ipd__head">
        <span class="text-h2">{{ data.title ?? 'Preview' }}</span>
        <button class="ipd__close" type="button" (click)="ref.close()" aria-label="Close">
          <mat-icon>close</mat-icon>
        </button>
      </div>
      <div class="ipd__body">
        <img [src]="data.url" [alt]="data.title ?? 'Preview'" />
      </div>
    </div>
  `,
  styles: [`
    .ipd { display:flex; flex-direction:column; max-width:90vw; max-height:90vh; }
    .ipd__head { display:flex; justify-content:space-between; align-items:center; padding:14px 16px; }
    .ipd__close { border:none; background:none; cursor:pointer; display:flex; color:var(--content-muted); }
    .ipd__body { padding:0 16px 16px; overflow:auto; display:flex; justify-content:center; }
    .ipd__body img { max-width:100%; max-height:80vh; object-fit:contain; border-radius:var(--r-field); }
  `]
})
export class ImagePreviewDialog {
  readonly ref = inject(MatDialogRef<ImagePreviewDialog>);
  readonly data = inject<ImagePreviewData>(MAT_DIALOG_DATA);
}
