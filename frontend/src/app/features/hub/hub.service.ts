// Hub module not built yet — backend has no /api/v1/hubs controller. Disabled, not routed.
/*
import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Page, PageQuery } from '@core/models/page.model';

/** Hub endpoints follow Branch's shape; the module lands next on the backend. * /
@Injectable({ providedIn: 'root' })
export class HubService {
  private readonly api = inject(ApiService);
  list(query: PageQuery) { return this.api.page<Record<string, unknown>>(API.hubs, query); }
}
*/
