export type IncidentRule =
  | 'REFRESH_TOKEN_REUSE' | 'FOREIGN_LOGIN_CORRECT_PASSWORD' | 'FOREIGN_TOKEN_USE' | 'REVOKED_TOKEN_REPLAY'
  | 'SESSION_AGENT_CHANGE' | 'SESSION_MULTI_IP' | 'BULK_ACTIVITY' | 'ACCESS_DENIED_BURST'
  | 'CREDENTIAL_ATTACK' | 'FOREIGN_LOGIN_ATTEMPT';
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'BANNED' | 'DISMISSED' | 'UNBANNED';
export type IncidentAction = 'ban' | 'dismiss' | 'unban';

/** Mirrors backend IncidentResponse. User/branch fields are empty for IP-level incidents. */
export interface SecurityIncident {
  id: string;
  companyId: string | null;
  rule: IncidentRule;
  severity: IncidentSeverity;
  status: IncidentStatus;
  autoAction: string | null;
  detectedAt: string;
  userId: string | null;
  userDisplayName: string | null;
  userEmail: string | null;
  userRoles: string[];
  branchName: string | null;
  evidence: Record<string, unknown>;
  evidenceUnreadable: boolean;
  resolvedBy: string | null;
  resolvedByName: string | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
}

export interface IncidentFilter {
  status?: IncidentStatus;
  severity?: IncidentSeverity;
  rule?: IncidentRule;
  userId?: string;
  page?: number;
  size?: number;
}

export type IncidentSummary = Record<IncidentStatus, number>;
