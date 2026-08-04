import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import { Manifest, CreateManifestRequest, Shipment } from '@core/models/shipment.model';

/**
 * The minimal Manifest module Shipment Movement needs underneath it — see
 * MEMORY/modules/shipment-movement.md. Talks to /api/v1/manifests, mirroring
 * ManifestController one-to-one; no mock data.
 */
@Injectable({ providedIn: 'root' })
export class ManifestService {
  private readonly api = inject(ApiService);

  create(body: CreateManifestRequest) { return this.api.post<Manifest>(API.manifests, body); }
  get(id: string) { return this.api.get<Manifest>(`${API.manifests}/${id}`); }
  list(query: PageQuery) { return this.api.page<Manifest>(API.manifests, query); }
  shipments(id: string) { return this.api.get<Shipment[]>(`${API.manifests}/${id}/shipments`); }
}
