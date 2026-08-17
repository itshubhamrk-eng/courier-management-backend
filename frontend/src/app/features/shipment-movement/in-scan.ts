import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { AuthService } from '@core/auth/auth.service';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiInput } from '@shared/components/ui-input/ui-input';
import { UiLoader } from '@shared/components/ui-loader/ui-loader';
import { Manifest, MovementOutcome, Shipment } from '@core/models/shipment.model';
import { ManifestService } from '@features/manifest/manifest.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { ShipmentService } from '@features/shipment/shipment.service';
import { ShipmentMovementService } from './shipment-movement.service';
import { ManifestCard } from './components/manifest-card';
import { ScannerIllustration } from '@shared/components/illustrations/scanner-illustration';
import { StatusBadge } from '@shared/components/status-badge/status-badge';

/** In Scan — Scan (by Manifest No. / Shipment No. / AWB(tracking) No., top of page — the
 *  operator's primary action) then the Pending Manifests worklist (DISPATCHED manifests
 *  bound for my branch, awaiting receipt). Receiving branch defaults to the signed-in
 *  user's own branch, the same "no picker, my own branch" pattern shipment-create uses
 *  for Booking Branch. What happens next to a received (IN_SCAN) shipment lives on its
 *  own page — see Pending Delivery.
 *
 *  Picking a manifest (THC action button, or typing its number into Scan) no longer
 *  bulk-receives every shipment on it blind — on direct request ("if 10 shipment in
 *  single THS ... able to uncheck if some shipment not physicaly available in THC"), it
 *  opens a checklist of that manifest's non-`IN_SCAN` shipments, every row checked by
 *  default; unchecking one just drops it from `selectedTrackingNumbers` client-side, and
 *  only the checked trackingNumbers go into the `inScan()` POST. Scanning a single
 *  Shipment No./AWB directly (`runScan`) is unaffected — that flow already only ever
 *  submits the one number the operator physically scanned. */
