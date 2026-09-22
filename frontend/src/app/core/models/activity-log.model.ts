/** Mirrors the backend's ActivityLog — the automatic, request-shaped activity trail.
 *  See MEMORY/modules/activity-log.md. */
export type ActivityStatus = 'SUCCESS' | 'FAILURE';

export interface ActivityLog {
  id: string;
  companyId?: string | null;
  userId?: string | null;
  username?: string | null;
  module?: string | null;
  submodule?: string | null;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  description?: string | null;
  oldValue?: string | null;
  newValue?: string | null;
  ipAddress?: string | null;
  device?: string | null;
  browser?: string | null;
  os?: string | null;
  sessionId?: string | null;
  requestMethod?: string | null;
  apiEndpoint?: string | null;
  status: ActivityStatus;
  errorMessage?: string | null;
  occurredAt: string;
}

/** Query params for GET /activity-logs. Every field optional. */
export interface ActivityLogSearchQuery {
  page?: number;
  size?: number;
  sort?: string;
  userId?: string;
  module?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  status?: ActivityStatus;
  sessionId?: string;
  dateFrom?: string;
  dateTo?: string;
  search?: string;
}

export type LoginEventType = 'LOGIN_SUCCESS' | 'LOGIN_FAILED' | 'LOGOUT' | 'SESSION_EXPIRED';

export interface LoginHistoryEntry {
  id: string;
  userId?: string | null;
  attemptedEmail: string;
  eventType: LoginEventType;
  success: boolean;
  failureReason?: string | null;
  sessionId?: string | null;
  ipAddress?: string | null;
  device?: string | null;
  browser?: string | null;
  os?: string | null;
  occurredAt: string;
  logoutAt?: string | null;
}

export interface ActiveSession {
  id: string;
  deviceName?: string | null;
  deviceType?: string | null;
  browser?: string | null;
  os?: string | null;
  ipAddress?: string | null;
  lastSeenAt: string;
  expiresAt: string;
  rememberMe: boolean;
}

export interface UserActivitySummary {
  loginHistory: LoginHistoryEntry[];
  recentActivities: ActivityLog[];
  modulesAccessed: string[];
  lastActivityAt?: string | null;
  activeSessions: ActiveSession[];
}

export function loginEventLabel(type: LoginEventType): string {
  switch (type) {
    case 'LOGIN_SUCCESS': return 'Login';
    case 'LOGIN_FAILED': return 'Failed Login';
    case 'LOGOUT': return 'Logout';
    case 'SESSION_EXPIRED': return 'Session Expired';
  }
}
