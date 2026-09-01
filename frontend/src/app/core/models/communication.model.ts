/**
 * Communication Center models — mirror the backend `com.courier.modules.communication`
 * module one-to-one (see MEMORY/modules/communication.md). No mock shapes: every field is
 * returned by, or accepted by, an endpoint.
 */

export type CommunicationChannel = 'WHATSAPP' | 'SMS' | 'EMAIL';
export const COMMUNICATION_CHANNELS: CommunicationChannel[] = ['WHATSAPP', 'SMS', 'EMAIL'];

export type CommunicationEventType =
  | 'SHIPMENT_BOOKED' | 'SHIPMENT_DISPATCHED' | 'SHIPMENT_RECEIVED' | 'OUT_FOR_DELIVERY'
  | 'SHIPMENT_DELIVERED' | 'SHIPMENT_CANCELLED' | 'RTO_INITIATED' | 'RTO_DELIVERED';

export const COMMUNICATION_EVENT_TYPES: CommunicationEventType[] = [
  'SHIPMENT_BOOKED', 'SHIPMENT_DISPATCHED', 'SHIPMENT_RECEIVED', 'OUT_FOR_DELIVERY',
  'SHIPMENT_DELIVERED', 'SHIPMENT_CANCELLED', 'RTO_INITIATED', 'RTO_DELIVERED'
];

export type TemplateStatus = 'ACTIVE' | 'INACTIVE';

export type CommunicationStatus = 'PENDING' | 'SENT' | 'DELIVERED' | 'FAILED' | 'CANCELLED';

/** Mirrors backend `CommunicationTemplateResponse`. */
export interface CommunicationTemplate {
  id: string;
  eventType: CommunicationEventType;
  channel: CommunicationChannel;
  templateName: string;
  subject?: string | null;
  content: string;
  status: TemplateStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
  version: number;
}

/** Mirrors backend `CreateCommunicationTemplateRequest`. */
export interface CreateCommunicationTemplateRequest {
  eventType: CommunicationEventType;
  channel: CommunicationChannel;
  templateName: string;
  subject?: string | null;
  content: string;
}

/** Mirrors backend `UpdateCommunicationTemplateRequest`. */
export interface UpdateCommunicationTemplateRequest {
  templateName: string;
  subject?: string | null;
  content: string;
  status: TemplateStatus;
  version: number;
}

/** Mirrors backend `CommunicationTemplatePreviewResponse`. */
export interface CommunicationTemplatePreview {
  subject?: string | null;
  content: string;
}

/** Mirrors backend `CommunicationSettingResponse`. Never carries a secret — only whether
 *  one is set. */
export interface CommunicationSetting {
  id: string;
  channel: CommunicationChannel;
  enabled: boolean;
  provider?: string | null;
  config: Record<string, string>;
  secretConfigured: boolean;
  updatedAt?: string | null;
}

/** Mirrors backend `UpsertCommunicationSettingRequest`. `secret` blank/omitted keeps the
 *  one already stored. */
export interface UpsertCommunicationSettingRequest {
  enabled: boolean;
  provider?: string | null;
  config: Record<string, string>;
  secret?: string | null;
}

export interface ConnectionTestResult {
  ok: boolean;
  message: string;
}

/** Mirrors backend `CommunicationLogResponse`. */
export interface CommunicationLog {
  id: string;
  shipmentId: string;
  customerId?: string | null;
  eventType: CommunicationEventType;
  channel: CommunicationChannel;
  recipient: string;
  templateId?: string | null;
  status: CommunicationStatus;
  providerMessageId?: string | null;
  errorMessage?: string | null;
  attemptCount: number;
  lastAttemptAt?: string | null;
  nextRetryAt?: string | null;
  sentAt?: string | null;
  createdAt?: string | null;
}

/** Advanced-filter criteria for GET /communication/logs. All optional. */
export interface CommunicationLogSearchRequest {
  shipmentId?: string;
  customerId?: string;
  eventType?: CommunicationEventType;
  channel?: CommunicationChannel;
  status?: CommunicationStatus;
}

export interface CommunicationChannelStats {
  sent: number;
  delivered: number;
  failed: number;
  pending: number;
  cancelled: number;
}

/** Mirrors backend `CommunicationDashboardResponse`. */
export interface CommunicationDashboard {
  totalSent: number;
  totalDelivered: number;
  totalFailed: number;
  totalPending: number;
  channels: Partial<Record<CommunicationChannel, CommunicationChannelStats>>;
}
