import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { PodService } from '@core/services/pod.service';
import { PodVerification } from '@core/models/pod.model';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { PinIllustration } from '@shared/components/illustrations/pin-illustration';

/** POD Review — the Manual Review screen POD Auto Verification's spec calls for. Lists
 *  every REVIEW-status verification (PodService.pendingReview), lets a reviewer open one,
 *  view the captured photo/signature and the AI's own reasons, then Approve (-> PASS) or
 *  Reject (-> FAIL) with an optional remark (PodService.review). AI itself never decided
 *  this — a human always does, which is the whole point of the REVIEW status existing.
 *  See MEMORY/modules/pod-verification.md. */
@Component({
  selector: 'app-pod-review',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatIconModule, UiCard, UiLoader, UiButton, UiInput, PinIllustration],
  template: `
    <div class="page">
      <header class="page__head" data-tour="pod-review-head">
        <div class="page__head-row">
          <app-pin-illustration class="page__head-ill" [size]="52" />
          <div><h1 class="text-h1">POD Review</h1>
          <p class="text-caption">AI flagged these for manual review — approve or reject before delivery can complete.</p></div>
        </div>
        @if (!selected()) {
          <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
        }
      </header>

      @if (!selected()) {
        @if (loading()) {
          <app-loader [minHeight]="120" caption="Loading…" />
        } @else if (!items().length) {
          <app-card><p class="empty">Nothing awaiting review.</p></app-card>
        } @else {
          <app-card>
            <div class="tbl__wrap">
              <table class="tbl">
                <thead>
                  <tr><th>#</th><th>Tracking No.</th><th>Receiver</th><th>Score</th><th>Reasons</th><th></th></tr>
                </thead>
                <tbody>
                  @for (v of items(); track v.id; let i = $index) {
                    <tr class="tbl__row--actionable" (click)="select(v)">
                      <td>{{ i + 1 }}</td>
                      <td>{{ v.trackingNumber || v.shipmentNumber || v.shipmentId }}</td>
                      <td>{{ v.detectedReceiverName || '—' }}</td>
                      <td>{{ v.verificationScore }}/100</td>
                      <td class="tbl__reasons">{{ v.verificationReasons[0] || '—' }}</td>
                      <td class="tbl--right"><app-button icon="fact_check" (pressed)="select(v)">Review</app-button></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </app-card>
        }
      }

      @if (selected(); as v) {
        <app-card>
          <div class="sh">
            <div><strong>{{ v.trackingNumber || v.shipmentNumber }}</strong>
              <span class="text-caption">Score {{ v.verificationScore }}/100 · {{ v.imageQuality || '—' }} image quality</span></div>
            <app-button variant="stroked" icon="close" (pressed)="selected.set(null)">Back to List</app-button>
          </div>
        </app-card>

        <app-card title="Captured POD">
          <div class="pods">
            @if (v.photoUrl) {
              <a [href]="v.photoUrl" target="_blank" rel="noopener" class="pod-thumb">
                <img [src]="v.photoUrl" alt="Delivery photo" />
                <span>Photo</span>
              </a>
            } @else {
              <div class="pod-thumb pod-thumb--empty"><mat-icon>image_not_supported</mat-icon><span>No photo</span></div>
            }
            @if (v.signatureUrl) {
              <a [href]="v.signatureUrl" target="_blank" rel="noopener" class="pod-thumb">
                <img [src]="v.signatureUrl" alt="Signature" />
                <span>Signature</span>
              </a>
            } @else {
              <div class="pod-thumb pod-thumb--empty"><mat-icon>draw</mat-icon><span>No signature</span></div>
            }
          </div>
        </app-card>

        <app-card title="AI Result">
          <dl class="ai-result__grid">
            <div><dt>Receiver</dt><dd>{{ v.detectedReceiverName || '—' }}</dd></div>
            <div><dt>AWB</dt><dd>{{ v.detectedAwb || '—' }}</dd></div>
            <div><dt>Date</dt><dd>{{ v.detectedDate || '—' }}</dd></div>
            <div><dt>Signature</dt><dd>{{ v.signatureDetected ? 'Detected' : 'Not detected' }}</dd></div>
            <div><dt>AI Provider</dt><dd>{{ v.aiProvider }} / {{ v.aiModel }}</dd></div>
          </dl>
          @if (v.verificationReasons.length) {
            <ul class="ai-result__reasons">
              @for (r of v.verificationReasons; track r) { <li>{{ r }}</li> }
            </ul>
          }
        </app-card>

        <app-card title="Decision">
          <form [formGroup]="form" class="df">
            <app-input [control]="c('remarks')" label="Remarks" placeholder="Optional" />
            <div class="df__bar">
              <app-button variant="danger" icon="cancel" [loading]="deciding() === 'reject'" (pressed)="decide(false)">Reject</app-button>
              <app-button icon="check_circle" [loading]="deciding() === 'approve'" (pressed)="decide(true)">Approve</app-button>
            </div>
          </form>
        </app-card>
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .tbl__wrap { overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl__reasons { white-space:normal; max-width:320px; }
    .tbl--right { text-align:right; }
    .tbl__row--actionable { cursor:pointer; }
    .tbl__row--actionable:hover { background:var(--surface-muted); }
    .sh { display:flex; justify-content:space-between; align-items:center; gap:12px; }
    .sh strong { display:block; font:600 15px var(--font-sans); }
    .pods { display:flex; gap:16px; flex-wrap:wrap; }
    .pod-thumb { display:flex; flex-direction:column; align-items:center; gap:6px; width:160px;
      border:1px solid var(--surface-border); border-radius:var(--r-field); padding:10px;
      text-decoration:none; color:var(--content-fg); font:600 12px var(--font-sans); }
    .pod-thumb img { width:100%; height:140px; object-fit:cover; border-radius:calc(var(--r-field) - 4px); }
    .pod-thumb--empty { color:var(--content-muted); height:170px; justify-content:center; }
    .ai-result__grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(160px, 1fr)); gap:10px 16px; margin:0; }
    .ai-result__grid dt { font:600 11px var(--font-sans); color:var(--content-muted); text-transform:uppercase; letter-spacing:.03em; }
    .ai-result__grid dd { margin:2px 0 0; font:500 13px var(--font-sans); }
    .ai-result__reasons { margin:12px 0 0; padding-left:18px; font:400 13px var(--font-sans); color:var(--content-muted); }
    .df { display:flex; flex-direction:column; gap:16px; }
    .df__bar { display:flex; justify-content:flex-end; gap:10px; }
  `]
})
export class PodReview implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly podService = inject(PodService);

  readonly items = signal<PodVerification[]>([]);
  readonly loading = signal(true);
  readonly selected = signal<PodVerification | null>(null);
  readonly deciding = signal<'approve' | 'reject' | null>(null);

  readonly form: FormGroup = this.fb.group({
    remarks: ['', Validators.maxLength(1000)]
  });

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'POD Review' }]);
    this.load();
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  load(): void {
    this.loading.set(true);
    this.podService.pendingReview().subscribe({
      next: (items) => { this.items.set(items); this.loading.set(false); },
      error: () => { this.items.set([]); this.loading.set(false); }
    });
  }

  select(v: PodVerification): void {
    this.selected.set(v);
    this.form.reset();
  }

  decide(approve: boolean): void {
    const v = this.selected();
    if (!v) return;
    this.deciding.set(approve ? 'approve' : 'reject');
    this.podService.review(v.shipmentId, {
      approve, remarks: this.c('remarks').value?.trim() || null
    }).subscribe({
      next: () => {
        this.deciding.set(null);
        this.notify.success(approve ? 'POD approved.' : 'POD rejected.');
        this.selected.set(null);
        this.load();
      },
      error: (e: HttpErrorResponse) => {
        this.deciding.set(null);
        this.notify.error(e.error?.message ?? 'Could not record the decision.');
      }
    });
  }
}
