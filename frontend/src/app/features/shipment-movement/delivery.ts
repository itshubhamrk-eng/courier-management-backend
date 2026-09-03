import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import jsQR from 'jsqr';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { ShipmentStatusBadge } from '@features/shipment/components/shipment-status-badge';
import { ShipmentService } from '@features/shipment/shipment.service';
import { ShipmentMovementService } from './shipment-movement.service';
import { Shipment, ShipmentStatus } from '@core/models/shipment.model';
import { PinIllustration } from '@shared/components/illustrations/pin-illustration';
import { MasterDataService } from '@features/masters/master-data.service';
import { MASTER_DEFINITIONS } from '@features/masters/master.config';
import { PaymentMode } from '@core/models/master.model';
import { PodService } from '@core/services/pod.service';
import { PodVerification } from '@core/models/pod.model';

type StatusFilter = 'ALL' | 'IN_SCAN' | 'OUT_FOR_DELIVERY' | 'DELIVERED';
const LIST_STATUSES: ShipmentStatus[] = ['IN_SCAN', 'OUT_FOR_DELIVERY', 'DELIVERED'];

/** Delivery — a filterable worklist of this branch's IN_SCAN / OUT_FOR_DELIVERY / DELIVERED
 *  orders (status + text search over tracking/shipment number/receiver), then a capture ->
 *  AI verification -> decision flow for an OUT_FOR_DELIVERY row:
 *
 *  Upload POD (photo required, signature optional, real file picker) -> optionally scan the
 *  label's own QR (device camera, jsQR) -> "Run AI Verification" (PodService.verify, POD Auto
 *  Verification module) -> PASS shows "Complete Delivery" (the existing, unchanged
 *  ShipmentMovementService.deliver — AI never itself marks a shipment DELIVERED), REVIEW
 *  shows a pending state with a manual "Check Review Status" refresh (a POD Review screen
 *  elsewhere approves/rejects it), FAIL shows "Upload New POD" to recapture. The QR scan is
 *  optional at this screen (unlike the photo) — the backend falls back to decoding the QR out
 *  of the uploaded photo itself when no live scan was made; either way it's a real independent
 *  cross-check, not an echo of the already-selected shipment's own trackingNumber/
 *  shipmentNumber (sent regardless, below). See MEMORY/modules/pod-verification.md. */
