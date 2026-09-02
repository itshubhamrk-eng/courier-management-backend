/**
 * Shipment Booking models — mirror the backend `com.courier.modules.shipment` module
 * one-to-one (see MEMORY/modules/shipment-booking.md). A shipment is booked only after
 * Customer, Serviceability + Route + Pricing (all inside one Pricing Engine call) and,
 * for a PAID booking, the Branch Wallet have all agreed. No mock shapes: every field is
 * returned by, or accepted by, an endpoint.
 */

export type ShipmentType = 'DOCUMENT' | 'NON_DOCUMENT' | 'CARGO';
export const SHIPMENT_TYPES: ShipmentType[] = ['DOCUMENT', 'NON_DOCUMENT', 'CARGO'];

/**
 * Renamed in V19 (Shipment Movement) to match that module's own vocabulary exactly:
 * `MANIFESTED` -> `MANIFEST_CREATED`, `RECEIVED` -> `IN_SCAN`, `RETURN_INITIATED` folded
 * into a direct edge to `RETURNED`. V20, on direct request, folded the separate
 * `OUT_SCAN` state back into `MANIFEST_CREATED` — one milestone ("loading sheet created"),
 * not two; adding a shipment to a manifest already is the loading sheet action. See backend
 * `ShipmentStatus.java`.
 */
export type ShipmentStatus =
  | 'BOOKED' | 'READY_FOR_MANIFEST' | 'MANIFEST_CREATED' | 'DISPATCHED' | 'IN_SCAN'
  | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'RETURNED' | 'CANCELLED';

/** The states a booking may still be cancelled from — mirrors `ShipmentStatus.isCancellable()`. */
export const CANCELLABLE_STATUSES: ShipmentStatus[] =
  ['BOOKED', 'READY_FOR_MANIFEST', 'MANIFEST_CREATED'];

export type ShipmentDocumentType = 'INVOICE' | 'EWAY_BILL' | 'PACKING_LIST' | 'LR_COPY' | 'POD';
export const SHIPMENT_DOCUMENT_TYPES: ShipmentDocumentType[] =
  ['INVOICE', 'EWAY_BILL', 'PACKING_LIST', 'LR_COPY', 'POD'];

/** One packed item — mirrors backend `ShipmentItemResponse`/`ShipmentItemRequest`. */
export interface ShipmentItem {
  id?: string;
  itemName: string;
  quantity: number;
  weight: number;
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
  declaredValue?: number | null;
  fragile: boolean;
  dangerousGoods: boolean;
}

/** List-row projection — mirrors backend `ShipmentSummaryResponse` (GET /shipments). */
export interface Shipment {
  id: string;
  shipmentNumber: string;
  trackingNumber: string;
  bookingDate: string;
  bookingBranchId: string;
  deliveryBranchId: string;
  /** Where the shipment physically is right now — same as `bookingBranchId` unless it has
   *  moved past a crossing hop. */
  currentLocationId?: string | null;
  /** The shipment's next stop — a crossing hub, or `deliveryBranchId` once every hop is
   *  done. */
  nextLocationId?: string | null;
  manifestId?: string | null;
  paymentModeId: string;
  senderName: string;
  senderContact: string;
  receiverName: string;
  receiverContact: string;
  chargeableWeight: number;
  /** Null only for a row with no charge record — see backend `ShipmentSummaryResponse`. */
  netAmount: number | null;
  /** Commission breakdown (V28), null only for a row with no charge record. */
  totalCommission: number | null;
  commissionOnBasicFreight: number | null;
  branchCommissionOnOtherAmount: number | null;
  companyCommissionOnBasicFreight: number | null;
  status: ShipmentStatus;
  /** Null until `DeliveryAssignment.markDelivered` has run — the Delivery Report's own column. */
  deliveredAt?: string | null;
  createdDate?: string | null;
  version: number;
}