@Component({
  selector: 'app-in-scan',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatIconModule, UiCard, UiLoader, UiButton, UiInput, ManifestCard, ScannerIllustration, StatusBadge],
  template: `
    <div class="page">
      <header class="page__head" data-tour="in-scan-head">
        <div class="page__head-row">
          <app-scanner-illustration class="page__head-ill" [size]="52" />
          <div><h1 class="text-h1">In Scan</h1>
          <p class="text-caption">Receive shipments dispatched to {{ myBranchLabel() }}.</p></div>
        </div>
        @if (myBranchId) {
          <app-button variant="stroked" icon="refresh" (pressed)="loadPendingManifests()">Refresh</app-button>
        }
      </header>

      @if (!myBranchId) {
        <app-card><p class="empty">No branch assigned — ask an admin.</p></app-card>
      } @else if (receivingManifest(); as rm) {
        <app-card>
          <div class="mh">
            <div><strong>{{ rm.manifestNumber }}</strong>
              <span class="text-caption">Uncheck any shipment not physically in this THC before receiving.</span></div>
            <app-button variant="stroked" icon="close" (pressed)="cancelReceive()">Cancel</app-button>
          </div>
        </app-card>

        <app-card title="Shipments on this Manifest">
          @if (loadingReceivingShipments()) {
            <app-loader [minHeight]="100" caption="Loading…" />
          } @else if (!receivingManifestShipments().length) {
            <p class="empty">Nothing to receive — every shipment on this manifest is already In Scan.</p>
          } @else {
            <div class="tbl__wrap">
              <table class="tbl">
                <thead>
                  <tr><th></th><th>#</th><th>Tracking No.</th><th>Sender → Receiver</th><th class="tbl--right">Weight</th><th>Status</th></tr>
                </thead>
                <tbody>
                  @for (s of receivingManifestShipments(); track s.id; let i = $index) {
                    <tr>
                      <td><input type="checkbox" [checked]="isChecked(s.trackingNumber)" (change)="toggleReceiveShipment(s.trackingNumber)" /></td>
                      <td>{{ i + 1 }}</td>
                      <td>{{ s.trackingNumber }}</td>
                      <td>{{ s.senderName }} → {{ s.receiverName }}</td>
                      <td class="tbl--right">{{ s.chargeableWeight }} kg</td>
                      <td><app-status-badge value="PENDING" label="Pending" tone="warning" /></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="df__bar">
              <app-button icon="qr_code_scanner" [loading]="scanning()" [disabled]="!selectedCount()" (pressed)="confirmReceive()">
                Receive{{ selectedCount() ? ' (' + selectedCount() + ')' : '' }}
              </app-button>
            </div>
          }
        </app-card>
      } @else {
        <app-card title="Scan" subtitle="Manifest No., Shipment No., or AWB (Tracking) No.">
          <div class="row">
            <app-input [control]="scanControl" label="Manifest / Shipment / AWB No." placeholder="MFT-… / SHP-… / AWB…" (keydown.enter)="scanOne()" />
            <app-button icon="qr_code_scanner" [loading]="scanning()" (pressed)="scanOne()">Scan</app-button>
          </div>
        </app-card>

        @if (outcomes().length) {
          <app-card [title]="'Result (' + successCount() + ' of ' + outcomes().length + ' received)'">
            <div class="ol">
              @for (o of outcomes(); track o.reference) {
                <div class="ol__row" [class.ol__row--fail]="!o.success">
                  <mat-icon>{{ o.success ? 'check_circle' : 'error' }}</mat-icon>
                  <span>{{ o.reference }}</span>
                  @if (o.message) { <span class="text-caption">— {{ o.message }}</span> }
                </div>
              }
            </div>
          </app-card>
        }

        <h2 class="text-h2 section-title">Pending Manifests</h2>
        @if (loadingManifests()) {
          <app-loader [minHeight]="120" caption="Loading…" />
        } @else if (!pendingManifests().length) {
          <app-card><p class="empty">No manifest is pending receipt at your branch.</p></app-card>
        } @else {
          @for (m of pendingManifests(); track m.id) {
            <app-manifest-card [manifest]="m" [branchNames]="branchNames()"
              [showInScanAction]="true" [inScanLoading]="receivingId() === m.id"
              (inScan)="receiveManifest($event)" />
          }
        }
      }
    </div>
  `,
  styles: [`
    .page__head { display:flex; justify-content:space-between; align-items:flex-start; }
    .section-title { margin:8px 0 0; }
    .row { display:flex; gap:12px; align-items:flex-end; }
    .row app-input { flex:1; }
    .ol { display:flex; flex-direction:column; gap:6px; }
    .ol__row { display:flex; align-items:center; gap:8px; font:400 13px var(--font-sans); color:var(--success-600, #16a34a); }
    .ol__row--fail { color:var(--danger-600, #dc2626); }
    .empty { font:400 14px var(--font-sans); color:var(--content-muted); text-align:center; padding:20px; }
    .mh { display:flex; justify-content:space-between; align-items:center; gap:12px; }
    .mh strong { display:block; font:600 15px var(--font-sans); }
    .tbl__wrap { overflow-x:auto; border:1px solid var(--surface-border); border-radius:var(--r-field); }
    .tbl { width:100%; border-collapse:collapse; font:400 13px var(--font-sans); }
    .tbl th { text-align:left; padding:10px 14px; background:var(--surface-muted); color:var(--content-muted); font:600 11px var(--font-sans); text-transform:uppercase; letter-spacing:.03em; white-space:nowrap; }
    .tbl td { padding:10px 14px; border-top:1px solid var(--surface-border); white-space:nowrap; }
    .tbl--right { text-align:right; }
    .df__bar { display:flex; justify-content:flex-end; margin-top:16px; }
  `]
})
export class InScan implements OnInit {
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly movementService = inject(ShipmentMovementService);
  private readonly manifestService = inject(ManifestService);
  private readonly masterData = inject(MasterDataService);
  private readonly shipmentService = inject(ShipmentService);

  protected readonly myBranchId = this.auth.user()?.branchId ?? null;
  protected readonly myBranchLabel = computed(() => 'your branch');

  readonly scanning = signal(false);
  readonly outcomes = signal<MovementOutcome[]>([]);
  readonly successCount = computed(() => this.outcomes().filter((o) => o.success).length);

  readonly pendingManifests = signal<Manifest[]>([]);
  readonly loadingManifests = signal(true);
  readonly branchNames = signal<Map<string, string>>(new Map());
  /** Set only while that manifest's shipment list is being fetched for the checklist. */
  readonly receivingId = signal<string | null>(null);

  readonly receivingManifest = signal<Manifest | null>(null);
  readonly receivingManifestShipments = signal<Shipment[]>([]);
  readonly loadingReceivingShipments = signal(false);
  readonly selectedTrackingNumbers = signal<Set<string>>(new Set());
  protected readonly selectedCount = computed(() => this.selectedTrackingNumbers().size);

