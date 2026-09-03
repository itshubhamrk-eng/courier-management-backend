import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  DistrictLevelFreight, CreateDistrictLevelFreightRequest, UpdateDistrictLevelFreightRequest,
  ImportSummaryResponse
} from '@core/models/district-level-freight.model';

/**
 * District Level Freight administration — talks to /api/v1/district-level-freight via
 * ApiService, mirroring DistrictLevelFreightController one-to-one; no mock data. The
 * optimistic-lock `version` travels in the PUT body. Status (activate/deactivate) has its
 * own endpoint.
 */
@Injectable({ providedIn: 'root' })
export class DistrictLevelFreightService {
  private readonly api = inject(ApiService);

  // ---- reads ----------------------------------------------------------------
  list(query: PageQuery) { return this.api.page<DistrictLevelFreight>(API.districtLevelFreight, query); }
  get(id: string) { return this.api.get<DistrictLevelFreight>(`${API.districtLevelFreight}/${id}`); }

  // ---- writes -----------------------------------------------------------------
  create(body: CreateDistrictLevelFreightRequest) {
    return this.api.post<DistrictLevelFreight>(API.districtLevelFreight, body);
  }
  update(id: string, body: UpdateDistrictLevelFreightRequest) {
    return this.api.put<DistrictLevelFreight>(`${API.districtLevelFreight}/${id}`, body);
  }
  delete(id: string) { return this.api.delete<void>(`${API.districtLevelFreight}/${id}`); }

  // ---- lifecycle (idempotent) -------------------------------------------------
  activate(id: string) { return this.api.patch<DistrictLevelFreight>(`${API.districtLevelFreight}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<DistrictLevelFreight>(`${API.districtLevelFreight}/${id}/deactivate`, {}); }

  // ---- Excel import -------------------------------------------------------------
  previewImport(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.api.post<ImportSummaryResponse>(API.districtLevelFreightImportPreview, body);
  }

  commitImport(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.api.post<ImportSummaryResponse>(API.districtLevelFreightImport, body);
  }
}
