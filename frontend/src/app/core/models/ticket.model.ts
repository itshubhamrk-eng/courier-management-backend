/**
 * Ticket Support models — mirror the backend `com.courier.modules.support` module
 * one-to-one (Phase 1: lifecycle, conversation, categories, dashboard; Phase 2: SLA
 * rules + in-app notifications). No mock shapes: every field is returned by, or
 * accepted by, an endpoint.
 */

/** ON_TRACK/WARNING only ever apply to an open ticket; MET/BREACHED to a closed one;
 *  NO_SLA when the company has no active rule for this ticket's priority. */
export type SlaStatus = 'ON_TRACK' | 'WARNING' | 'BREACHED' | 'MET' | 'NO_SLA';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export type TicketStatus =
  | 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'WAITING_FOR_USER' | 'WAITING_FOR_INTERNAL_TEAM'
  | 'RESOLVED' | 'CLOSED' | 'REOPENED';
export const TICKET_STATUSES: TicketStatus[] =
  ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_FOR_USER', 'WAITING_FOR_INTERNAL_TEAM', 'RESOLVED', 'CLOSED', 'REOPENED'];

export type TicketAssignmentAction = 'ASSIGNED' | 'REASSIGNED' | 'ESCALATED';

/** Flat, ids only — mirrors backend `TicketResponse`; the frontend resolves labels itself. */
export interface Ticket {
  id: string;
  companyId: string;
  ticketNumber: string;
  subject: string;
  description: string;
  categoryId: string;
  subCategoryId?: string | null;
  priority: TicketPriority;
  status: TicketStatus;
  relatedShipmentId?: string | null;
  relatedCustomerId?: string | null;
  relatedBranchId?: string | null;
  /** Null for a system-raised ticket (e.g. an SLA breach auto-ticket). */
  createdByUserId: string | null;
  assigneeUserId?: string | null;
  escalated: boolean;
  firstResponseAt?: string | null;
  slaFirstResponseDueAt?: string | null;
  slaResolutionDueAt?: string | null;
  slaStatus: SlaStatus;
  resolvedAt?: string | null;
  closedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface TicketMessage {
  id: string;
  ticketId: string;
  authorUserId: string;
  body: string;
  internalNote: boolean;
  createdAt: string;
}

export interface TicketAttachment {
  id: string;
  ticketId: string;
  messageId?: string | null;
  assetUrl: string;
  filename: string;
  contentType?: string | null;
  sizeBytes: number;
  uploadedByUserId: string;
  createdAt: string;
}

export interface TicketStatusHistoryEntry {
  id: string;
  fromStatus?: TicketStatus | null;
  toStatus: TicketStatus;
  changedByUserId: string;
  remarks?: string | null;
  createdAt: string;
}

export interface TicketAssignmentHistoryEntry {
  id: string;
  assignedToUserId: string;
  assignedByUserId: string;
  action: TicketAssignmentAction;
  remarks?: string | null;
  createdAt: string;
}

export interface TicketDetail {
  ticket: Ticket;
  messages: TicketMessage[];
  attachments: TicketAttachment[];
  statusHistory: TicketStatusHistoryEntry[];
  assignmentHistory: TicketAssignmentHistoryEntry[];
}

export interface TicketCategory {
  id: string;
  name: string;
  active: boolean;
  version: number;
}

export interface TicketSubCategory {
  id: string;
  categoryId: string;
  name: string;
  active: boolean;
  version: number;
}

export interface CreateTicketRequest {
  subject: string;
  description: string;
  categoryId: string;
  subCategoryId?: string | null;
  priority?: TicketPriority | null;
  relatedShipmentId?: string | null;
  relatedCustomerId?: string | null;
  relatedBranchId?: string | null;
  /** SUPER_ADMIN only. */
  companyId?: string | null;
}

export interface ReplyRequest {
  body: string;
  internalNote: boolean;
}

export interface AssignmentRequest {
  assigneeUserId?: string | null;
  remarks?: string | null;
}

export interface ChangeStatusRequest {
  status: TicketStatus;
  remarks?: string | null;
}

export interface ChangePriorityRequest {
  priority: TicketPriority;
  remarks?: string | null;
}

export interface ChangeCategoryRequest {
  categoryId: string;
  subCategoryId?: string | null;
  remarks?: string | null;
}

export interface RemarksRequest {
  remarks?: string | null;
}

/** Query params of GET /support/tickets — merges into PageQuery like every other search. */
export interface TicketSearchRequest {
  status?: TicketStatus;
  priority?: TicketPriority;
  categoryId?: string;
  subCategoryId?: string;
  relatedBranchId?: string;
  assigneeUserId?: string;
  createdFrom?: string;
  createdTo?: string;
  search?: string;
  /** SUPER_ADMIN only — narrows the cross-tenant view to one company. */
  companyId?: string;
}

/** Mirrors backend `TicketDashboardStats`. */
export interface TicketDashboardStats {
  totalTickets: number;
  openTickets: number;
  assignedTickets: number;
  inProgress: number;
  waitingForUser: number;
  waitingForInternalTeam: number;
  criticalTickets: number;
  resolvedToday: number;
  closedToday: number;
  averageResolutionHours: number | null;
  byStatus: Record<string, number>;
  byPriority: Record<string, number>;
  byCategory: Record<string, number>;
  byBranch: Record<string, number>;
  byAgent: Record<string, number>;
  byTenant: Record<string, number>;
  volumeTrend: { date: string; total: number }[];
  /** Open tickets past SLA resolution due date; 0 for cross-tenant SUPER_ADMIN view
   *  (SLA targets are company-scoped, mixing them is meaningless). */
  slaBreached: number;
  /** Keyed by SlaStatus bucket name, open tickets only. */
  slaPerformance: Record<string, number>;
}

/** Mirrors backend `SlaRuleResponse` — one row per TicketPriority. */
export interface SlaRule {
  id: string;
  companyId: string;
  priority: TicketPriority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
  active: boolean;
  version: number;
}

export interface UpsertSlaRuleRequest {
  priority: TicketPriority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
}

export type NotificationType =
  | 'TICKET_ASSIGNED' | 'TICKET_REASSIGNED' | 'TICKET_ESCALATED' | 'NEW_REPLY'
  | 'INTERNAL_UPDATE' | 'STATUS_CHANGED' | 'PRIORITY_CHANGED' | 'TICKET_RESOLVED'
  | 'TICKET_CLOSED' | 'TICKET_REOPENED' | 'SLA_APPROACHING' | 'SLA_BREACHED'
  // Follow-up Management (V44) — reuses this same in-app feed.
  | 'FOLLOWUP_ASSIGNED' | 'FOLLOWUP_DUE_TODAY' | 'FOLLOWUP_OVERDUE' | 'FOLLOWUP_URGENT';

/** Mirrors backend NotificationResponse. */
export interface AppNotification {
  id: string;
  companyId: string;
  recipientUserId: string;
  type: NotificationType;
  title: string;
  message: string;
  ticketId?: string | null;
  followUpId?: string | null;
  read: boolean;
  createdAt: string;
}