/** Full representation, with its item grid — mirrors backend `ShipmentResponse`. */
export interface ShipmentResponse {
  id: string;
  companyId: string;
  shipmentNumber: string;
  trackingNumber: string;
  bookingDate: string;
  bookingBranchId: string;
  deliveryBranchId: string;
  manifestId?: string | null;
  currentLocationId?: string | null;
  nextLocationId?: string | null;
  pickupPincode: string;
  deliveryPincode: string;
  senderName: string;
  senderAddress: string;
  senderContact: string;
  receiverName: string;
  receiverAddress: string;
  receiverContact: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  shipmentType: ShipmentType;
  expectedDeliveryDate?: string | null;
  actualWeight: number;
  volumetricWeight: number;
  chargeableWeight: number;
  declaredValue?: number | null;
  numberOfPackages: number;
  status: ShipmentStatus;
  remarks?: string | null;
  deliveredAt?: string | null;
  podPhotoUrl?: string | null;
  podSignatureUrl?: string | null;
  shipmentImageUrl?: string | null;
  /** Drives whether an E-Way Bill is mandatory — see `ShipmentEwayBillInfo`/`ewayBillRequired`. */
  invoiceValue?: number | null;
  /** Frozen at booking time from `invoiceValue` vs. the threshold in effect then — never
   *  recomputed against a later threshold change. */
  ewayBillRequired: boolean;
  /** The shipment's current E-Way Bill, if it has ever had one. */
  ewayBill?: ShipmentEwayBillInfo | null;
  createdBy?: string | null;
  createdByName?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
  items: ShipmentItem[];
}

/**
 * E-Way Bill Management (`com.courier.modules.ewaybill`) — integrated into Shipment
 * Booking: an invoice value over the company's own configurable threshold
 * (`CompanySettings.ewayBillMandatoryValue`, default 50000.00, see
 * `GET /company-settings`'s `ewayBill` section) makes an E-Way Bill mandatory before AWB
 * generation; at or under it, one is optional. The backend enforces this inside
 * `POST`/`PUT /shipments` — the frontend's own checks below are UX only, never the real
 * gate. See MEMORY/modules/eway-bill.md.
 */
export type EwayBillStatus =
  | 'NOT_REQUIRED' | 'REQUIRED' | 'PENDING' | 'UPLOADED' | 'VALIDATED' | 'INVALID'
  | 'EXPIRED' | 'CANCELLED';

/** The E-Way Bill data a booking screen supplies inline, in the same call that books or
 *  edits a shipment — mirrors backend `EwayBillBookingRequest`. Its own `invoiceValue`
 *  is not repeated here; the shipment's own `invoiceValue` carries it. */
export interface EwayBillBookingRequest {
  ewayBillNumber?: string | null;
  invoiceNumber: string;
  invoiceDate: string;
  documentType?: string | null;
  documentNumber?: string | null;
  documentDate?: string | null;
  transporterId?: string | null;
  vehicleNumber?: string | null;
  distance?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  documentUrl?: string | null;
  remarks?: string | null;
}

/** The shipment's current E-Way Bill, read-only — mirrors backend
 *  `ShipmentResponse.EwayBillInfo`, nested on `ShipmentResponse.ewayBill`. */
export interface ShipmentEwayBillInfo {
  id: string;
  ewayBillNumber?: string | null;
  status: EwayBillStatus;
  invoiceValue: number;
  validFrom?: string | null;
  validUntil?: string | null;
  documentUrl?: string | null;
}

/** Full standalone E-Way Bill record — mirrors backend `EwayBillResponse`, the
 *  `/eway-bills` CRUD/lifecycle endpoints used from Shipment Details post-booking. */
