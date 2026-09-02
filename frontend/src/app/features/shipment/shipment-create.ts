import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AbstractControl, FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, filter, merge, of, switchMap } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { BreadcrumbService } from '@core/services/breadcrumb.service';
import { NotificationService } from '@core/services/notification.service';
import { MasterDataService } from '@features/masters/master-data.service';
import { CustomerService } from '@features/customer/customer.service';
import { SettingsService } from '@features/settings/settings.service';
import { UiSelect, SelectOption } from '@shared/components/ui-select/ui-select';
import { UiAutocomplete } from '@shared/components/ui-autocomplete/ui-autocomplete';
import { UiButton } from '@shared/components/ui-button/ui-button';
import { UiCard } from '@shared/components/ui-card/ui-card';
import { Customer } from '@core/models/customer.model';
import {
  ShipmentItemRequest, CreateShipmentRequest, PricingResponse
} from '@core/models/shipment.model';
import { ItemEntryGrid } from './components/item-entry-grid';
import { ChargeSummary } from './components/charge-summary';
import { VoiceMicButton } from './components/voice-mic-button';
import { ShipmentService } from './shipment.service';
import { EwayBillService } from './eway-bill.service';
import { printConsignmentCopies } from './consignment-print.util';
import { parseVoiceBooking } from './voice-booking.util';

/** `yyyy-MM-dd` in the local timezone — a native `<input type="date">` value, and what
 *  `bookingDate` defaults to on the server if omitted; setting it explicitly here just
 *  makes today's date visible without waiting on a round trip. */
function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type PriceOutcome = { ok: true; data: PricingResponse } | { ok: false; message: string | null };

/** A native `<input type="date">` value (`yyyy-MM-dd`) has no time-of-day; the backend's
 *  `validFrom`/`validUntil` are `Instant`, so a bare date is widened to the start/end of
 *  that day in UTC. */
function toInstantStart(date: string | null | undefined): string | null {
  return date ? `${date}T00:00:00Z` : null;
}
function toInstantEnd(date: string | null | undefined): string | null {
  return date ? `${date}T23:59:59Z` : null;
}

/**
 * Create Shipment — a single page, not a step wizard: Booking Details, Parties,
 * and the Item grid on the left, a sticky live "Booking Summary"
 * sidebar on the right that prices the booking as soon as every required field is
 * filled in (debounced) — the layout the user asked to book from directly, adapted
 * from a supplied reference mockup onto the app's own design system.
 *
 * <p>Consignor/Consignee are plain typed fields (name/address/contact number) — no
 * Customer module lookup, matching the reference screen's own Parties block. Pickup/
 * delivery pincode are typed alongside the branches, feeding the Pricing Engine
 * directly, since there is no longer an address record to resolve one from.
 */
