import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Page, PageQuery } from '@core/models/page.model';
import {
  CommunicationChannel, CommunicationDashboard, CommunicationLog, CommunicationLogSearchRequest,
  CommunicationSetting, CommunicationTemplate, CommunicationTemplatePreview,
  ConnectionTestResult, CreateCommunicationTemplateRequest, UpdateCommunicationTemplateRequest,
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

  dashboard() {
    return this.api.get<CommunicationDashboard>(API.communicationDashboard);
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