export interface EwayBill {
  id: string;
  shipmentId: string;
  ewayBillNumber?: string | null;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceValue: number;
  documentType: string;
  documentNumber?: string | null;
  documentDate?: string | null;
  transporterId?: string | null;
  vehicleNumber?: string | null;
  distance?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  status: EwayBillStatus;
  documentUrl?: string | null;
  remarks?: string | null;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Body of POST /eway-bills — attaches an E-Way Bill to an already-booked shipment. */
export interface CreateEwayBillRequest extends EwayBillBookingRequest {
  shipmentId: string;
  invoiceValue: number;
}

/** Body of PUT /eway-bills/{id}. */
export interface UpdateEwayBillRequest extends EwayBillBookingRequest {
  invoiceValue: number;
  version: number;
}

/** The Pricing Engine's own charge breakup, persisted at booking time — GET /shipments/{id}/charges. */
export interface ShipmentCharge {
  shipmentId: string;
  freight: number;
  fuelCharge: number;
  handlingCharge: number;
  odaCharge: number;
  insuranceCharge: number;
  gstAmount: number;
  discountAmount: number;
  roundOff: number;
  otherCharges: number;
  /** Commission breakdown (V28), computed from the booking branch's own charge percentages. */
  commissionOnBasicFreight: number;
  branchCommissionOnOtherAmount: number;
  companyCommissionOnBasicFreight: number;
  /** {@code commissionOnBasicFreight + branchCommissionOnOtherAmount} — the branch's total earning. */
  totalCommission: number;
  netAmount: number;
  matchedRouteId?: string | null;
  matchedRouteCode?: string | null;
  matchedRateId?: string | null;
  matchedRateCode?: string | null;
  /** The Freight Factor grid cell's own factor, or an accepted override of it — null unless
   *  this shipment priced through the Freight Factor fallback. */
  appliedFreightFactor?: number | null;
}

/** One entry of a shipment's status timeline — GET /shipments/{id}/history. */
export interface ShipmentStatusHistoryEntry {
  id: string;
  status: ShipmentStatus;
  previousStatus?: ShipmentStatus | null;
  remarks?: string | null;
  branchId?: string | null;
  manifestId?: string | null;
  vehicleId?: string | null;
  changedBy?: string | null;
  changedAt: string;
}

/** One uploaded document reference — GET/POST /shipments/{id}/documents. */
export interface ShipmentDocument {
  id: string;
  documentType: ShipmentDocumentType;
  documentName: string;
  documentUrl: string;
  remarks?: string | null;
  createdBy?: string | null;
  createdDate?: string | null;
}

/** One packed item on a booking/update request — mirrors backend `ShipmentItemRequest`. */
export interface ShipmentItemRequest {
  itemName: string;
  quantity?: number | null;
  weight: number;
  lengthCm?: number | null;
  widthCm?: number | null;
  heightCm?: number | null;
  declaredValue?: number | null;
  fragile?: boolean;
  dangerousGoods?: boolean;
}

/** Fields shared by create and update — mirrors the overlap of Create/UpdateShipmentRequest. */
export interface ShipmentFields {
  deliveryBranchId: string;
  pickupPincode: string;
  deliveryPincode: string;
  senderName: string;
  senderAddress: string;
  senderContact: string;
  receiverName: string;
  receiverAddress: string;
  receiverContact: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  shipmentType?: ShipmentType | null;
  bookingDate?: string | null;
  declaredValue?: number | null;
  numberOfPackages?: number | null;
  remarks?: string | null;
  /** Manual, typed at booking time — not computed by the Pricing Engine — added on top of
   *  its net amount server-side. See `ShipmentCharge.otherCharges`. */
  otherCharges?: number | null;
  /** Optional override of the Pricing Engine's own `ShipmentCharge.odaCharge` — typed at
   *  booking time when the operator needs to adjust it. Null uses the engine's own computed
   *  value unchanged; GST is recomputed server-side on the difference. */
  odaCharge?: number | null;
  /** Only meaningful when this lane falls back to the Freight Factor grid (no route/rate
   *  available) — raises the matched cell's own factor. Must be >= the matched factor;
   *  the server refuses a smaller value. See `PricingResponse.appliedFreightFactor`. */
  freightFactorOverride?: number | null;
  items?: ShipmentItemRequest[];
  actualWeight?: number | null;
  length?: number | null;
  width?: number | null;
  height?: number | null;
  /** Drives whether an E-Way Bill is mandatory — see `EwayBillBookingRequest`. Null is
   *  never mandatory. */
  invoiceValue?: number | null;
  /** Required when `invoiceValue` exceeds the company's own mandatory threshold — booking
   *  is refused with a 422 otherwise (backend-enforced; the frontend's own checks in
   *  shipment-create.ts are UX only). Optional and simply attached when supplied below it. */
  ewayBill?: EwayBillBookingRequest | null;
}

/** Body of POST /shipments — mirrors backend `CreateShipmentRequest`. */
export interface CreateShipmentRequest extends ShipmentFields {
  bookingBranchId: string;
  /** Optional — enter a specific shipment number instead of the auto-generated
   *  "<BRANCH_CODE>-<serial>" one. Must be unique within the company; booking is
   *  refused with a 422 if it's already in use. */
  manualShipmentNumber?: string | null;
  /** Route this shipment through one or more intermediate branches/hubs, in order,
   *  instead of straight to the delivery branch. When true, crossingBranchIds must carry
   *  at least one branch. */
  crossing?: boolean | null;
  /** The intermediate branches/hubs, in the order the shipment passes through them. */
  crossingBranchIds?: string[] | null;
  /** The whole route's crossing charge — not per hop. */
  crossingCharge?: number | null;
}

/** Body of PUT /shipments/{id} — mirrors backend `UpdateShipmentRequest`. `bookingBranchId`
 *  is absent — immutable once booked. Carries `version`. */
export interface UpdateShipmentRequest extends ShipmentFields {
  version: number;
}

/** Advanced-filter criteria for GET /shipments. All optional; merged into the page query. */
export interface ShipmentSearchRequest {
  status?: ShipmentStatus[];
  bookingBranchId?: string;
  deliveryBranchId?: string;
  manifestId?: string;
  bookingDateFrom?: string;
  bookingDateTo?: string;
  /** Delivery Report's own range — matched against when the shipment was actually
   *  delivered, not when it was booked. */
  deliveredDateFrom?: string;
  deliveredDateTo?: string;
  search?: string;
}

/** Unpaged aggregates for GET /shipments/summary — the Booking/Delivery Report summary row.
 *  Mirrors backend `ShipmentSummaryStatsResponse`. */
export interface ShipmentSummaryStats {
  totalCount: number;
  totalChargeableWeight: number;
  totalNetAmount: number;
  statusCounts: Partial<Record<ShipmentStatus, number>>;
}

/** One row of GET /shipments/commission-summary — the Commission Report's branch-wise
 *  summary table. Mirrors backend `BranchCommissionSummaryResponse`. */
export interface BranchCommissionSummary {
  bookingBranchId: string;
  shipmentCount: number;
  totalNetAmount: number;
  commissionOnBasicFreight: number;
  branchCommissionOnOtherAmount: number;
  companyCommissionOnBasicFreight: number;
  totalCommission: number;
}

/** One row of GET /shipments/branch-performance — the Branch Performance Report's
 *  per-branch summary table. Mirrors backend `BranchPerformanceSummaryResponse`. */
export interface BranchPerformanceSummary {
  bookingBranchId: string;
  shipmentCount: number;
  deliveredCount: number;
  inTransitCount: number;
  returnedCount: number;
  cancelledCount: number;
  totalChargeableWeight: number;
  totalNetAmount: number;
}

/** Body of POST /shipments/{id}/documents — mirrors backend `AddShipmentDocumentRequest`. */
export interface AddShipmentDocumentRequest {
  documentType: ShipmentDocumentType;
  documentName: string;
  documentUrl: string;
  remarks?: string | null;
}

/**
 * The Pricing Engine (`com.courier.modules.pricing`) has no frontend of its own — see
 * MEMORY/modules/pricing-engine.md's own Definition of Done — so its request/response
 * shapes live here, next to the one caller that needs a live preview before booking: the
 * wizard's Step 3. Mirrors backend `PricingRequest`/`PricingResponse` one-to-one.
 */
export interface PricingRequest {
  bookingBranchId: string;
  deliveryBranchId: string;
  pickupPincode: string;
  deliveryPincode: string;
  serviceTypeId: string;
  packageTypeId: string;
  paymentModeId: string;
  actualWeight: number;
  length?: number | null;
  width?: number | null;
  height?: number | null;
  declaredValue?: number | null;
  bookingDate?: string | null;
  discountPercentage?: number | null;
  discountAmount?: number | null;
  /** Only meaningful when this lane falls back to the Freight Factor grid — raises the
   *  matched cell's own factor. Must be >= the matched factor; a smaller value is refused. */
  freightFactorOverride?: number | null;
}

export interface ChargeBreakup {
  freight: number;
  fuelCharge: number;
  handlingCharge: number;
  odaCharge: number;
  insuranceCharge: number;
  gstAmount: number;
  discount: number;
  roundOff: number;
  netAmount: number;
}

export interface PricingResponse {
  bookingBranchId: string;
  deliveryBranchId: string;
  matchedRouteId?: string | null;
  matchedRouteCode?: string | null;
  matchedRateId?: string | null;
  matchedRateCode?: string | null;
  matchedRateName?: string | null;
  actualWeight: number;
  volumetricWeight: number;
  chargeableWeight: number;
  weightUnit: string;
  /** The Freight Factor grid cell's own factor, or an accepted override of it — set only
   *  on the Freight Factor fallback (matchedRouteCode/matchedRateCode both null/absent). */
  appliedFreightFactor?: number | null;
  chargeBreakup: ChargeBreakup;
}

/**
 * Shipment Movement (V19) — Booking Branch -> Create Manifest (Loading Sheet Created) ->
 * Trip Hire Challan (THC) -> Delivery Branch -> In Scan -> Out For Delivery -> Delivered. Mirrors
 * `com.courier.modules.manifest`/`com.courier.modules.shipment`'s movement additions
 * one-to-one. See MEMORY/modules/shipment-movement.md.
 */

export type ManifestStatus = 'CREATED' | 'DISPATCHED' | 'COMPLETED';

/** Mirrors backend `ManifestResponse` — GET/POST /manifests. */
export interface Manifest {
  id: string;
  manifestNumber: string;
  bookingBranchId: string;
  deliveryBranchId: string;
  vehicleId?: string | null;
  driverUserId?: string | null;
  status: ManifestStatus;
  dispatchedAt?: string | null;
  departureTime?: string | null;
  completedAt?: string | null;
  remarks?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  version: number;
  shipmentCount: number;
  totalWeight: number;
  totalPackages: number;
}

/** Body of POST /manifests — mirrors backend `CreateManifestRequest`. */
export interface CreateManifestRequest {
  bookingBranchId: string;
  deliveryBranchId: string;
  shipmentIds: string[];
  remarks?: string | null;
}

/** GET /manifests/summary — the THC Report summary row. Mirrors backend
 *  `ManifestSummaryStatsResponse`. */
export interface ManifestSummaryStats {
  totalManifests: number;
  totalShipments: number;
  totalWeight: number;
  totalPackages: number;
}

export interface ManifestSearchRequest {
  status?: ManifestStatus;
  bookingBranchId?: string;
  deliveryBranchId?: string;
  search?: string;
}

export type VehicleType = 'BIKE' | 'SCOOTER' | 'AUTO' | 'VAN' | 'PICKUP' | 'TRUCK' | 'TEMPO' | 'OTHER';
export type FuelType = 'PETROL' | 'DIESEL' | 'CNG' | 'EV' | 'OTHER';
export type VehicleStatus = 'AVAILABLE' | 'IN_USE' | 'MAINTENANCE' | 'INACTIVE';

/** Mirrors backend `VehicleResponse` — the fleet Trip Hire Challan (THC)'s "Assign Vehicle" picker reads. */
export interface Vehicle {
  id: string;
  companyId: string;
  vehicleNumber: string;
  vehicleType: VehicleType;
  make?: string | null;
  model?: string | null;
  fuelType?: FuelType | null;
  capacityKg?: number | null;
  currentOdometer?: number | null;
  purchaseDate?: string | null;
  registrationDate?: string | null;
  insuranceExpiry?: string | null;
  pucExpiry?: string | null;
  fitnessExpiry?: string | null;
  permitExpiry?: string | null;
  status: VehicleStatus;
  branchId?: string | null;
  remarks?: string | null;
  active: boolean;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedBy?: string | null;
  updatedAt?: string | null;
  version: number;
}

export interface CreateVehicleRequest {
  vehicleNumber: string;
  vehicleType: VehicleType;
  make?: string | null;
  model?: string | null;
  fuelType?: FuelType | null;
  capacityKg?: number | null;
  currentOdometer?: number | null;
  purchaseDate?: string | null;
  registrationDate?: string | null;
  insuranceExpiry?: string | null;
  pucExpiry?: string | null;
  fitnessExpiry?: string | null;
  permitExpiry?: string | null;
  branchId?: string | null;
  remarks?: string | null;
}

export interface UpdateVehicleRequest {
  vehicleNumber: string;
  vehicleType: VehicleType;
  make?: string | null;
  model?: string | null;
  fuelType?: FuelType | null;
  capacityKg?: number | null;
  currentOdometer?: number | null;
  purchaseDate?: string | null;
  registrationDate?: string | null;
  insuranceExpiry?: string | null;
  pucExpiry?: string | null;
  fitnessExpiry?: string | null;
  permitExpiry?: string | null;
  status: VehicleStatus;
  branchId?: string | null;
  remarks?: string | null;
  version: number;
}

/** Bulk-operation per-item result — mirrors backend `MovementOutcomeResponse`. */
export interface MovementOutcome {
  reference: string;
  success: boolean;
  message?: string | null;
}

/** Mirrors backend `BulkMovementResponse` — In Scan/Out For Delivery both return this.
 *  `drsNumber` is only ever set on Out For Delivery's response — null for In Scan.
 *  `shortageTicketNumber` is only ever set on In Scan's response, and only when it was
 *  called with a non-empty `missingTrackingNumbers` — null otherwise. */
export interface BulkMovementResult {
  results: MovementOutcome[];
  successCount: number;
  failureCount: number;
  drsNumber: string | null;
  shortageTicketNumber: string | null;
}

/** Body of POST /shipment-movement/dispatch. */
export interface DispatchManifestRequest {
  manifestId: string;
  vehicleId: string;
  driverUserId: string;
  departureTime?: string | null;
}

export interface DispatchManifestResponse {
  manifestId: string;
  manifestNumber: string;
  status: ManifestStatus;
  vehicleId: string;
  driverUserId: string;
  dispatchedAt?: string | null;
  departureTime?: string | null;
  shipmentCount: number;
}

/** Body of POST /shipment-movement/in-scan. `manifestNumber` is descriptive only (a
 *  shortage ticket's subject/description); a non-empty `missingTrackingNumbers`
 *  auto-raises that ticket. */
export interface InScanRequest {
  receivingBranchId: string;
  trackingNumbers: string[];
  manifestNumber?: string | null;
  missingTrackingNumbers?: string[];
}

/** Body of POST /shipment-movement/out-for-delivery. */
export interface OutForDeliveryRequest {
  shipmentIds: string[];
  deliveryUserId: string;
}

/** Body of POST /shipment-movement/deliver. */
export interface DeliverRequest {
  shipmentId: string;
  receiverName: string;
  remarks?: string | null;
  otp?: string | null;
  signatureUrl?: string | null;
  photoUrl?: string | null;
}

/** One step of GET /shipments/{id}/timeline — mirrors backend `TimelineStepResponse`. */
export interface TimelineStep {
  status: ShipmentStatus;
  label: string;
  changedAt?: string | null;
  changedBy?: string | null;
  completed: boolean;
}

/** One row of GET /shipment-movement/drs — a DRS "run" (delivery user + delivery branch +
 *  calendar day), grouped server-side from `DeliveryAssignment` rows. There is no separate
 *  DRS/batch table — see `MEMORY/modules/shipment-movement.md`. Mirrors `DrsSummaryResponse`. */
export interface DrsSummary {
  deliveryUserId: string;
  deliveryBranchId: string;
  runDate: string;
  drsNumber: string | null;
  shipmentCount: number;
  deliveredCount: number;
  pendingCount: number;
}

/** One shipment row of GET /shipment-movement/drs/detail — mirrors `DrsShipmentRowResponse`. */
export interface DrsShipmentRow {
  shipmentId: string;
  shipmentNumber: string;
  trackingNumber: string;
  receiverName: string;
  receiverContact: string;
  paymentModeId: string;
  netAmount: number | null;
  status: ShipmentStatus;
  deliveredAt?: string | null;
}

/** Body of GET /shipment-movement/drs/detail — mirrors `DrsDetailResponse`. */
export interface DrsDetail {
  deliveryUserId: string;
  deliveryBranchId: string;
  runDate: string;
  drsNumber: string | null;
  shipments: DrsShipmentRow[];
}
