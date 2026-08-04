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
 * `OUT_SCAN` state back into `MANIFEST_CREATED` — one milestone ("out scan created"),
 * not two; adding a shipment to a manifest already is the out-scan action. See backend
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
  manifestId?: string | null;
  senderName: string;
  senderContact: string;
  receiverName: string;
  receiverContact: string;
  chargeableWeight: number;
  /** Null only for a row with no charge record — see backend `ShipmentSummaryResponse`. */
  netAmount: number | null;
  status: ShipmentStatus;
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
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
  items: ShipmentItem[];
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
  netAmount: number;
  matchedRouteId?: string | null;
  matchedRouteCode?: string | null;
  matchedRateId?: string | null;
  matchedRateCode?: string | null;
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
  items?: ShipmentItemRequest[];
  actualWeight?: number | null;
  length?: number | null;
  width?: number | null;
  height?: number | null;
}

/** Body of POST /shipments — mirrors backend `CreateShipmentRequest`. */
export interface CreateShipmentRequest extends ShipmentFields {
  bookingBranchId: string;
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
  search?: string;
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
  chargeBreakup: ChargeBreakup;
}

/**
 * Shipment Movement (V19) — Booking Branch -> Create Manifest -> Assign Vehicle -> Out
 * Scan -> Dispatch -> Delivery Branch -> In Scan -> Out For Delivery -> Delivered. Mirrors
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
  completedAt?: string | null;
  remarks?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  version: number;
}

/** Body of POST /manifests — mirrors backend `CreateManifestRequest`. */
export interface CreateManifestRequest {
  bookingBranchId: string;
  deliveryBranchId: string;
  shipmentIds: string[];
  remarks?: string | null;
}

export interface ManifestSearchRequest {
  status?: ManifestStatus;
  bookingBranchId?: string;
  deliveryBranchId?: string;
  search?: string;
}

export type VehicleStatus = 'ACTIVE' | 'INACTIVE';

/** Mirrors backend `VehicleResponse` — the fleet Dispatch's "Assign Vehicle" picker reads. */
export interface Vehicle {
  id: string;
  vehicleNumber: string;
  vehicleTypeId?: string | null;
  capacityKg?: number | null;
  status: VehicleStatus;
  remarks?: string | null;
  version: number;
}

export interface CreateVehicleRequest {
  vehicleNumber: string;
  vehicleTypeId?: string | null;
  capacityKg?: number | null;
  remarks?: string | null;
}

/** Bulk-operation per-item result — mirrors backend `MovementOutcomeResponse`. */
export interface MovementOutcome {
  reference: string;
  success: boolean;
  message?: string | null;
}

/** Mirrors backend `BulkMovementResponse` — In Scan/Out For Delivery both return this. */
export interface BulkMovementResult {
  results: MovementOutcome[];
  successCount: number;
  failureCount: number;
}

/** Body of POST /shipment-movement/dispatch. */
export interface DispatchManifestRequest {
  manifestId: string;
  vehicleId: string;
  driverUserId: string;
}

export interface DispatchManifestResponse {
  manifestId: string;
  manifestNumber: string;
  status: ManifestStatus;
  vehicleId: string;
  driverUserId: string;
  dispatchedAt?: string | null;
  shipmentCount: number;
}

/** Body of POST /shipment-movement/in-scan. */
export interface InScanRequest {
  receivingBranchId: string;
  trackingNumbers: string[];
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