  readonly scanControl = new FormControl('');

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Operations' }, { label: 'In Scan' }]);
    if (!this.myBranchId) return;
    this.masterData.branchDirectory().subscribe((list) =>
      this.branchNames.set(new Map(list.map((b) => [b.id, `${b.branchName} (${b.branchCode})`]))));
    this.loadPendingManifests();
  }

  loadPendingManifests(): void {
    if (!this.myBranchId) return;
    this.loadingManifests.set(true);
    this.manifestService.list({
      page: 0, size: 50, status: 'DISPATCHED', deliveryBranchId: this.myBranchId, sort: 'dispatchedAt,desc'
    }).subscribe({
      next: (p) => { this.pendingManifests.set(p.content); this.loadingManifests.set(false); },
      error: () => { this.pendingManifests.set([]); this.loadingManifests.set(false); }
    });
  }

  scanOne(): void {
    const raw = this.scanControl.value?.trim();
    if (!raw) return;
    this.scanControl.setValue('');
    this.resolve(raw);
  }

  /** One box, three identifiers: try Manifest No. first, then fall back to Shipment No. /
   *  AWB (Tracking) No. — `ShipmentService.search` already matches either column. */
  private resolve(raw: string): void {
    this.scanning.set(true);
    this.manifestService.list({ page: 0, size: 5, search: raw }).subscribe({
      next: (mp) => {
        const manifest = mp.content.find((m) => m.manifestNumber.toLowerCase() === raw.toLowerCase());
        if (manifest) { this.scanning.set(false); this.receiveManifest(manifest); return; }
        this.resolveShipment(raw);
      },
      error: () => this.resolveShipment(raw)
    });
  }

  private resolveShipment(raw: string): void {
    this.shipmentService.list({ page: 0, size: 5, search: raw }).subscribe({
      next: (sp) => {
        const shipment = sp.content.find((s) =>
          s.trackingNumber.toLowerCase() === raw.toLowerCase() || s.shipmentNumber.toLowerCase() === raw.toLowerCase());
        this.runScan([shipment?.trackingNumber ?? raw]);
      },
      error: () => this.runScan([raw])
    });
  }

  /** Opens the checklist — does not receive anything on its own. */
  receiveManifest(m: Manifest): void {
    if (!this.myBranchId || this.receivingId()) return;
    this.receivingId.set(m.id);
    this.manifestService.shipments(m.id).subscribe({
      next: (shipments) => {
        const pending = shipments.filter((s) => s.status !== 'IN_SCAN');
        this.receivingManifestShipments.set(pending);
        this.selectedTrackingNumbers.set(new Set(pending.map((s) => s.trackingNumber)));
        this.receivingManifest.set(m);
        this.receivingId.set(null);
      },
      error: () => { this.receivingId.set(null); this.notify.error('Could not load manifest shipments.'); }
    });
  }

  protected isChecked(trackingNumber: string): boolean {
    return this.selectedTrackingNumbers().has(trackingNumber);
  }

  protected toggleReceiveShipment(trackingNumber: string): void {
    this.selectedTrackingNumbers.update((current) => {
      const next = new Set(current);
      if (next.has(trackingNumber)) next.delete(trackingNumber); else next.add(trackingNumber);
      return next;
    });
  }

  protected cancelReceive(): void {
    this.receivingManifest.set(null);
    this.receivingManifestShipments.set([]);
    this.selectedTrackingNumbers.set(new Set());
  }

  /** Only the checked trackingNumbers go into the bulk `inScan` call — an unchecked
   *  shipment (short-received, not physically on the vehicle) is simply left out, not
   *  removed from the manifest itself. The unchecked complement goes along as
   *  `missingTrackingNumbers` so the backend can auto-raise a Support ticket for the
   *  company the instant a THC comes up short — the operator's own explicit answer,
   *  not a guess. */
  protected confirmReceive(): void {
    const selected = this.selectedTrackingNumbers();
    const trackingNumbers = [...selected];
    if (!this.myBranchId || !trackingNumbers.length) return;
    const missingTrackingNumbers = this.receivingManifestShipments()
      .map((s) => s.trackingNumber)
      .filter((t) => !selected.has(t));
    const manifestNumber = this.receivingManifest()?.manifestNumber ?? null;
    this.scanning.set(true);
    this.movementService.inScan({
      receivingBranchId: this.myBranchId, trackingNumbers, manifestNumber, missingTrackingNumbers
    }).subscribe({
      next: (r) => {
        this.scanning.set(false);
        this.outcomes.set(r.results);
        if (r.failureCount) this.notify.error(`${r.failureCount} of ${r.results.length} failed to receive.`);
        else this.notify.success(`${r.successCount} shipment(s) received.`);
        if (r.shortageTicketNumber) {
          this.notify.error(`${missingTrackingNumbers.length} shipment(s) not received — ticket ${r.shortageTicketNumber} raised for the company.`);
        }
        this.cancelReceive();
        if (r.successCount) this.loadPendingManifests();
      },
      error: (e: HttpErrorResponse) => { this.scanning.set(false); this.notify.error(e.error?.message ?? 'Scan failed.'); }
    });
  }

  private runScan(trackingNumbers: string[]): void {
    if (!this.myBranchId) return;
    this.scanning.set(true);
    this.movementService.inScan({ receivingBranchId: this.myBranchId, trackingNumbers }).subscribe({
      next: (r) => {
        this.scanning.set(false);
        this.outcomes.set(r.results);
        if (r.failureCount) this.notify.error(`${r.failureCount} of ${r.results.length} failed to receive.`);
        else this.notify.success(`${r.successCount} shipment(s) received.`);
        if (r.successCount) this.loadPendingManifests();
      },
      error: (e: HttpErrorResponse) => { this.scanning.set(false); this.notify.error(e.error?.message ?? 'Scan failed.'); }
    });
  }
}