@Component({
  selector: 'app-shipment-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, ReactiveFormsModule, MatIconModule, UiSelect, UiAutocomplete, UiButton, UiCard, ItemEntryGrid, ChargeSummary, VoiceMicButton],
  template: `
    <div class="page">
      <header class="page__head" data-tour="booking-head">
        <div><h1 class="text-h1">New Shipment</h1><p class="text-caption">Fill in the booking — the summary prices it as you go, or speak it.</p></div>
        <div class="page__head-actions">
          <app-voice-mic-button (transcriptReady)="onVoiceTranscript($event)" (error)="onVoiceError($event)" />
          <app-button variant="stroked" (pressed)="cancel()">Cancel</app-button>
        </div>
      </header>
      @if (voiceSummary(); as vs) {
        <p class="voice-banner">{{ vs }}</p>
      }

      <div class="lr">
        <div class="lr__main">
          <app-card title="Booking Details" [subtitle]="'Booking from ' + myBranchLabel()">
            <div class="grid3">
              <label class="fld"><span class="fld__l">Booking Date</span>
                <input class="fld__i" type="date" [formControl]="c('bookingDate')" /></label>
              <app-autocomplete [control]="c('deliveryBranchId')" label="Delivery Branch" [options]="branchOptions()" placeholder="Search delivery branch…" />
              <app-select [control]="c('serviceTypeId')" label="Service Type" [options]="serviceTypeOptions()" placeholder="Select a service type" />
              <label class="fld"><span class="fld__l">Shipment No. (optional)</span>
                <input class="fld__i" [formControl]="c('manualShipmentNumber')" placeholder="Leave blank to auto-generate" maxlength="30" /></label>
            </div>
            <div class="spacer"></div>
            <label class="chk">
              <input type="checkbox" [formControl]="c('crossing')" />
              <span>Route through a crossing branch/hub</span>
            </label>
            @if (c('crossing').value) {
              <div class="crossing-hops">
                @for (ctrl of crossingBranchArray.controls; track $index) {
                  <div class="crossing-hop">
                    <app-autocomplete [control]="asControl(ctrl)" [label]="'Crossing Branch ' + ($index + 1)" [options]="crossingBranchOptions()" placeholder="Search crossing branch…" />
                    @if (crossingBranchArray.length > 1) {
                      <button type="button" class="crossing-hop__remove" (click)="removeCrossingBranch($index)" aria-label="Remove this hop">✕</button>
                    }
                  </div>
                }
                <app-button variant="stroked" (pressed)="addCrossingBranch()">+ Add another crossing branch</app-button>
              </div>
              <div class="spacer"></div>
              <div class="grid3">
                <label class="fld"><span class="fld__l">Crossing Charge</span>
                  <input class="fld__i" type="number" min="0" step="0.01" [formControl]="c('crossingCharge')" /></label>
              </div>
            }
          </app-card>

          <app-card title="Items" subtitle="Add every package on this shipment; weight and dimensions drive the chargeable weight.">
            <app-item-entry-grid [initial]="voiceItems()" [defaultWeightKg]="defaultChargeableWeightKg()" (itemsChange)="onItems($event)" (weightChange)="onWeight($event)" (packagesChange)="onPackages($event)">
              <app-autocomplete class="fld--sm" [control]="c('packageTypeId')" label="Package Type" [options]="packageTypeOptions()" placeholder="Search package type…" />
              <label class="fld fld--sm"><span class="fld__l">Number of Packages</span>
                <input class="fld__i" type="number" [value]="c('numberOfPackages').value" disabled /></label>
              <label class="fld fld--sm"><span class="fld__l">Declared Value</span>
                <input class="fld__i" type="number" min="0" step="0.01" [formControl]="c('declaredValue')" /></label>
            </app-item-entry-grid>
            <div class="spacer"></div>
            <label class="fld"><span class="fld__l">Remarks</span>
              <textarea class="ta" rows="2" placeholder="Handle with care, deliver before noon…" maxlength="500" [formControl]="c('remarks')"></textarea></label>
          </app-card>

          <app-card title="Parties">
            <div class="parties">
              <div class="party party--sender">
                <div class="party__title">Consignor (Sender)</div>
                <label class="fld party__lookup"><span class="fld__l">Name</span>
                  <input class="fld__i" [formControl]="c('senderName')" placeholder="Sender's full name, or search by name / mobile"
                    maxlength="150" (focus)="openSuggest('sender', 'name')" (blur)="closeSuggest('sender')" />
                  @if (senderSuggestOpen() === 'name' && senderSuggestions().length) {
                    <ul class="lookup__list">
                      @for (cust of senderSuggestions(); track cust.id) {
                        <li class="lookup__item" (mousedown)="pickCustomer('sender', cust)">
                          <span class="lookup__name">{{ cust.displayName }}</span>
                          <span class="lookup__mobile">{{ cust.mobile }}</span>
                        </li>
                      }
                    </ul>
                  }
                </label>
                <div class="spacer"></div>
                <label class="fld"><span class="fld__l">Address</span>
                  <textarea class="ta" rows="2" [formControl]="c('senderAddress')" placeholder="Pickup address" maxlength="500"></textarea></label>
                <div class="spacer"></div>
                <label class="fld party__lookup"><span class="fld__l">Contact Number</span>
                  <input class="fld__i" type="tel" [formControl]="c('senderContact')" placeholder="10-digit mobile number, or search"
                    maxlength="20" (focus)="openSuggest('sender', 'contact')" (blur)="closeSuggest('sender')" />
                  @if (senderSuggestOpen() === 'contact' && senderSuggestions().length) {
                    <ul class="lookup__list">
                      @for (cust of senderSuggestions(); track cust.id) {
                        <li class="lookup__item" (mousedown)="pickCustomer('sender', cust)">
                          <span class="lookup__name">{{ cust.displayName }}</span>
                          <span class="lookup__mobile">{{ cust.mobile }}</span>
                        </li>
                      }
                    </ul>
                  }
                </label>
                <div class="spacer"></div>
                <label class="fld"><span class="fld__l">From Pincode</span>
                  <input class="fld__i" [formControl]="c('pickupPincode')" placeholder="e.g. 411001" maxlength="10" /></label>
              </div>

              <div class="party party--receiver">
                <div class="party__title">Consignee (Receiver)</div>
                <label class="fld party__lookup"><span class="fld__l">Name</span>
                  <input class="fld__i" [formControl]="c('receiverName')" placeholder="Receiver's full name, or search by name / mobile"
                    maxlength="150" (focus)="openSuggest('receiver', 'name')" (blur)="closeSuggest('receiver')" />
                  @if (receiverSuggestOpen() === 'name' && receiverSuggestions().length) {
                    <ul class="lookup__list">
                      @for (cust of receiverSuggestions(); track cust.id) {
                        <li class="lookup__item" (mousedown)="pickCustomer('receiver', cust)">
                          <span class="lookup__name">{{ cust.displayName }}</span>
                          <span class="lookup__mobile">{{ cust.mobile }}</span>
                        </li>
                      }
                    </ul>
                  }
                </label>
                <div class="spacer"></div>
                <label class="fld"><span class="fld__l">Address</span>
                  <textarea class="ta" rows="2" [formControl]="c('receiverAddress')" placeholder="Delivery address" maxlength="500"></textarea></label>
                <div class="spacer"></div>
                <label class="fld party__lookup"><span class="fld__l">Contact Number</span>
                  <input class="fld__i" type="tel" [formControl]="c('receiverContact')" placeholder="10-digit mobile number, or search"
                    maxlength="20" (focus)="openSuggest('receiver', 'contact')" (blur)="closeSuggest('receiver')" />
                  @if (receiverSuggestOpen() === 'contact' && receiverSuggestions().length) {
                    <ul class="lookup__list">
                      @for (cust of receiverSuggestions(); track cust.id) {
                        <li class="lookup__item" (mousedown)="pickCustomer('receiver', cust)">
                          <span class="lookup__name">{{ cust.displayName }}</span>
                          <span class="lookup__mobile">{{ cust.mobile }}</span>
                        </li>
                      }
                    </ul>
                  }
                </label>
                <div class="spacer"></div>
                <label class="fld"><span class="fld__l">To Pincode</span>
                  <input class="fld__i" [formControl]="c('deliveryPincode')" placeholder="e.g. 400008" maxlength="10" /></label>
              </div>
            </div>
          </app-card>

          <app-card title="E-Way Bill" subtitle="Required over the mandatory invoice value; optional below it.">
            <div class="grid3">
              <label class="fld"><span class="fld__l">Invoice Value</span>
                <input class="fld__i" type="number" min="0" step="0.01" [formControl]="c('invoiceValue')" placeholder="0.00" /></label>
              <div class="fld">
                <span class="fld__l">Status</span>
                @if (ewayBillMandatory()) {
                  <span class="eway-chip eway-chip--mandatory">⚠ E-Way Bill Mandatory</span>
                } @else {
                  <span class="eway-chip eway-chip--optional">E-Way Bill Optional</span>
                }
              </div>
            </div>

            @if (!ewayBillOpen()) {
              <div class="spacer"></div>
              <app-button variant="stroked" (pressed)="addEwayBill()">+ Add E-Way Bill</app-button>
            } @else {
              <div class="spacer"></div>
              <div class="grid3">
                <label class="fld"><span class="fld__l">E-Way Bill Number</span>
                  <input class="fld__i" [formControl]="c('ewayBillNumber')" placeholder="12-digit number" maxlength="30" /></label>
                <label class="fld"><span class="fld__l">Invoice Number</span>
                  <input class="fld__i" [formControl]="c('ewayBillInvoiceNumber')" placeholder="e.g. INV-1042" maxlength="50" /></label>
                <label class="fld"><span class="fld__l">Invoice Date</span>
                  <input class="fld__i" type="date" [formControl]="c('ewayBillInvoiceDate')" /></label>
                <label class="fld"><span class="fld__l">Vehicle Number</span>
                  <input class="fld__i" [formControl]="c('ewayBillVehicleNumber')" placeholder="e.g. MH12AB1234" maxlength="20" /></label>
                <label class="fld"><span class="fld__l">Valid From</span>
                  <input class="fld__i" type="date" [formControl]="c('ewayBillValidFrom')" /></label>
                <label class="fld"><span class="fld__l">Valid Until</span>
                  <input class="fld__i" type="date" [formControl]="c('ewayBillValidUntil')" /></label>
              </div>
              <div class="spacer"></div>
              <label class="fld"><span class="fld__l">Remarks</span>
                <input class="fld__i" [formControl]="c('ewayBillRemarks')" placeholder="Optional" maxlength="500" /></label>
              <div class="spacer"></div>
              <div class="eway-doc">
                @if (selectedEwayBillFile(); as file) {
                  <span class="eway-doc__name">{{ file.name }}</span>
                  <button type="button" class="eway-doc__remove" (click)="removeEwayBillFile()"><mat-icon>close</mat-icon></button>
                } @else {
                  <button type="button" class="img__btn" (click)="ewayBillFile.click()">
                    <mat-icon>upload_file</mat-icon> Upload document (PDF/JPG/PNG)
                  </button>
                }
                <input #ewayBillFile type="file" accept=".pdf,.jpg,.jpeg,.png" hidden (change)="onEwayBillFile($event)" />
              </div>
              <div class="spacer"></div>
              <app-button variant="stroked" (pressed)="removeEwayBill()">Remove</app-button>
            }
            @if (ewayBillReason(); as reason) {
              <div class="spacer"></div>
              <p class="err">{{ reason }}</p>
            }
          </app-card>

          <app-card title="Shipment Image" subtitle="Optional — a photo of the parcel, uploaded once the shipment is booked.">
            <div class="img">
              @if (imagePreviewUrl(); as preview) {
                <div class="img__preview">
                  <img [src]="preview" alt="Shipment photo preview" />
                  <button type="button" class="img__remove" (click)="removeImage()">
                    <mat-icon>close</mat-icon>
                  </button>
                </div>
              } @else {
                <button type="button" class="img__btn" (click)="imageFile.click()">
                  <mat-icon>add_a_photo</mat-icon> Choose photo
                </button>
              }
              <input #imageFile type="file" accept="image/*" hidden (change)="onImageFile($event)" />
            </div>
          </app-card>
        </div>

        <aside class="lr__sum">
          <div class="sum">
            <h2 class="sum__title">Booking Summary</h2>

            <span class="sum__lbl">Route</span>
            <span class="sum__val">{{ myBranchLabel() }} → {{ branchLabel(c('deliveryBranchId').value) }}</span>

            <span class="sum__lbl">Load</span>
            <span class="sum__val">{{ c('numberOfPackages').value || 1 }} pkg · {{ weight().chargeable | number: '1.3-3' }} kg</span>

            @if (c('invoiceValue').value) {
              <span class="sum__lbl">Invoice Value</span>
              <span class="sum__val">{{ c('invoiceValue').value | number: '1.2-2' }}
                — {{ ewayBillMandatory() ? 'E-Way Bill Mandatory' : 'E-Way Bill Optional' }}</span>
            }

            <div class="sum__body">
              @if (freightFactorApplicable()) {
                <div class="factor-row">
                  <span class="sum__lbl">Freight Factor
                    @if (matchedFreightFactor() != null) {
                      <span class="hint">(min {{ matchedFreightFactor() | number: '1.2-2' }}, increase only)</span>
                    }
                  </span>
                  <input class="factor-input" type="number" step="0.01" [min]="matchedFreightFactor()"
                         [value]="freightFactorOverride() ?? matchedFreightFactor()"
                         (input)="onFreightFactorInput($event)" />
                </div>
              }
              @if (!myBranchIdPresent()) {
                <p class="err">Your account has no branch assigned — ask an admin before booking.</p>
              } @else if (!readyToPrice()) {
                <p class="hint">Select a delivery branch and add at least one item's weight to see pricing.</p>
              } @else if (pricingLoading()) {
                <p class="hint">Pricing…</p>
              } @else if (pricingError()) {
                <p class="err">{{ pricingError() }}</p>
              } @else if (pricing(); as p) {
                <div class="matched">
                  @if (p.matchedRouteCode) { <span class="chip">Route {{ p.matchedRouteCode }}</span> }
                  @if (p.matchedRateCode) { <span class="chip">Rate {{ p.matchedRateCode }}</span> }
                </div>
                <app-charge-summary [charges]="{
                  freight: p.chargeBreakup.freight, fuelCharge: p.chargeBreakup.fuelCharge,
                  handlingCharge: p.chargeBreakup.handlingCharge,
                  odaCharge: odaChargeOverride() ?? p.chargeBreakup.odaCharge,
                  insuranceCharge: p.chargeBreakup.insuranceCharge,
                  gstAmount: p.chargeBreakup.gstAmount + gstOnOtherCharges() + gstOnOdaChargeDelta(),
                  discountAmount: p.chargeBreakup.discount, roundOff: p.chargeBreakup.roundOff,
                  otherCharges: otherCharges(),
                  netAmount: (manualNetAmount() ?? p.chargeBreakup.netAmount) + otherCharges() + gstOnOtherCharges()
                    + odaChargeDelta() + gstOnOdaChargeDelta()
                }" [editable]="true" (netAmountChange)="manualNetAmount.set($event)"
                  (otherChargesChange)="otherCharges.set($event)"
                  (odaChargeChange)="odaChargeOverride.set($event)" />
              }
            </div>

            <div class="sum__paymode">
              <app-select [control]="c('paymentModeId')" label="Payment Mode" [options]="paymentModeOptions()" placeholder="Select a payment mode" />
            </div>

            @if (ewayBillReason(); as reason) {
              <p class="err">{{ reason }}</p>
            }
            <div class="sum__cta">
              <app-button icon="check" [loading]="submitting()"
                [disabled]="!pricing() || pricingLoading() || form.invalid || ewayBillReason() !== null"
                (pressed)="book()">Book Shipment</app-button>
            </div>
          </div>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .page__head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; flex-wrap:wrap; }
    .page__head { margin-bottom:4px; }
    .page__head-actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
    .voice-banner { margin:4px 0 0; font:500 13px var(--font-sans); color:var(--brand-700); background:var(--brand-50);
      border-radius:var(--r-field); padding:8px 12px; }
    .lr { display:grid; grid-template-columns:minmax(0,1fr) 300px; gap:16px; align-items:start; margin-top:10px; }
    .lr__main { display:flex; flex-direction:column; gap:10px; min-width:0; }
    .lr__main ::ng-deep .ac__head { padding:10px 16px; }
    .lr__main ::ng-deep .ac__body { padding:14px 16px; }
    .lr__sum { position:sticky; top:10px; }
    .sum { display:flex; flex-direction:column; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); padding:14px 16px; gap:2px; }
    .sum__title { margin:0 0 6px; font:700 15px var(--font-sans); color:var(--content-fg); }
    .sum__lbl { font:500 12px var(--font-sans); color:var(--content-muted); margin-top:8px; }
    .sum__val { font:600 14px var(--font-sans); color:var(--content-fg); }
    .sum__body { margin-top:12px; }
    .factor-row { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:12px; }
    .factor-row .hint { font:400 11px var(--font-sans); }
    .factor-input { width:90px; text-align:right; font:700 14px var(--font-mono, ui-monospace); color:var(--brand-600);
      background:transparent; border:1px solid var(--surface-border); border-radius:6px; padding:2px 6px; }
    .factor-input:focus { outline:0; border-color:var(--brand-500); }
    .sum__paymode { margin-top:12px; }
    .sum__cta { margin-top:10px; }
    .sum__cta app-button, .sum__cta ::ng-deep .app-btn { width:100%; }
    .hint { font:400 13px var(--font-sans); color:var(--content-muted); margin:0; }
    .err { font:500 13px var(--font-sans); color:var(--danger); padding:10px 12px; background:var(--danger-bg); border-radius:var(--r-field); margin:0; }
    .matched { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:12px; }
    .chip { font:600 11px var(--font-sans); padding:4px 10px; border-radius:999px; background:var(--brand-50); color:var(--brand-700); }
    .grid2 { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px 16px; }
    .grid3 { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px 16px; }
    .parties { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }
    .party { background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); padding:12px; }
    .party--sender { border-top:3px solid var(--info); }
    .party--receiver { border-top:3px solid var(--brand-600); }
    .party__title { font:700 11px var(--font-sans); letter-spacing:.06em; text-transform:uppercase; margin-bottom:8px; }
    .party--sender .party__title { color:var(--info); }
    .party--receiver .party__title { color:var(--brand-600); }
    .fld { display:flex; flex-direction:column; gap:4px; }
    .chk { display:flex; gap:10px; align-items:flex-start; font:400 14px var(--font-sans); color:var(--content-fg); cursor:pointer; }
    .chk input { margin-top:3px; width:16px; height:16px; accent-color:var(--brand-600); }
    .crossing-hops { display:flex; flex-direction:column; gap:8px; margin-top:8px; }
    .crossing-hop { display:flex; align-items:flex-end; gap:8px; }
    .crossing-hop app-autocomplete { flex:1; }
    .crossing-hop__remove { flex-shrink:0; height:38px; width:38px; border:1px solid var(--surface-border);
      border-radius:var(--r-field); background:var(--surface); color:var(--content-muted); cursor:pointer; }
    .crossing-hop__remove:hover { color:var(--danger, #e11d48); border-color:currentColor; }
    .party__lookup { position:relative; }
    .lookup__list { position:absolute; top:100%; left:0; right:0; z-index:20; margin-top:2px; max-height:220px; overflow-y:auto;
      background:var(--surface); border:1px solid var(--surface-border); border-radius:var(--r-field); box-shadow:0 4px 14px rgba(0,0,0,.12);
      list-style:none; padding:4px; }
    .lookup__item { display:flex; justify-content:space-between; gap:8px; padding:7px 9px; border-radius:6px; cursor:pointer; font:400 13px var(--font-sans); }
    .lookup__item:hover { background:var(--brand-50); }
    .lookup__name { color:var(--content-fg); font-weight:500; }
    .lookup__mobile { color:var(--content-muted); }
    .fld--sm { width:160px; }
    .fld__l { font:500 13px var(--font-sans); color:var(--content-fg); }
    .fld__i { height:38px; padding:0 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); }
    .ta { width:100%; padding:8px 12px; background:var(--surface); border:1px solid var(--surface-border);
      border-radius:var(--r-field); font:400 14px var(--font-sans); color:var(--content-fg); resize:vertical; }
    .spacer { height:8px; }
    .img__btn { display:inline-flex; align-items:center; gap:6px;
      border:1px dashed var(--surface-border); background:var(--surface); border-radius:var(--r-field);
      padding:10px 16px; font:600 13px var(--font-sans); color:var(--content-fg); cursor:pointer; }
    .img__btn mat-icon { font-size:18px; width:18px; height:18px; }
    .img__preview { position:relative; display:inline-block; }
    .img__preview img { display:block; max-width:220px; max-height:160px; border-radius:var(--r-field);
      border:1px solid var(--surface-border); }
    .img__remove { position:absolute; top:-8px; right:-8px; width:26px; height:26px; border-radius:50%;
      border:1px solid var(--surface-border); background:var(--surface); color:var(--content-fg);
      display:grid; place-items:center; cursor:pointer; }
    .img__remove mat-icon { font-size:16px; width:16px; height:16px; }
    .eway-chip { display:inline-flex; align-items:center; height:38px; padding:0 12px; border-radius:var(--r-field);
      font:600 13px var(--font-sans); width:fit-content; }
    .eway-chip--optional { background:var(--brand-50); color:var(--brand-700); }
    .eway-chip--mandatory { background:var(--danger-bg); color:var(--danger); }
    .eway-doc { display:flex; align-items:center; gap:10px; }
    .eway-doc__name { font:500 13px var(--font-sans); color:var(--content-fg); }
    .eway-doc__remove { width:26px; height:26px; border-radius:50%; border:1px solid var(--surface-border);
      background:var(--surface); color:var(--content-fg); display:grid; place-items:center; cursor:pointer; flex-shrink:0; }
    .eway-doc__remove mat-icon { font-size:16px; width:16px; height:16px; }
    @media (max-width:960px){ .lr { grid-template-columns:1fr; } .lr__sum { position:static; } }
    @media (max-width:760px){ .grid2, .grid3, .parties { grid-template-columns:1fr; } }
  `]
})
export class ShipmentCreate implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ShipmentService);
  private readonly ewayBillService = inject(EwayBillService);
  private readonly customers = inject(CustomerService);
  private readonly masters = inject(MasterDataService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly notify = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly settings = inject(SettingsService);

  protected readonly submitting = signal(false);

  /** The signed-in user's own branch — Booking Branch is not a picker here, a booking
   *  desk books from its own branch, not a chosen one. `null` for a user with no branch
   *  of their own (e.g. COMPANY_ADMIN), handled as a hard stop, not a silent fallback. */
  private readonly myBranchId = this.auth.user()?.branchId ?? null;

  /** Delivery Branch picker options — excludes the caller's own branch, see `ngOnInit`. */
  protected readonly branchOptions = signal<SelectOption[]>([]);
  /** The caller's own branch label, read from the unfiltered list before it's excluded
   *  above — `myBranchLabel()` needs it even though `branchOptions()` no longer carries it. */
  protected readonly myBranchName = signal<string | null>(null);
  protected readonly serviceTypeOptions = signal<SelectOption[]>([]);
  protected readonly packageTypeOptions = signal<SelectOption[]>([]);
  protected readonly paymentModeOptions = signal<SelectOption[]>([]);

  protected readonly items = signal<ShipmentItemRequest[]>([]);
  protected readonly weight = signal({ actual: 0, volumetric: 0, chargeable: 0 });

  /** Fed to `ItemEntryGrid`'s `initial` input only after a voice command supplies a
   *  weight/package count — `null` otherwise, so the grid keeps its own default row. */
  protected readonly voiceItems = signal<ShipmentItemRequest[] | null>(null);
  /** What the last voice command actually filled — shown as a small confirmation banner;
   *  the user still reviews and clicks Book, nothing books itself off a voice command. */
  protected readonly voiceSummary = signal<string | null>(null);

  protected readonly pricing = signal<PricingResponse | null>(null);
  protected readonly pricingLoading = signal(false);
  protected readonly pricingError = signal<string | null>(null);

  /** A manual override of the previewed Net Amount — display only, cleared whenever the
   *  underlying price is recomputed. Never sent to the server: the booking is always
   *  priced server-side from the actual booking fields, not from what was shown here. */
  protected readonly manualNetAmount = signal<number | null>(null);

  /** Other Charges — a manual, typed-at-booking amount (e.g. packing, handling extras) on
   *  top of the Pricing Engine's own rate-driven breakup. Unlike {@link manualNetAmount}
   *  this one IS sent to the server, see {@link book}; it survives a reprice since it's
   *  the desk's own figure, not a stale echo of one specific preview. */
  protected readonly otherCharges = signal<number>(0);

  /** ODA Charge override — null until the operator types over the Pricing Engine's own
   *  `chargeBreakup.odaCharge` for this preview. Unlike {@link otherCharges} this one
   *  replaces the engine's line rather than adding a new one, so it resets to null (falls
   *  back to the engine's figure again) on every reprice — see {@link priceIt$}. IS sent
   *  to the server, see {@link book}. */
  protected readonly odaChargeOverride = signal<number | null>(null);

  /** Set only when the current preview priced through the Freight Factor fallback (no
   *  route/rate for this lane) — gates the "Freight Factor" input in the summary. */
  protected readonly freightFactorApplicable = signal(false);
  /** The grid cell's own factor, as last returned by a preview that carried no override —
   *  shown as the floor a typed value may not go below (server-enforced; see {@link
   *  onFreightFactorInput}). */
  protected readonly matchedFreightFactor = signal<number | null>(null);
  /** `null` until the desk types a raised factor — sent to the server as-is, which is the
   *  only place "increase only" is actually enforced. */
  protected readonly freightFactorOverride = signal<number | null>(null);

  /** The booking branch's own GST% (V25) — Other Charges is a manual, booking-time amount
   *  the Pricing Engine never sees, so GST on it is computed here (mirroring
   *  `ShipmentServiceImpl.copyCharge`) purely so the live preview's total matches what
   *  actually gets persisted at booking. */
  protected readonly myBranchGstPercentage = signal<number>(18);

  /** Company Settings → Shipment → default chargeable weight, fed to `ItemEntryGrid`.
   *  Null until the settings call resolves — the grid falls back to its own constant
   *  until then. */
  protected readonly defaultChargeableWeightKg = signal<number | null>(null);

  /** Picked at booking time but only uploaded once the shipment id exists — the endpoint
   *  is `POST /shipments/{id}/image-upload`, so there is nothing to upload to until
   *  `book()` succeeds. `imagePreviewUrl` is a local `URL.createObjectURL`, revoked on
   *  removal/replacement so it doesn't leak. */
  protected readonly selectedImageFile = signal<File | null>(null);
  protected readonly imagePreviewUrl = signal<string | null>(null);

  /**
   * E-Way Bill Management (`com.courier.modules.ewaybill`), integrated inline: invoice
   * value over the company's own configurable threshold (`ewayBillThreshold`, from
   * `GET /company-settings`'s `ewayBill` section, default 50000) makes an E-Way Bill
   * mandatory before AWB generation. The backend re-checks and enforces this itself
   * inside `POST /shipments` — `ewayBillReason()` below is UX only, never the real gate;
   * see `MEMORY/modules/eway-bill.md`.
   */
  protected readonly ewayBillThreshold = signal(50000);
  /** Shown/hidden by "Add E-Way Bill"/"Remove" — auto-opens once invoice value crosses
   *  the threshold, see `ngOnInit`. */
  protected readonly ewayBillOpen = signal(false);
  /** Picked at booking time, uploaded once the shipment (and its E-Way Bill row) exist —
   *  same delayed-upload shape as `selectedImageFile`. */
  protected readonly selectedEwayBillFile = signal<File | null>(null);

  private readonly pricingTrigger$ = new Subject<void>();

  /** Search-as-you-type over the Customer module by name or mobile (see `CustomerSpecifications`
   *  on the backend) — matches existing prior parties into `senderName`/`senderContact` and
   *  `receiverName`/`receiverContact` without joining Shipment to Customer at booking time; the
   *  picked customer's plain values are copied into the free-text fields exactly like manual
   *  typing, nothing here becomes a foreign key. */
  protected readonly senderSuggestions = signal<Customer[]>([]);
  protected readonly receiverSuggestions = signal<Customer[]>([]);
  /** Which field within the party currently owns the dropdown — `null` closes it. Both
   *  Name and Contact Number search into the same suggestion list, but only the focused
   *  field should show it, not both at once. */
  protected readonly senderSuggestOpen = signal<'name' | 'contact' | null>(null);
  protected readonly receiverSuggestOpen = signal<'name' | 'contact' | null>(null);
  private readonly senderQuery$ = new Subject<string>();
  private readonly receiverQuery$ = new Subject<string>();

  protected readonly form: FormGroup = this.fb.group({
    bookingBranchId: [this.myBranchId, Validators.required],
    manualShipmentNumber: ['', Validators.maxLength(30)],
    deliveryBranchId: [null as string | null, Validators.required],
    pickupPincode: ['', Validators.maxLength(10)],
    deliveryPincode: ['', Validators.maxLength(10)],
    senderName: ['', [Validators.required, Validators.maxLength(150)]],
    senderAddress: ['', [Validators.required, Validators.maxLength(500)]],
    senderContact: ['', [Validators.required, Validators.maxLength(20)]],
    receiverName: ['', [Validators.required, Validators.maxLength(150)]],
    receiverAddress: ['', [Validators.required, Validators.maxLength(500)]],
    receiverContact: ['', [Validators.required, Validators.maxLength(20)]],
    serviceTypeId: [null as string | null, Validators.required],
    packageTypeId: [null as string | null, Validators.required],
    paymentModeId: [null as string | null, Validators.required],
    bookingDate: [today()],
    numberOfPackages: [1],
    declaredValue: [null as number | null],
    remarks: ['', Validators.maxLength(500)],
    crossing: [false],
    crossingBranchIds: this.fb.array<FormControl<string | null>>([]),
    crossingCharge: [null as number | null],
    invoiceValue: [null as number | null],
    ewayBillNumber: ['', Validators.maxLength(30)],
    ewayBillInvoiceNumber: ['', Validators.maxLength(50)],
    ewayBillInvoiceDate: [today()],
    ewayBillVehicleNumber: ['', Validators.maxLength(20)],
    ewayBillValidFrom: [null as string | null],
    ewayBillValidUntil: [null as string | null],
    ewayBillRemarks: ['', Validators.maxLength(500)]
  });

  protected get crossingBranchArray(): FormArray<FormControl<string | null>> {
    return this.form.get('crossingBranchIds') as FormArray<FormControl<string | null>>;
  }

  /** `app-autocomplete` wants a plain `FormControl` — a `FormArray` element is typed as
   *  `AbstractControl` in the template's `@for`, so this narrows it back. */
  protected asControl(ctrl: AbstractControl): FormControl<string | null> {
    return ctrl as FormControl<string | null>;
  }

  protected addCrossingBranch(): void {
    this.crossingBranchArray.push(this.fb.control(null as string | null, Validators.required));
  }

  protected removeCrossingBranch(index: number): void {
    this.crossingBranchArray.removeAt(index);
  }

  private readonly deliveryBranchIdValue = toSignal(this.form.get('deliveryBranchId')!.valueChanges, {
    initialValue: this.form.get('deliveryBranchId')!.value
  });

  /** Crossing Branch picker options — excludes the caller's own branch (already out of
   *  `branchOptions`) and whichever branch is currently picked as Delivery Branch, so a
   *  crossing hop can't equal either endpoint of the shipment. */
  protected readonly crossingBranchOptions = computed(() =>
    this.branchOptions().filter((opt) => opt.value !== this.deliveryBranchIdValue())
  );

  ngOnInit(): void {
    this.breadcrumb.set([{ label: 'Shipments', route: '/shipments' }, { label: 'New' }]);
    this.settings.get().subscribe((d) => {
      const shipment = (d as { shipment?: { defaultChargeableWeightKg?: number } })?.shipment;
      if (shipment?.defaultChargeableWeightKg != null) {
        this.defaultChargeableWeightKg.set(Number(shipment.defaultChargeableWeightKg));
      }
      const ewayBill = (d as { ewayBill?: { ewayBillMandatoryValue?: number } })?.ewayBill;
      if (ewayBill?.ewayBillMandatoryValue != null) {
        this.ewayBillThreshold.set(Number(ewayBill.ewayBillMandatoryValue));
      }
    });
    // Auto-opens the E-Way Bill section the moment invoice value crosses the threshold —
    // a desk typing a large invoice shouldn't also have to remember to click "Add E-Way
    // Bill" themselves. Never auto-closes it once opened, even if the value drops back
    // down, since the desk may already be partway through filling it in.
    this.c('invoiceValue').valueChanges.subscribe(() => {
      if (this.ewayBillMandatory()) this.ewayBillOpen.set(true);
    });
    // Delivery Branch excludes the caller's own booking branch — a shipment cannot be
    // booked and delivered from the same branch (no route covers that pair).
    this.masters.options('branches').subscribe((o) => {
      this.myBranchName.set(o.find((opt) => opt.value === this.myBranchId)?.label ?? null);
      this.branchOptions.set(o.filter((opt) => opt.value !== this.myBranchId));
    });
    // Pincodes default from the branches' own postal codes, so picking a delivery branch
    // is enough to price — typing an exact pincode is only needed to check a more precise
    // serviceability than "somewhere in this branch's area".
    this.masters.branchDirectory().subscribe((list) => {
      const mine = list.find((b) => b.id === this.myBranchId);
      if (mine?.postalCode && !this.form.get('pickupPincode')?.value) {
        this.form.get('pickupPincode')?.setValue(mine.postalCode);
      }
      if (mine?.gstPercentage != null) this.myBranchGstPercentage.set(mine.gstPercentage);
    });
    this.form.get('deliveryBranchId')?.valueChanges.subscribe((id) => {
      if (!id) return;
      this.masters.branchDirectory().subscribe((list) => {
        const branch = list.find((b) => b.id === id);
        if (branch?.postalCode) this.form.get('deliveryPincode')?.setValue(branch.postalCode);
      });
    });
    // At least one Crossing Branch is required only while Crossing is checked —
    // unchecking clears every picked hop, so a hidden stale value never submits.
    this.form.get('crossing')?.valueChanges.subscribe((on) => {
      if (on) {
        if (this.crossingBranchArray.length === 0) this.addCrossingBranch();
      } else {
        this.crossingBranchArray.clear();
        this.form.get('crossingCharge')?.setValue(null);
      }
    });
    this.masters.options('service-types').subscribe((o) => {
      this.serviceTypeOptions.set(o);
      if (o.length && !this.form.get('serviceTypeId')?.value) this.form.get('serviceTypeId')?.setValue(o[0].value);
    });
    this.masters.options('package-types').subscribe((o) => {
      this.packageTypeOptions.set(o);
      if (o.length && !this.form.get('packageTypeId')?.value) this.form.get('packageTypeId')?.setValue(o[0].value);
    });
    this.masters.options('payment-modes').subscribe((o) => {
      this.paymentModeOptions.set(o);
      const paid = o.find((opt) => opt.label.endsWith('(PAID)'));
      if (paid && !this.form.get('paymentModeId')?.value) this.form.get('paymentModeId')?.setValue(paid.value);
    });

    // Only the fields that actually feed PricingCommand reschedule a price call — typing
    // in sender/receiver name, address, contact or pincode (or an item's own name, see
    // onItems) does not move the price, so it must not restart the debounce or spam
    // /pricing/calculate on every keystroke there.
    const PRICE_AFFECTING_CONTROLS = ['deliveryBranchId', 'serviceTypeId', 'packageTypeId',
      'paymentModeId', 'declaredValue', 'bookingDate'];
    merge(...PRICE_AFFECTING_CONTROLS.map((name) => this.form.get(name)!.valueChanges))
      .subscribe(() => { this.resetFreightFactor(); this.schedulePricing(); });

    this.pricingTrigger$.pipe(
      debounceTime(500),
      switchMap(() => this.priceIt$()),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((outcome) => {
      this.pricingLoading.set(false);
      if (outcome.ok) {
        this.pricing.set(outcome.data);
        this.pricingError.set(null);
        const applied = outcome.data.appliedFreightFactor;
        if (applied != null) {
          this.freightFactorApplicable.set(true);
          // Only capture the floor from a response priced with no override — an overridden
          // response echoes back the override itself, not the grid's own matched value.
          if (this.freightFactorOverride() == null) this.matchedFreightFactor.set(applied);
        } else {
          this.freightFactorApplicable.set(false);
          this.matchedFreightFactor.set(null);
          this.freightFactorOverride.set(null);
        }
      } else {
        this.pricing.set(null);
        this.pricingError.set(outcome.message);
      }
    });

    // Name and Contact Number both feed the same free-text search — typing either one
    // looks up prior customers matching it (CustomerSpecifications LIKEs code/name/mobile/email).
    merge(this.c('senderName').valueChanges, this.c('senderContact').valueChanges)
      .subscribe((v) => this.senderQuery$.next(v ?? ''));
    merge(this.c('receiverName').valueChanges, this.c('receiverContact').valueChanges)
      .subscribe((v) => this.receiverQuery$.next(v ?? ''));

    this.senderQuery$.pipe(
      debounceTime(300), distinctUntilChanged(), filter((q) => q.trim().length >= 2),
      switchMap((q) => this.customers.list({ page: 0, size: 6, status: 'ACTIVE', search: q })),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((page) => this.senderSuggestions.set(page.content));

    this.receiverQuery$.pipe(
      debounceTime(300), distinctUntilChanged(), filter((q) => q.trim().length >= 2),
      switchMap((q) => this.customers.list({ page: 0, size: 6, status: 'ACTIVE', search: q })),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((page) => this.receiverSuggestions.set(page.content));
  }

  /** Marks which field owns the dropdown; the template only renders it once that party's
   *  suggestions list is non-empty, so an empty focus shows nothing until a search resolves. */
  protected openSuggest(party: 'sender' | 'receiver', field: 'name' | 'contact'): void {
    (party === 'sender' ? this.senderSuggestOpen : this.receiverSuggestOpen).set(field);
  }

  /** Copies the picked customer's plain name/mobile into the party's free-text fields —
   *  no Customer id is ever stored on the shipment, matching the rest of this form. */
  protected pickCustomer(party: 'sender' | 'receiver', cust: Customer): void {
    this.c(`${party}Name`).setValue(cust.displayName);
    this.c(`${party}Contact`).setValue(cust.mobile);
    (party === 'sender' ? this.senderSuggestOpen : this.receiverSuggestOpen).set(null);
  }

  /** Deferred so `mousedown` on a suggestion fires before the input's own `blur` closes the list. */
  protected closeSuggest(party: 'sender' | 'receiver'): void {
    setTimeout(() => (party === 'sender' ? this.senderSuggestOpen : this.receiverSuggestOpen).set(null), 150);
  }

  protected c(name: string): FormControl { return this.form.get(name) as FormControl; }
  protected branchLabel(id: string | null): string { return this.labelOf(this.branchOptions(), id); }

  /** "—" while the branch list is still loading, then either the caller's own branch
   *  name or a hard "no branch assigned" notice — never a picker. */
  protected myBranchLabel(): string {
    if (!this.myBranchId) return 'no branch assigned to your account';
    return this.myBranchName() ?? '—';
  }

  /** Just keeps the booking payload's item list current — an item's name never affects
   *  price, and its weight/dimensions already reschedule pricing through {@link onWeight}. */
  protected onItems(items: ShipmentItemRequest[]): void {
    this.items.set(items);
  }

  protected onWeight(weight: { actual: number; volumetric: number; chargeable: number }): void {
    this.weight.set(weight);
    this.resetFreightFactor();
    this.schedulePricing();
  }

  /** A different lane or weight may not hit the Freight Factor fallback at all, or may
   *  match a different grid cell — any typed override from the previous quote no longer
   *  means anything, so it's cleared rather than silently resubmitted. */
  private resetFreightFactor(): void {
    this.freightFactorOverride.set(null);
    this.matchedFreightFactor.set(null);
  }

  protected onFreightFactorInput(e: Event): void {
    const v = Number((e.target as HTMLInputElement).value);
    if (Number.isNaN(v)) return;
    this.freightFactorOverride.set(v);
    this.schedulePricing();
  }

  /** Manual Other Charges (never priced by the Pricing Engine) at the booking branch's own
   *  GST% (V25) — mirrors `ShipmentServiceImpl.copyCharge`'s server-side math so the live
   *  total shown here matches what actually gets booked. */
  protected gstOnOtherCharges(): number {
    return (this.otherCharges() * this.myBranchGstPercentage()) / 100;
  }

  /** ODA Charge is normally the Pricing Engine's own figure (GST on it already folded into
   *  `chargeBreakup.gstAmount`) — once the operator types an override, only the *difference*
   *  from the engine's figure needs fresh GST, same branch GST% as {@link gstOnOtherCharges}.
   *  Mirrors `ShipmentServiceImpl.copyCharge`'s `odaChargeDelta`/`gstOnOdaChargeDelta`. */
  protected gstOnOdaChargeDelta(): number {
    return (this.odaChargeDelta() * this.myBranchGstPercentage()) / 100;
  }

  /** Difference between the typed ODA override and the Pricing Engine's own figure — zero
   *  until the operator edits it. See {@link gstOnOdaChargeDelta}. */
  protected odaChargeDelta(): number {
    const override = this.odaChargeOverride();
    if (override === null) return 0;
    const engineOda = this.pricing()?.chargeBreakup.odaCharge ?? 0;
    return override - engineOda;
  }

  protected onPackages(count: number): void {
    this.form.get('numberOfPackages')?.setValue(count, { emitEvent: false });
  }

  protected cancel(): void { this.router.navigate(['/shipments']); }

  protected onImageFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const previous = this.imagePreviewUrl();
    if (previous) URL.revokeObjectURL(previous);
    this.selectedImageFile.set(file);
    this.imagePreviewUrl.set(URL.createObjectURL(file));
  }

  protected removeImage(): void {
    const previous = this.imagePreviewUrl();
    if (previous) URL.revokeObjectURL(previous);
    this.selectedImageFile.set(null);
    this.imagePreviewUrl.set(null);
  }

  // ------------------------------------------------------------------- E-Way Bill

  /** A plain method, not `computed()` — same `FormControl.value`-staleness reason as
   *  {@link readyToPrice}. Re-invoked on every change-detection run, so it never lags
   *  behind a typed invoice value. */
  protected ewayBillMandatory(): boolean {
    const v = Number(this.c('invoiceValue').value);
    return !!v && v > this.ewayBillThreshold();
  }

  /** Null once nothing blocks booking; otherwise the reason shown next to the Book
   *  button. Mirrors `LocalEwayBillProvider`'s own field checks for instant feedback —
   *  the backend re-runs the real check server-side regardless, since this is UX only. */
  protected ewayBillReason(): string | null {
    if (!this.ewayBillMandatory()) return null;
    if (!this.ewayBillOpen()) {
      return 'E-Way Bill is mandatory because invoice value exceeds the configured threshold — add one below.';
    }
    const number = (this.c('ewayBillNumber').value ?? '').trim();
    const invoiceNumber = (this.c('ewayBillInvoiceNumber').value ?? '').trim();
    if (!/^\d{12}$/.test(number)) return 'E-Way Bill number must be exactly 12 digits.';
    if (!invoiceNumber) return 'An E-Way Bill invoice number is required.';
    if (!this.c('ewayBillInvoiceDate').value) return 'An E-Way Bill invoice date is required.';
    return null;
  }

  protected addEwayBill(): void { this.ewayBillOpen.set(true); }

  protected removeEwayBill(): void {
    this.ewayBillOpen.set(false);
    this.c('ewayBillNumber').setValue('');
    this.c('ewayBillInvoiceNumber').setValue('');
    this.c('ewayBillInvoiceDate').setValue(today());
    this.c('ewayBillVehicleNumber').setValue('');
    this.c('ewayBillValidFrom').setValue(null);
    this.c('ewayBillValidUntil').setValue(null);
    this.c('ewayBillRemarks').setValue('');
    this.selectedEwayBillFile.set(null);
  }

  protected onEwayBillFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) this.selectedEwayBillFile.set(file);
  }

  protected removeEwayBillFile(): void { this.selectedEwayBillFile.set(null); }

  /**
   * Rule-based transcript → form fields (see `voice-booking.util.ts`). Only ever
   * `setValue`s a control when the transcript actually supplied that field — an
   * incomplete voice command leaves the rest of the form exactly as it was, never
   * clears anything. Nothing books itself: the existing pricing debounce and the
   * Booking Summary sidebar pick up the filled fields the same way manual typing does,
   * and the user still has to review and click Book Shipment.
   */
  protected onVoiceTranscript(transcript: string): void {
    const fields = parseVoiceBooking(transcript);
    const applied: string[] = [];

    const setIfPresent = (control: string, value: string | number | null | undefined, label: string) => {
      if (value === null || value === undefined || value === '') return;
      this.form.get(control)?.setValue(value);
      applied.push(label);
    };

    setIfPresent('senderName', fields.senderName, 'sender name');
    setIfPresent('senderContact', fields.senderContact, 'sender contact');
    setIfPresent('senderAddress', fields.senderAddress, 'sender address');
    setIfPresent('pickupPincode', fields.pickupPincode, 'pickup pincode');
    setIfPresent('receiverName', fields.receiverName, 'receiver name');
    setIfPresent('receiverContact', fields.receiverContact, 'receiver contact');
    setIfPresent('receiverAddress', fields.receiverAddress, 'receiver address');
    setIfPresent('deliveryPincode', fields.deliveryPincode, 'delivery pincode');
    setIfPresent('declaredValue', fields.declaredValue, 'declared value');
    setIfPresent('remarks', fields.remarks, 'remarks');

    const branchId = fields.deliveryBranchText && this.matchOption(this.branchOptions(), fields.deliveryBranchText);
    setIfPresent('deliveryBranchId', branchId || null, 'delivery branch');

    const serviceTypeId = fields.serviceTypeText && this.matchOption(this.serviceTypeOptions(), fields.serviceTypeText);
    setIfPresent('serviceTypeId', serviceTypeId || null, 'service type');

    const packageTypeId = fields.packageTypeText && this.matchOption(this.packageTypeOptions(), fields.packageTypeText);
    setIfPresent('packageTypeId', packageTypeId || null, 'package type');

    const paymentModeId = fields.paymentModeText && this.matchOption(this.paymentModeOptions(), fields.paymentModeText);
    setIfPresent('paymentModeId', paymentModeId || null, 'payment mode');

    if (fields.weightKg != null || fields.numberOfPackages != null) {
      this.voiceItems.set([{
        itemName: 'Package', quantity: fields.numberOfPackages ?? 1, weight: fields.weightKg ?? 5,
        lengthCm: null, widthCm: null, heightCm: null,
        declaredValue: fields.declaredValue ?? null, fragile: false, dangerousGoods: false
      }]);
      applied.push('items (weight/packages)');
    }

    if (applied.length) {
      this.voiceSummary.set(`Voice filled: ${applied.join(', ')}. Review and click Book Shipment.`);
      this.notify.success(`Voice booking filled ${applied.length} field${applied.length > 1 ? 's' : ''} from what you said.`);
    } else {
      this.voiceSummary.set(null);
      this.notify.error('Could not pick out any booking details from that. Try naming sender/receiver, pincode, weight and payment mode.');
    }
  }

  protected onVoiceError(message: string): void { this.notify.error(message); }

  private matchOption(options: SelectOption[], spoken: string): string | null {
    const needle = spoken.trim().toLowerCase();
    if (!needle) return null;
    const found = options.find((o) => {
      const label = o.label.toLowerCase();
      return label.includes(needle) || needle.includes(label);
    });
    return found?.value ?? null;
  }

  /**
   * A plain method, not `computed()` — see `MEMORY/modules/shipment-booking.md`'s note
   * on the identical `computed()`-reading-`FormControl.value` staleness bug this
   * project already hit once. Called from the template on every change detection run,
   * so it never goes stale.
   */
  protected readyToPrice(): boolean {
    const v = this.form.getRawValue();
    // Only the fields the Pricing Engine actually reads — sender/receiver identity plays
    // no part in a price, so the preview shouldn't wait on it (booking itself still does,
    // via the Book button's own `form.invalid` check).
    return !!(v.bookingBranchId && v.deliveryBranchId && v.serviceTypeId && v.packageTypeId
      && v.paymentModeId && this.weight().chargeable > 0);
  }

  /** See {@link readyToPrice} — same reason this is a plain method. */
  protected myBranchIdPresent(): boolean { return !!this.myBranchId; }

  private schedulePricing(): void {
    this.pricing.set(null);
    this.pricingError.set(null);
    this.manualNetAmount.set(null);
    this.odaChargeOverride.set(null);
    this.pricingTrigger$.next();
  }

  private priceIt$(): Observable<PriceOutcome> {
    if (!this.readyToPrice()) return of({ ok: false, message: null } as PriceOutcome);

    this.pricingLoading.set(true);
    const v = this.form.getRawValue();

    return this.service.preview({
      bookingBranchId: v.bookingBranchId, deliveryBranchId: v.deliveryBranchId,
      pickupPincode: v.pickupPincode, deliveryPincode: v.deliveryPincode,
      serviceTypeId: v.serviceTypeId, packageTypeId: v.packageTypeId, paymentModeId: v.paymentModeId,
      actualWeight: this.weight().chargeable, declaredValue: v.declaredValue || null,
      bookingDate: v.bookingDate || null, freightFactorOverride: this.freightFactorOverride()
    }).pipe(
      switchMap((data) => of({ ok: true, data }) as Observable<PriceOutcome>),
      catchError((e: HttpErrorResponse) =>
        of({ ok: false, message: e?.error?.message ?? 'Could not price this booking.' } as PriceOutcome))
    );
  }

  protected book(): void {
    const p = this.pricing();
    if (!p || this.form.invalid || this.ewayBillReason() !== null) return;
    const v = this.form.getRawValue();

    const body: CreateShipmentRequest = {
      bookingBranchId: v.bookingBranchId, manualShipmentNumber: v.manualShipmentNumber?.trim() || null,
      deliveryBranchId: v.deliveryBranchId,
      pickupPincode: v.pickupPincode, deliveryPincode: v.deliveryPincode,
      senderName: v.senderName, senderAddress: v.senderAddress, senderContact: v.senderContact,
      receiverName: v.receiverName, receiverAddress: v.receiverAddress, receiverContact: v.receiverContact,
      serviceTypeId: v.serviceTypeId, packageTypeId: v.packageTypeId, paymentModeId: v.paymentModeId,
      bookingDate: v.bookingDate || null,
      declaredValue: v.declaredValue || null, numberOfPackages: v.numberOfPackages || 1,
      remarks: v.remarks || null, otherCharges: this.otherCharges() || null,
      odaCharge: this.odaChargeOverride(),
      freightFactorOverride: this.freightFactorOverride(), items: this.items(),
      crossing: v.crossing || null,
      crossingBranchIds: v.crossing ? (v.crossingBranchIds as (string | null)[]).filter((id): id is string => !!id) : null,
      crossingCharge: v.crossingCharge || null,
      invoiceValue: v.invoiceValue || null,
      ewayBill: this.ewayBillOpen() && (v.ewayBillNumber?.trim() || v.ewayBillInvoiceNumber?.trim()) ? {
        ewayBillNumber: v.ewayBillNumber?.trim() || null,
        invoiceNumber: v.ewayBillInvoiceNumber?.trim() || '',
        invoiceDate: v.ewayBillInvoiceDate || today(),
        documentType: 'INVOICE',
        vehicleNumber: v.ewayBillVehicleNumber?.trim() || null,
        validFrom: toInstantStart(v.ewayBillValidFrom),
        validUntil: toInstantEnd(v.ewayBillValidUntil),
        remarks: v.ewayBillRemarks?.trim() || null
      } : null
    };

    this.submitting.set(true);
    this.service.create(body).subscribe({
      next: (s) => {
        this.submitting.set(false);
        const ewayBillFile = this.selectedEwayBillFile();
        if (ewayBillFile && s.ewayBill?.id) {
          this.ewayBillService.upload(s.ewayBill.id, ewayBillFile).subscribe({
            error: () => this.notify.error(`Shipment ${s.shipmentNumber} booked, but the E-Way Bill document could not be uploaded.`)
          });
        }
        this.notify.success(`Shipment ${s.shipmentNumber} booked — AWB ${s.trackingNumber}.`);
        printConsignmentCopies({
          companyName: this.auth.companyName() ?? 'Courier SaaS',
          companyLogo: this.auth.companyLogo(),
          shipmentNumber: s.shipmentNumber, trackingNumber: s.trackingNumber, bookingDate: s.bookingDate,
          expectedDeliveryDate: s.expectedDeliveryDate ?? null,
          bookingBranchLabel: this.myBranchLabel(), deliveryBranchLabel: this.branchLabel(v.deliveryBranchId),
          senderName: v.senderName, senderAddress: v.senderAddress, senderContact: v.senderContact,
          receiverName: v.receiverName, receiverAddress: v.receiverAddress, receiverContact: v.receiverContact,
          serviceTypeLabel: this.labelOf(this.serviceTypeOptions(), v.serviceTypeId),
          packageTypeLabel: this.labelOf(this.packageTypeOptions(), v.packageTypeId),
          paymentModeLabel: this.labelOf(this.paymentModeOptions(), v.paymentModeId),
          numberOfPackages: v.numberOfPackages || 1, chargeableWeight: this.weight().chargeable,
          declaredValue: v.declaredValue || null,
          charges: {
            ...p.chargeBreakup,
            odaCharge: this.odaChargeOverride() ?? p.chargeBreakup.odaCharge,
            gstAmount: p.chargeBreakup.gstAmount + this.gstOnOtherCharges() + this.gstOnOdaChargeDelta(),
            netAmount: p.chargeBreakup.netAmount + this.gstOnOtherCharges()
              + this.odaChargeDelta() + this.gstOnOdaChargeDelta()
          },
          otherCharges: this.otherCharges(),
          remarks: v.remarks || null,
          createdByName: s.createdByName ?? null
        });
        const image = this.selectedImageFile();
        if (image) {
          this.service.uploadImage(s.id, image).subscribe({
            error: () => this.notify.error(`Shipment ${s.shipmentNumber} booked, but the photo could not be uploaded.`)
          });
        }
        this.router.navigate(['/shipments', s.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // Any 4xx is the caller's to fix (bad input, a business rule, a stale reference) and
        // carries a specific message worth showing; 5xx/network failures get the generic one.
        const specific: string | null = err.status >= 400 && err.status < 500 ? err.error?.message : null;
        // ShipmentServiceImpl.requireSufficientBalance's own wording ("available X, required Y")
        // is accurate but not actionable — the fix is always one of two things, so say that
        // instead of the raw balance figures.
        if (specific?.startsWith('Insufficient wallet balance')) {
          this.notify.error('Insufficient wallet balance — recharge your wallet or choose a different payment mode.');
          return;
        }
        this.notify.error(specific ?? 'Could not book this shipment.');
      }
    });
  }

  private labelOf(options: SelectOption[], value: string | null): string {
    if (!value) return '—';
    return options.find((o) => o.value === value)?.label ?? '—';
  }
}
