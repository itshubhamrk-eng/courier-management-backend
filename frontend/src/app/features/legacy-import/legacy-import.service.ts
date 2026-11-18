import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  LegacyCommitResult, LegacyImportBatch, LegacyPreviewResult
} from '@core/models/legacy-import.model';

/** Legacy consignment import — mirrors LegacyImportController one-to-one; no mock data. */
@Injectable({ providedIn: 'root' })
export class LegacyImportService {
  private readonly api = inject(ApiService);

  preview(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.api.post<LegacyPreviewResult>(API.legacyImportPreview, body);
  }

  commit(file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.api.post<LegacyCommitResult>(API.legacyImportCommit, body);
  }

  mapOrigin(origin: string, branchId: string) {
    return this.api.put<void>(API.legacyImportOriginMap, { origin, branchId });
  }

  batches() { return this.api.get<LegacyImportBatch[]>(API.legacyImportBatches); }
}
