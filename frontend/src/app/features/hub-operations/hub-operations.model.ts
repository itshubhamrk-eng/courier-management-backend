/**
 * Hub Operations models — mirror the backend `com.courier.modules.crossing` one-to-one
 * (see MEMORY/modules/hub-operations.md). A hub itself is a `Branch` with
 * `branchType: 'HUB'` (see `@core/models/branch.model.ts`) — no separate Hub model.
 * In-scan, Load Sheet/Sorting, vehicle+driver and dispatch reuse the existing
 * Shipment/Manifest models; only out-scan and exceptions are genuinely new.
 */

export type HubExceptionType = 'MISSING' | 'DAMAGED' | 'SHORT' | 'WRONG_DESTINATION' | 'MISROUTED' | 'ON_HOLD';
export const HUB_EXCEPTION_TYPES: HubExceptionType[] =
  ['MISSING', 'DAMAGED', 'SHORT', 'WRONG_DESTINATION', 'MISROUTED', 'ON_HOLD'];

export type HubExceptionStatus = 'OPEN' | 'RESOLVED';

/** Mirrors backend `ShipmentExceptionResponse`. Never implies a change to the
 *  shipment's own status — see the backend entity's own doc for why. */
export interface HubException {
  id: string;
  shipmentId: string;
  hubBranchId: string;
  exceptionType: HubExceptionType;
  status: HubExceptionStatus;
  remarks?: string | null;
  raisedBy?: string | null;
  raisedAt: string;
  resolvedBy?: string | null;
  resolvedAt?: string | null;
  resolutionRemarks?: string | null;
  ticketId?: string | null;
}

/** Body of POST /hub-operations/exceptions. */
export interface RaiseExceptionRequest {
  shipmentId: string;
  hubBranchId: string;
  exceptionType: HubExceptionType;
  remarks?: string | null;
}

/** Body of PATCH /hub-operations/exceptions/{id}/resolve. */
export interface ResolveExceptionRequest {
  resolutionRemarks?: string | null;
}

export interface ExceptionSearchRequest {
  shipmentId?: string;
  hubBranchId?: string;
  status?: HubExceptionStatus;
}

/** Body of POST /hub-operations/out-scan. */
export interface OutScanRequest {
  manifestId: string;
  hubBranchId: string;
  trackingNumbers: string[];
}

/** Mirrors backend `HubDashboardResponse` — the nine Hub Dashboard figures for one hub. */
export interface HubDashboardStats {
  todaysInbound: number;
  pendingInScan: number;
  shipmentsAtHub: number;
  pendingSorting: number;
  readyForDispatch: number;
  dispatchedToday: number;
  pendingExceptions: number;
  todaysLoadSheets: number;
}
