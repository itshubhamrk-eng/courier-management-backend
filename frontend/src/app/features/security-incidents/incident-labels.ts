import { IncidentRule, IncidentSeverity, IncidentStatus } from './security-incident.model';

/** The one place that turns incident codes into words an administrator can read. */
export const RULE_INFO: Record<IncidentRule, { label: string; description: string }> = {
  REFRESH_TOKEN_REUSE: {
    label: 'Refresh token reused',
    description: 'A sign-in token that had already been replaced was presented again, which usually means a copy of it was stolen.'
  },
  FOREIGN_LOGIN_CORRECT_PASSWORD: {
    label: 'Foreign sign-in with correct password',
    description: 'Someone signed in from outside the allowed countries with the right password, so the credentials may be compromised.'
  },
  FOREIGN_TOKEN_USE: {
    label: 'Session used from a foreign location',
    description: 'An existing session was used from outside the allowed countries, which can mean the session was hijacked.'
  },
  REVOKED_TOKEN_REPLAY: {
    label: 'Revoked session replayed',
    description: 'A session that had already been signed out or revoked was used again several times.'
  },
  SESSION_AGENT_CHANGE: {
    label: 'Device changed mid-session',
    description: 'One session suddenly appeared to come from a different browser or device, which can mean it was copied elsewhere.'
  },
  SESSION_MULTI_IP: {
    label: 'Session used from many addresses',
    description: 'One session was used from several different network addresses in a short time.'
  },
  BULK_ACTIVITY: {
    label: 'Unusual bulk activity',
    description: 'The user performed far more reads or exports than normal in a short time, which can indicate data scraping.'
  },
  ACCESS_DENIED_BURST: {
    label: 'Burst of denied requests',
    description: 'The user repeatedly tried things they are not permitted to do, which can indicate probing for access.'
  },
  CREDENTIAL_ATTACK: {
    label: 'Credential attack',
    description: 'Many failed sign-in attempts were made against this account or from this address, which looks like password guessing.'
  },
  FOREIGN_LOGIN_ATTEMPT: {
    label: 'Foreign sign-in attempt',
    description: 'A sign-in was attempted from outside the allowed countries. It was blocked.'
  }
};

export const INCIDENT_RULE_CODES = Object.keys(RULE_INFO) as IncidentRule[];
export const INCIDENT_SEVERITIES: IncidentSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const INCIDENT_STATUSES: IncidentStatus[] = ['OPEN', 'BANNED', 'DISMISSED', 'UNBANNED'];

export const ruleLabel = (rule: string): string => RULE_INFO[rule as IncidentRule]?.label ?? rule;
export const ruleDescription = (rule: string): string =>
  RULE_INFO[rule as IncidentRule]?.description ?? 'No description is available for this rule.';

export const SEVERITY_TONE: Record<IncidentSeverity, 'info' | 'warning' | 'danger'> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger'
};
export const STATUS_TONE: Record<IncidentStatus, 'danger' | 'neutral' | 'info' | 'success'> = {
  OPEN: 'danger', BANNED: 'neutral', DISMISSED: 'info', UNBANNED: 'success'
};

/** Evidence values rendered as text: strings as-is, nested objects/arrays as JSON. */
export function evidenceText(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/** "failedCount" -> "Failed count". */
export function humanKey(key: string): string {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
