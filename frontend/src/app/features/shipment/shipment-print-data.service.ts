import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AuthService } from '@core/auth/auth.service';
import { ShipmentCharge, ShipmentResponse } from '@core/models/shipment.model';
import { MasterDataService } from '@features/masters/master-data.service';
import { CompanyLetterhead, CompanyProfileService } from '@features/company/company-profile.service';
import { ConsignmentPrintData, companyAddressLine } from './consignment-print.util';
import { ShipmentService } from './shipment.service';

interface Lookups {
  company: CompanyLetterhead | null;
  branches: Map<string, string>;
  serviceTypes: Map<string, string>;
  packageTypes: Map<string, string>;
  paymentModes: Map<string, string>;
}

/** Builds `ConsignmentPrintData` for a saved shipment — the one place the booking's
 *  stored fields, its charge record and the master-data labels are joined for printing.
 *  Used by the shipment detail page (one shipment) and the list's bulk print (many). */
@Injectable({ providedIn: 'root' })
export class ShipmentPrintDataService {
  private readonly auth = inject(AuthService);
  private readonly masters = inject(MasterDataService);
  private readonly companyProfile = inject(CompanyProfileService);
  private readonly shipments = inject(ShipmentService);

  private labelMap(source: 'branches' | 'service-types' | 'package-types' | 'payment-modes'): Observable<Map<string, string>> {
    return this.masters.options(source).pipe(
      map((opts) => new Map(opts.map((o) => [String(o.value), o.label] as [string, string]))),
      catchError(() => of(new Map<string, string>())));
  }

  private lookups(): Observable<Lookups> {
    return forkJoin({
      company: this.companyProfile.get().pipe(catchError(() => of(null))),
      branches: this.labelMap('branches'),
      serviceTypes: this.labelMap('service-types'),
      packageTypes: this.labelMap('package-types'),
      paymentModes: this.labelMap('payment-modes')
    });
  }

  /** One shipment, already loaded by the caller together with its charge record. */
  build(s: ShipmentResponse, c: ShipmentCharge): Observable<ConsignmentPrintData> {
    return this.lookups().pipe(switchMap((l) => this.assemble(s, c, l)));
  }

  /** Bulk: loads each shipment + charge by id (skipping any without a charge record),
   *  keeping the caller's order. Lookups are fetched once for the whole batch. */
  buildMany(ids: string[]): Observable<ConsignmentPrintData[]> {
    if (!ids.length) return of([]);
    return this.lookups().pipe(switchMap((l) => forkJoin(ids.map((id) =>
      forkJoin({ s: this.shipments.get(id), c: this.shipments.charges(id).pipe(catchError(() => of(null))) }).pipe(
        switchMap(({ s, c }) => (c ? this.assemble(s, c, l) : of(null))),
        catchError(() => of(null))))
    )), map((all) => all.filter((x): x is ConsignmentPrintData => x !== null)));
  }

  private assemble(s: ShipmentResponse, c: ShipmentCharge, l: Lookups): Observable<ConsignmentPrintData> {
    const label = (m: Map<string, string>, id: string | null | undefined) => (id ? m.get(id) ?? id : '—');
    return forkJoin({
      bookingGeo: s.pickupPincode ? this.masters.pincodeGeo(s.pickupPincode).pipe(catchError(() => of(null))) : of(null),
      deliveryGeo: s.deliveryPincode
        ? this.masters.pincodeGeo(s.deliveryPincode, s.destinationAreaId).pipe(catchError(() => of(null)))
        : of(null)
    }).pipe(map(({ bookingGeo, deliveryGeo }): ConsignmentPrintData => ({
      companyName: l.company?.companyName ?? this.auth.companyName() ?? 'Courier SaaS',
      companyLogo: l.company?.logo ?? this.auth.companyLogo(),
      companyAddress: companyAddressLine(l.company),
      companyGst: l.company?.gstNumber ?? null,
      companyContact: l.company?.mobile ?? null,
      companyWebsite: l.company?.website ?? null,
      shipmentNumber: s.shipmentNumber, trackingNumber: s.trackingNumber, bookingDate: s.bookingDate,
      expectedDeliveryDate: s.expectedDeliveryDate ?? null,
      bookingBranchLabel: label(l.branches, s.bookingBranchId),
      deliveryBranchLabel: s.deliveryBranchId ? label(l.branches, s.deliveryBranchId) : s.toCity || '—',
      bookingPincode: s.pickupPincode || null,
      bookingDistrict: bookingGeo?.districtName ?? null,
      bookingArea: bookingGeo?.areaName ?? null,
      deliveryPincode: s.deliveryPincode || null,
      deliveryDistrict: deliveryGeo?.districtName ?? null,
      deliveryArea: deliveryGeo?.areaName ?? null,
      senderName: s.senderName, senderAddress: s.senderAddress, senderContact: s.senderContact,
      receiverName: s.receiverName, receiverAddress: s.receiverAddress, receiverContact: s.receiverContact,
      serviceTypeLabel: label(l.serviceTypes, s.serviceTypeId), packageTypeLabel: label(l.packageTypes, s.packageTypeId),
      paymentModeLabel: label(l.paymentModes, s.paymentModeId),
      deliveryType: s.deliveryType,
      numberOfPackages: s.numberOfPackages, chargeableWeight: s.chargeableWeight,
      declaredValue: s.declaredValue ?? null,
      charges: {
        freight: c.freight, fuelCharge: c.fuelCharge, handlingCharge: c.handlingCharge, odaCharge: c.odaCharge,
        insuranceCharge: c.insuranceCharge, applicableCharges: c.applicableCharges,
        applicableChargeLines: c.applicableChargeLines, gstAmount: c.gstAmount,
        discount: c.discountAmount, roundOff: c.roundOff,
        // The saved netAmount already includes Other, Appointment, Door-delivery and To-Pay
        // charges, and the print totals add each back on top (same shape as the booking-time
        // print) — take them out here so they are counted once.
        netAmount: c.netAmount - (c.otherCharges ?? 0) - (c.appointmentDeliveryCharge ?? 0)
          - (c.doorDeliveryCharge ?? 0) - (c.toPayCharge ?? 0)
      },
      otherCharges: c.otherCharges,
      appointmentDeliveryCharge: c.appointmentDeliveryCharge,
      doorDeliveryCharge: c.doorDeliveryCharge,
      toPayCharge: c.toPayCharge,
      remarks: s.remarks ?? null,
      createdByName: s.createdByName ?? null,
      invoiceValue: s.invoiceValue ?? null,
      items: s.items.map((i) => ({ weight: i.weight, lengthCm: i.lengthCm, widthCm: i.widthCm, heightCm: i.heightCm }))
    })));
  }
}