@Component({
  selector: 'app-delivery',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, MatIconModule, UiCard, UiLoader, UiButton, UiInput, UiSelect, ShipmentStatusBadge, PinIllustration],
  template: `
    <div class="page">
      <header class="page__head" data-tour="delivery-head">
        <div class="page__head-row">
          <app-pin-illustration class="page__head-ill" [size]="52" />
          <div><h1 class="text-h1">Delivery</h1>
          <p class="text-caption">Close a delivery against the consignee.</p></div>
        </div>
        @if (!shipment()) {
          <app-button variant="stroked" icon="refresh" (pressed)="load()">Refresh</app-button>
        }
      </header>

      @if (!myBranchId) {
        <app-card><p class="empty">No branch assigned — ask an admin.</p></app-card>
      } @else if (!shipment()) {
        <app-card>
          <div class="filters">
            <app-input [control]="searchControl" label="Search" placeholder="Tracking / Shipment No. / Receiver" />
            <app-select [control]="statusControl" label="Status" [options]="statusOptions" />
          </div>
        </app-card>

        @if (loading()) {
          <app-loader [minHeight]="120" caption="Loading…" />
        } @else if (!filteredShipments().length) {
          <app-card><p class="empty">Nothing matches at your branch.</p></app-card>
        } @else {
          <app-card>
            <div class="tbl__wrap">
              <table class="tbl">
                <thead>
                  <tr><th>#</th><th>Tracking No.</th><th>Receiver</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  @for (s of filteredShipments(); track s.id; let i = $index) {
                    <tr [class.tbl__row--actionable]="s.status === 'OUT_FOR_DELIVERY'" (click)="selectShipment(s)">
                      <td>{{ i + 1 }}</td>
                      <td>{{ s.trackingNumber }}</td>
                      <td>{{ s.receiverName }}</td>
                      <td><app-shipment-status-badge [status]="s.status" /></td>
                      <td class="tbl--right">
                        @if (s.status === 'OUT_FOR_DELIVERY') {
                          <app-button icon="task_alt" (pressed)="selectShipment(s)">Deliver</app-button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </app-card>
        }
      }

      @if (shipment(); as s) {
        <app-card>
          <div class="sh">
            <div><strong>{{ s.trackingNumber }}</strong>
              <span class="text-caption">{{ s.senderName }} → {{ s.receiverName }}, {{ s.receiverContact }}</span></div>
            <app-button variant="stroked" icon="close" (pressed)="reset()">Back to List</app-button>
          </div>
          @if (paymentMode(); as pm) {
            <div class="pay">
              @if (pm.collectAtBooking) {
                <span class="pay__badge pay__badge--paid"><mat-icon>check_circle</mat-icon>{{ pm.name }}</span>
              } @else {
                <span class="pay__badge">{{ pm.name }}</span>
                @if (pm.collectAtDelivery && s.netAmount != null) {
                  <span class="pay__amount">Collect ₹{{ s.netAmount | number: '1.2-2' }}</span>
                }
              }
            </div>
          }
        </app-card>

        <app-card title="Delivery Form">
          <form [formGroup]="form" (ngSubmit)="onEnter()" class="df">
            <div class="grid2">
              <app-input [control]="c('receiverName')" label="Receiver Name" [required]="true" [maxLength]="150" />
              <app-input [control]="c('otp')" label="OTP" placeholder="Optional" [maxLength]="10" />
            </div>
            <app-input [control]="c('remarks')" label="Remarks" placeholder="Optional" [maxLength]="500" />

            @if (!verification() || verification()!.verificationStatus === 'FAIL') {
              <div class="pod-row">
                <div class="pod">
                  <span class="pod__label">Photo <em>required</em></span>
                  <button type="button" class="pod__btn" (click)="photoFile.click()">
                    <mat-icon>photo_camera</mat-icon> {{ photo() ? photo()!.name : 'Choose file' }}
                  </button>
                  <input #photoFile type="file" accept="image/*" hidden (change)="onFile($event, 'photo')" />
                </div>
                <div class="pod">
                  <span class="pod__label">Signature <em>optional</em></span>
                  <button type="button" class="pod__btn" (click)="signatureFile.click()">
                    <mat-icon>draw</mat-icon> {{ signature() ? signature()!.name : 'Choose file' }}
                  </button>
                  <input #signatureFile type="file" accept="image/*" hidden (change)="onFile($event, 'signature')" />
                </div>
                <div class="pod">
                  <span class="pod__label">Label QR <em>optional</em></span>
                  @if (qrScanValue()) {
                    <div class="qr-chip">
                      <mat-icon>qr_code_2</mat-icon><span>{{ qrScanValue() }}</span>
                      <button type="button" class="qr-chip__clear" (click)="clearQrScan()">
                        <mat-icon>close</mat-icon>
                      </button>
                    </div>
                  } @else {
                    <button type="button" class="pod__btn" (click)="scanningQr() ? stopQrScan() : startQrScan()">
                      <mat-icon>{{ scanningQr() ? 'close' : 'qr_code_scanner' }}</mat-icon>
                      {{ scanningQr() ? 'Cancel Scan' : 'Scan QR' }}
                    </button>
                  }
                  <video #qrVideo playsinline muted class="qr-video" [class.qr-video--active]="scanningQr()"></video>
                </div>
              </div>
              <div class="df__bar">
                <app-button icon="smart_toy" [loading]="verifying()" [disabled]="!photo()" (pressed)="runVerification()">
                  Run AI Verification
                </app-button>
              </div>
            }

            @if (verifying()) {
              <div class="ai-status ai-status--pending">
                <mat-icon>hourglass_top</mat-icon>
                <span>AI Verification — Processing…</span>
              </div>
            }

            @if (verification(); as v) {
              <div class="ai-result" [class.ai-result--pass]="v.verificationStatus === 'PASS'"
                   [class.ai-result--review]="v.verificationStatus === 'REVIEW'"
                   [class.ai-result--fail]="v.verificationStatus === 'FAIL'">
                <div class="ai-result__head">
                  <mat-icon>{{ statusIcon(v) }}</mat-icon>
                  <strong>{{ statusLabel(v) }}</strong>
                  <span class="ai-result__score">{{ v.verificationScore }}/100</span>
                </div>
                <dl class="ai-result__grid">
                  <div><dt>Receiver</dt><dd>{{ v.detectedReceiverName || '—' }}</dd></div>
                  <div><dt>AWB</dt><dd>{{ v.detectedAwb || '—' }}</dd></div>
                  <div><dt>Date</dt><dd>{{ v.detectedDate || '—' }}</dd></div>
                  <div><dt>Signature</dt><dd>{{ v.signatureDetected ? 'Detected' : 'Not detected' }}</dd></div>
                  <div><dt>Image Quality</dt><dd>{{ v.imageQuality || '—' }}</dd></div>
                </dl>
                @if (v.verificationReasons.length) {
                  <ul class="ai-result__reasons">
                    @for (r of v.verificationReasons; track r) { <li>{{ r }}</li> }
                  </ul>
                }

                <div class="df__bar">
                  @if (v.verificationStatus === 'PASS') {
                    <app-button icon="task_alt" [loading]="delivering()" (pressed)="deliver()">Complete Delivery</app-button>
                  } @else if (v.verificationStatus === 'REVIEW') {
                    <app-button variant="stroked" icon="refresh" [loading]="checkingReview()" (pressed)="checkReviewStatus()">
                      Check Review Status
                    </app-button>
                    <span class="ai-result__hint">Sent for manual review — a supervisor must approve or reject it.</span>
                  } @else {
                    <app-button variant="stroked" icon="upload" (pressed)="uploadNewPod()">Upload New POD</app-button>
                  }
                </div>
              </div>
            }
          </form>
        </app-card>
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .filters { display:flex; gap:16px; flex-wrap:wrap; }
    .filters app-input { flex:1; min-width:220px; }
    .filters app-select { min-width:200px; }
    .df { display:flex; flex-direction:column; gap:16px; }
    .df__bar { display:flex; justify-content:flex-end; align-items:center; gap:10px; }
    .grid2 { display:grid; grid-template-columns:1fr 1fr; gap:16px 20px; }
    .pod-row { display:flex; gap:20px; flex-wrap:wrap; }
    .pod { display:flex; flex-direction:column; gap:8px; }
    .pod__label { font:600 12px var(--font-sans); color:var(--content-muted); }
    .pod__label em { font-style:normal; color:var(--content-muted); opacity:.7; }
    .pod__btn { display:inline-flex; align-items:center; gap:6px; align-self:flex-start;
      border:1px solid var(--surface-border); background:var(--surface); border-radius:var(--r-field);
      padding:6px 12px; font:600 12px var(--font-sans); color:var(--content-fg); cursor:pointer; }
    .pod__btn:disabled { opacity:.6; cursor:default; }
    .pod__btn mat-icon { font-size:16px; width:16px; height:16px; }
    .qr-chip { display:inline-flex; align-items:center; gap:6px; align-self:flex-start;
      border:1px solid var(--success-fg, #1a7f3c); background:var(--success-bg, #e6f7ec); border-radius:var(--r-field);
      padding:6px 10px; font:600 12px var(--font-sans); color:var(--content-fg); }
    .qr-chip mat-icon { font-size:16px; width:16px; height:16px; color:var(--success-fg, #1a7f3c); }
    .qr-chip__clear { display:inline-flex; border:0; background:none; cursor:pointer; padding:0; color:var(--content-muted); }
    .qr-chip__clear mat-icon { font-size:15px; width:15px; height:15px; }
    .qr-video { display:none; width:220px; height:165px; border-radius:var(--r-field); border:1px solid var(--surface-border); object-fit:cover; background:#000; }
    .qr-video--active { display:block; margin-top:8px; }
    .sh { display:flex; justify-content:space-between; align-items:center; gap:12px; }
    .sh strong { display:block; font:600 15px var(--font-sans); }
    .pay { display:flex; align-items:center; gap:10px; margin-top:12px; flex-wrap:wrap; }
    .pay__badge { display:inline-flex; align-items:center; gap:4px; padding:4px 10px; border-radius:999px;
      font:600 12px var(--font-sans); background:var(--surface-muted); color:var(--content-fg); }
    .pay__badge--paid { background:var(--success-bg, #e6f7ec); color:var(--success-fg, #1a7f3c); }
    .pay__badge--paid mat-icon { font-size:15px; width:15px; height:15px; }
    .pay__amount { font:600 13px var(--font-sans); color:var(--content-fg); }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .tbl__wrap { overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl--right { text-align:right; }
    .tbl__row--actionable { cursor:pointer; }
    .tbl__row--actionable:hover { background:var(--surface-muted); }
    .ai-status { display:flex; align-items:center; gap:8px; padding:10px 14px; border-radius:var(--r-field);
      background:var(--surface-muted); font:600 13px var(--font-sans); color:var(--content-muted); }
    .ai-result { display:flex; flex-direction:column; gap:12px; padding:14px 16px; border-radius:var(--r-field);
      border:1px solid var(--surface-border); }
    .ai-result--pass { border-color:var(--success-fg, #1a7f3c); background:var(--success-bg, #e6f7ec); }
    .ai-result--review { border-color:#b58900; background:#fff8e1; }
    .ai-result--fail { border-color:#c0392b; background:#fdecea; }
    .ai-result__head { display:flex; align-items:center; gap:8px; font:700 14px var(--font-sans); }
    .ai-result__score { margin-left:auto; font:600 13px var(--font-sans); color:var(--content-muted); }
    .ai-result__grid { display:grid; grid-template-columns:repeat(auto-fit, minmax(140px, 1fr)); gap:10px 16px; margin:0; }
    .ai-result__grid dt { font:600 11px var(--font-sans); color:var(--content-muted); text-transform:uppercase; letter-spacing:.03em; }
    .ai-result__grid dd { margin:2px 0 0; font:500 13px var(--font-sans); }
    .ai-result__reasons { margin:0; padding-left:18px; font:400 13px var(--font-sans); color:var(--content-muted); }
    .ai-result__hint { font:400 12px var(--font-sans); color:var(--content-muted); }
    @media (max-width:760px){ .grid2 { grid-template-columns:1fr; } }
  `]
})
export class Delivery implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly shipmentService = inject(ShipmentService);
  private readonly movementService = inject(ShipmentMovementService);
  private readonly podService = inject(PodService);
  private readonly masters = inject(MasterDataService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;

  readonly shipment = signal<Shipment | null>(null);
  readonly paymentMode = signal<PaymentMode | null>(null);
  readonly delivering = signal(false);
  readonly verifying = signal(false);
  readonly checkingReview = signal(false);
  readonly verification = signal<PodVerification | null>(null);
  readonly photo = signal<File | null>(null);
  readonly signature = signal<File | null>(null);
  readonly qrScanValue = signal<string | null>(null);
  readonly scanningQr = signal(false);
  private readonly qrVideo = viewChild<ElementRef<HTMLVideoElement>>('qrVideo');
  private qrStream: MediaStream | null = null;
  private qrRafId: number | null = null;

  readonly shipments = signal<Shipment[]>([]);
  readonly loading = signal(true);
  readonly searchControl = new FormControl('');
  readonly statusControl = new FormControl<StatusFilter>('ALL');
  readonly statusOptions: SelectOption[] = [
    { value: 'ALL', label: 'In Scan + DRS + Delivered' },
    { value: 'IN_SCAN', label: 'In Scan' },
    { value: 'OUT_FOR_DELIVERY', label: 'DRS' },
    { value: 'DELIVERED', label: 'Delivered' }
  ];
  protected readonly filteredShipments = computed(() => {
    const q = (this.searchControl.value ?? '').trim().toLowerCase();
    if (!q) return this.shipments();
    return this.shipments().filter((s) =>
      s.trackingNumber.toLowerCase().includes(q) ||
      s.shipmentNumber.toLowerCase().includes(q) ||
      s.receiverName.toLowerCase().includes(q));
  });

  readonly form: FormGroup = this.fb.group({
    receiverName: ['', [Validators.required, Validators.maxLength(150)]],
    remarks: ['', Validators.maxLength(500)],
    otp: ['', Validators.maxLength(10)]
  });

  private pendingTrackingNumber: string | null = null;

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'Delivery' }]);
    this.statusControl.valueChanges.subscribe(() => this.load());
    this.pendingTrackingNumber = this.route.snapshot.queryParamMap.get('trackingNumber');
    if (this.pendingTrackingNumber) this.searchControl.setValue(this.pendingTrackingNumber);
    this.load();
    this.destroyRef.onDestroy(() => this.stopQrScan());
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }

  /** Enter-key submit inside the form — routes to whichever action is currently valid,
   *  same as clicking the one visible primary button would. */
  protected onEnter(): void {
    const status = this.verification()?.verificationStatus;
    if (status === 'PASS') this.deliver();
    else if (!status || status === 'FAIL') this.runVerification();
  }

  protected statusIcon(v: PodVerification): string {
    return v.verificationStatus === 'PASS' ? 'verified' : v.verificationStatus === 'REVIEW' ? 'warning' : 'cancel';
  }

  protected statusLabel(v: PodVerification): string {
    return v.verificationStatus === 'PASS' ? 'Verified'
      : v.verificationStatus === 'REVIEW' ? 'Manual Review Required' : 'Verification Failed';
  }

  load(): void {
    if (!this.myBranchId) return;
    this.loading.set(true);
    const filter = this.statusControl.value ?? 'ALL';
    const statuses = filter === 'ALL' ? LIST_STATUSES : [filter as ShipmentStatus];
    forkJoin(statuses.map((status) =>
      this.shipmentService.list({ page: 0, size: 100, deliveryBranchId: this.myBranchId!, status })
    )).subscribe({
      next: (pages) => {
        const list = pages.flatMap((p) => p.content);
        this.shipments.set(list);
        this.loading.set(false);
        if (this.pendingTrackingNumber) {
          const match = list.find((s) => s.trackingNumber === this.pendingTrackingNumber);
          this.pendingTrackingNumber = null;
          if (match) this.selectShipment(match);
        }
      },
      error: () => { this.shipments.set([]); this.loading.set(false); }
    });
  }

  selectShipment(s: Shipment): void {
    if (s.status !== 'OUT_FOR_DELIVERY') {
      this.notify.error(`Shipment is ${s.status}, not OUT_FOR_DELIVERY.`);
      return;
    }
    this.shipment.set(s);
    this.paymentMode.set(null);
    this.verification.set(null);
    this.photo.set(null);
    this.signature.set(null);
    this.stopQrScan();
    this.qrScanValue.set(null);
    this.form.patchValue({ receiverName: s.receiverName });
    this.masters.get(MASTER_DEFINITIONS['payment-modes'], s.paymentModeId)
      .subscribe((pm) => this.paymentMode.set(pm as PaymentMode));
    // A prior REVIEW/PASS may already exist (e.g. the delivery user navigated away and
    // back) — hydrate it silently so the AI step isn't repeated unnecessarily.
    this.podService.getLatest(s.id).subscribe({
      next: (v) => this.verification.set(v),
      error: () => this.verification.set(null)
    });
  }

  reset(): void {
    this.shipment.set(null);
    this.paymentMode.set(null);
    this.verification.set(null);
    this.photo.set(null);
    this.signature.set(null);
    this.stopQrScan();
    this.qrScanValue.set(null);
    this.form.reset();
    this.load();
  }

  onFile(event: Event, kind: 'photo' | 'signature'): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (kind === 'photo') this.photo.set(file); else this.signature.set(file);
  }

  uploadNewPod(): void {
    this.verification.set(null);
    this.photo.set(null);
    this.signature.set(null);
    this.qrScanValue.set(null);
  }

  runVerification(): void {
    const shipment = this.shipment();
    const photo = this.photo();
    if (!shipment || !photo) { this.notify.error('A delivery photo is required.'); return; }
    if (this.c('receiverName').invalid) { this.form.markAllAsTouched(); return; }

    this.verifying.set(true);
    this.podService.verify(shipment.id, {
      photo, signature: this.signature(),
      receiverName: this.c('receiverName').value.trim(),
      awbNumber: shipment.trackingNumber, shipmentNumber: shipment.shipmentNumber,
      deliveryDateTime: new Date().toISOString(),
      qrScanValue: this.qrScanValue()
    }).subscribe({
      next: (v) => { this.verifying.set(false); this.verification.set(v); },
      error: (e: HttpErrorResponse) => {
        this.verifying.set(false);
        this.notify.error(e.error?.message ?? 'POD verification failed.');
      }
    });
  }

  /** Opens the device camera and starts scanning frames for a QR code — decodes the label
   *  physically stuck to the parcel, an independent identifier the operator can't accidentally
   *  self-echo (unlike `awbNumber`/`shipmentNumber` above, which just restate the already-
   *  selected shipment). Stops itself the moment a QR decodes; `stopQrScan()` cancels early. */
  async startQrScan(): Promise<void> {
    if (this.scanningQr()) return;
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    } catch {
      this.notify.error('Camera access denied or unavailable.');
      return;
    }
    this.qrStream = stream;
    this.scanningQr.set(true);
    const video = this.qrVideo()?.nativeElement;
    if (!video) { this.stopQrScan(); return; }
    video.srcObject = stream;
    void video.play();
    this.scanFrames(video);
  }

  stopQrScan(): void {
    this.scanningQr.set(false);
    if (this.qrRafId != null) { cancelAnimationFrame(this.qrRafId); this.qrRafId = null; }
    this.qrStream?.getTracks().forEach((track) => track.stop());
    this.qrStream = null;
    const video = this.qrVideo()?.nativeElement;
    if (video) video.srcObject = null;
  }

  clearQrScan(): void {
    this.qrScanValue.set(null);
  }

  private scanFrames(video: HTMLVideoElement): void {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    const tick = (): void => {
      if (!this.scanningQr()) return;
      if (ctx && video.readyState === video.HAVE_ENOUGH_DATA) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        const frame = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const decoded = jsQR(frame.data, frame.width, frame.height);
        if (decoded?.data) {
          this.qrScanValue.set(decoded.data);
          this.notify.success('QR code scanned.');
          this.stopQrScan();
          return;
        }
      }
      this.qrRafId = requestAnimationFrame(tick);
    };
    this.qrRafId = requestAnimationFrame(tick);
  }

  checkReviewStatus(): void {
    const shipment = this.shipment();
    if (!shipment) return;
    this.checkingReview.set(true);
    this.podService.getLatest(shipment.id).subscribe({
      next: (v) => {
        this.checkingReview.set(false);
        this.verification.set(v);
        if (v.verificationStatus === 'REVIEW') this.notify.error('Still awaiting manual review.');
      },
      error: () => this.checkingReview.set(false)
    });
  }

  deliver(): void {
    const shipment = this.shipment();
    if (!shipment || this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.delivering.set(true);
    this.movementService.deliver({
      shipmentId: shipment.id, receiverName: v.receiverName.trim(),
      remarks: v.remarks?.trim() || null, otp: v.otp?.trim() || null,
      // POD was already captured and stored by PodService.verify — nothing left to attach.
      signatureUrl: null, photoUrl: null
    }).subscribe({
      next: () => {
        this.delivering.set(false);
        this.notify.success(`Shipment ${shipment.trackingNumber} delivered.`);
        this.reset();
      },
      error: (e: HttpErrorResponse) => { this.delivering.set(false); this.notify.error(e.error?.message ?? 'Could not close the delivery.'); }
    });
  }
}
