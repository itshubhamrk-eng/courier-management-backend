/**
 * Follow-up Management models — mirror the backend `com.courier.modules.followup`
 * module one-to-one. No mock shapes: every field is returned by, or accepted by, an
 * endpoint. Distinct from Ticket Support: a follow-up is a branch user's internal
 * "take manual action by this date" reminder, not an external support issue.
 */

export type FollowUpType = 'CUSTOMER' | 'SHIPMENT' | 'DELIVERY' | 'PAYMENT' | 'EXCEPTION' | 'GENERAL';
export const FOLLOW_UP_TYPES: FollowUpType[] =
  ['CUSTOMER', 'SHIPMENT', 'DELIVERY', 'PAYMENT', 'EXCEPTION', 'GENERAL'];

export type FollowUpPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export const FOLLOW_UP_PRIORITIES: FollowUpPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export type FollowUpStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'RESCHEDULED' | 'CANCELLED';
export const FOLLOW_UP_STATUSES: FollowUpStatus[] =
  ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'RESCHEDULED', 'CANCELLED'];
/** Statuses reachable from any non-terminal follow-up via the plain status action —
 *  RESCHEDULED has its own dedicated action/endpoint, not this one. */
export const MOVEABLE_FOLLOW_UP_STATUSES: FollowUpStatus[] = ['IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

export type FollowUpHistoryAction = 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'RESCHEDULED' | 'ASSIGNED' | 'NOTE_ADDED';

/** Flat, ids only — mirrors backend `FollowUpResponse`; the frontend resolves labels itself. */
export interface FollowUp {
  id: string;
  companyId: string;
  branchId: string;
  referenceType: FollowUpType;
  referenceId?: string | null;
  customerId?: string | null;
  shipmentId?: string | null;
  assignedUserId?: string | null;
  title: string;
  description?: string | null;
  followUpType: FollowUpType;
  priority: FollowUpPriority;
  status: FollowUpStatus;
  dueDate: string;
  nextFollowUpDate?: string | null;
  /** Computed server-side: past due date and not COMPLETED/CANCELLED. */
  overdue: boolean;
  completedAt?: string | null;
  completedBy?: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface FollowUpHistoryEntry {
  id: string;
  action: FollowUpHistoryAction;
  fromStatus?: FollowUpStatus | null;
  toStatus?: FollowUpStatus | null;
  previousDueDate?: string | null;
  newDueDate?: string | null;
  assignedToUserId?: string | null;
  note?: string | null;
  changedByUserId?: string | null;
  createdAt: string;
}

export interface CreateFollowUpRequest {
  branchId?: string | null;
  referenceType?: FollowUpType | null;
  referenceId?: string | null;
  customerId?: string | null;
  shipmentId?: string | null;
  assignedUserId?: string | null;
  title: string;
  description?: string | null;
  followUpType?: FollowUpType | null;
  priority?: FollowUpPriority | null;
  dueDate: string;
}

export interface UpdateFollowUpRequest {
  branchId?: string | null;
  referenceType?: FollowUpType | null;
  referenceId?: string | null;
  customerId?: string | null;
  shipmentId?: string | null;
  title: string;
  description?: string | null;
  followUpType?: FollowUpType | null;
  priority?: FollowUpPriority | null;
  dueDate: string;
  version?: number | null;
}

export interface ChangeFollowUpStatusRequest {
  status: FollowUpStatus;
  remarks?: string | null;
}

export interface RescheduleFollowUpRequest {
  newDueDate: string;
  reason?: string | null;
}

export interface AssignFollowUpRequest {
  assignedUserId: string;
  remarks?: string | null;
}

export interface AddFollowUpNoteRequest {
  note: string;
}

/** Query params of GET /follow-ups — merges into PageQuery like every other search. */
export interface FollowUpSearchRequest {
  status?: FollowUpStatus;
  priority?: FollowUpPriority;
  type?: FollowUpType;
  assignedUser?: string;
  dueDate?: string;
  overdue?: boolean;
  customer?: string;
  shipment?: string;
  branch?: string;
  search?: string;
}

/** Mirrors backend `FollowUpDashboardStats` — backs the Operations Dashboard widget. */
export interface FollowUpDashboardStats {
  overdue: number;
  dueToday: number;
  upcoming: number;
  urgent: number;
}
