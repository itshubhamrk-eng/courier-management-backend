import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Branch } from '@core/models/branch.model';
import { PageQuery } from '@core/models/page.model';

/**
 * Hubs — a Hub is a `Branch` row with `branchType: 'HUB'` (V61), not a separate entity.
 * This service is a thin, filtered view over `BranchService`'s own endpoint
 * (`GET /branches?branchType=HUB`) — see MEMORY/modules/hub-operations.md decision 1.
 * Create/edit/activate/deactivate reuse the Branch screens directly; there is no
 * separate Hub write endpoint.
 */
@Injectable({ providedIn: 'root' })
export class HubService {
  private readonly api = inject(ApiService);

  list(query: PageQuery) {
    return this.api.page<Branch>(API.branches, { ...query, branchType: 'HUB' });
  }
}
