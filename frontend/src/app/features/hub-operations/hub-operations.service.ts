import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import { MovementOutcome } from '@core/models/shipment.model';
import {
  ExceptionSearchRequest, HubDashboardStats, HubException, OutScanRequest,
  RaiseExceptionRequest, ResolveExceptionRequest
} from './hub-operations.model';

/**
 * The genuinely new Hub Operations surface — out-scan and exceptions — talks to
 * /api/v1/hub-operations, mirroring HubOperationsController one-to-one; no mock data.
 * In-scan/Load Sheet/dispatch reuse ShipmentMovementService/ManifestService directly and
 * have no methods here.
 */
@Injectable({ providedIn: 'root' })
export class HubOperationsService {
  private readonly api = inject(ApiService);

  outScan(body: OutScanRequest) {
    return this.api.post<MovementOutcome[]>(`${API.hubOperations}/out-scan`, body);
  }

  raiseException(body: RaiseExceptionRequest) {
    return this.api.post<HubException>(`${API.hubOperations}/exceptions`, body);
  }

  resolveException(id: string, body: ResolveExceptionRequest) {
    return this.api.patch<HubException>(`${API.hubOperations}/exceptions/${id}/resolve`, body);
  }

  listExceptions(query: PageQuery & ExceptionSearchRequest) {
    return this.api.page<HubException>(`${API.hubOperations}/exceptions`, query);
  }

  dashboard(hubBranchId: string) {
    return this.api.get<HubDashboardStats>(`${API.hubOperations}/dashboard`, { hubBranchId });
  }
}
