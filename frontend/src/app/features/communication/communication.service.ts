import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Page, PageQuery } from '@core/models/page.model';
import {
  CommunicationChannel, CommunicationDashboard, DashboardPeriod, FetchedRates, CommunicationLog, CommunicationLogSearchRequest,
  CommunicationEventType, CommunicationSetting, CommunicationTemplate, CommunicationTemplatePreview, TemplateStarter,
  TemplateStatus,
  ConnectionTestResult, CreateCommunicationTemplateRequest, SubmitTemplateToMetaRequest,
  UpdateCommunicationTemplateRequest,
  UpsertCommunicationSettingRequest
} from '@core/models/communication.model';

/** Talks to the four Communication Center controllers one-to-one. See
 *  MEMORY/modules/communication.md. */
@Injectable({ providedIn: 'root' })
export class CommunicationService {
  private readonly api = inject(ApiService);

  // ------------------------------------------------------------ templates

  listTemplates() {
    return this.api.get<CommunicationTemplate[]>(API.communicationTemplates);
  }

  getTemplate(id: string) {
    return this.api.get<CommunicationTemplate>(`${API.communicationTemplates}/${id}`);
  }

  createTemplate(body: CreateCommunicationTemplateRequest) {
    return this.api.post<CommunicationTemplate>(API.communicationTemplates, body);
  }

  updateTemplate(id: string, body: UpdateCommunicationTemplateRequest) {
    return this.api.put<CommunicationTemplate>(`${API.communicationTemplates}/${id}`, body);
  }

  /** What a new template for this event + channel starts as (the seeded default, or a generated message). */
  templateStarter(eventType: CommunicationEventType, channel: CommunicationChannel) {
    return this.api.get<TemplateStarter>(`${API.communicationTemplates}/starter`, { eventType, channel });
  }

  /** Puts a template back to the app's default text/name and drops its Meta approval; sending on/off is kept. */
  resetTemplateToDefault(id: string) {
    return this.api.post<CommunicationTemplate>(`${API.communicationTemplates}/${id}/reset-to-default`, {});
  }

  /** Turns one template's sending on (ACTIVE) or off (INACTIVE) right away — touches nothing else. */
  setTemplateStatus(id: string, status: TemplateStatus) {
    return this.api.patch<CommunicationTemplate>(`${API.communicationTemplates}/${id}/status`, { status });
  }

  /** Files a WhatsApp template with Meta (placeholders become {{1}}..{{n}}); Meta still reviews it. */
  submitTemplateToMeta(id: string, body: SubmitTemplateToMetaRequest = {}) {
    return this.api.post<CommunicationTemplate>(`${API.communicationTemplates}/${id}/submit-to-meta`, body);
  }

  refreshTemplateMetaStatus(id: string) {
    return this.api.post<CommunicationTemplate>(`${API.communicationTemplates}/${id}/refresh-meta-status`, {});
  }

  previewTemplate(id: string) {
    return this.api.get<CommunicationTemplatePreview>(`${API.communicationTemplates}/${id}/preview`);
  }

  // ------------------------------------------------------------- settings

  listSettings() {
    return this.api.get<CommunicationSetting[]>(API.communicationSettings);
  }

  getSetting(channel: CommunicationChannel) {
    return this.api.get<CommunicationSetting>(`${API.communicationSettings}/${channel}`);
  }

  upsertSetting(channel: CommunicationChannel, body: UpsertCommunicationSettingRequest) {
    return this.api.put<CommunicationSetting>(`${API.communicationSettings}/${channel}`, body);
  }

  testConnection(channel: CommunicationChannel) {
    return this.api.post<ConnectionTestResult>(`${API.communicationSettings}/${channel}/test-connection`, {});
  }

  // ---------------------------------------------------------------- logs

  searchLogs(criteria: CommunicationLogSearchRequest, query?: PageQuery): Observable<Page<CommunicationLog>> {
    return this.api.page<CommunicationLog>(API.communicationLogs, { ...query, ...toQuery(criteria) });
  }

  getLog(id: string) {
    return this.api.get<CommunicationLog>(`${API.communicationLogs}/${id}`);
  }

  logsForShipment(shipmentId: string) {
    return this.api.get<CommunicationLog[]>(`${API.communicationLogs}/shipment/${shipmentId}`);
  }

  retry(id: string) {
    return this.api.post<CommunicationLog>(`${API.communicationLogs}/${id}/retry`, {});
  }

  // ----------------------------------------------------------- dashboard

  /** Reads what Meta actually billed (last 90 days), works out each category's price per message and saves it. */
  fetchRates() {
    return this.api.post<FetchedRates>(`${API.communicationDashboard}/fetch-rates`, {});
  }

  dashboard(period: DashboardPeriod = 'TODAY') {
    return this.api.get<CommunicationDashboard>(API.communicationDashboard, { period });
  }
}

function toQuery(criteria: CommunicationLogSearchRequest): Record<string, string | undefined> {
  return {
    shipmentId: criteria.shipmentId,
    customerId: criteria.customerId,
    eventType: criteria.eventType,
    channel: criteria.channel,
    status: criteria.status
  };
}
