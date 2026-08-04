import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  SubscriptionPlan, SubscriptionPlanProfile, CreateSubscriptionPlanRequest, UpdateSubscriptionPlanRequest
} from '@core/models/subscription-plan.model';
import { PageQuery } from '@core/models/page.model';

/**
 * Platform subscription-plan catalogue — talks to /api/v1/subscription-plans via
 * ApiService. SUPER_ADMIN only end to end (backend class-level @PreAuthorize); mirrors
 * the endpoints one-to-one, no mock data. The optimistic-lock `version` travels in the
 * PUT body, matching SubscriptionPlanController.
 */
@Injectable({ providedIn: 'root' })
export class SubscriptionPlanService {
  private readonly api = inject(ApiService);

  // ---- reads ----------------------------------------------------------------
  list(query: PageQuery) { return this.api.page<SubscriptionPlan>(API.subscriptionPlans, query); }
  get(id: string) { return this.api.get<SubscriptionPlanProfile>(`${API.subscriptionPlans}/${id}`); }

  // ---- writes ---------------------------------------------------------------
  create(body: CreateSubscriptionPlanRequest) { return this.api.post<SubscriptionPlanProfile>(API.subscriptionPlans, body); }
  update(id: string, body: UpdateSubscriptionPlanRequest) { return this.api.put<SubscriptionPlanProfile>(`${API.subscriptionPlans}/${id}`, body); }
  remove(id: string) { return this.api.delete<void>(`${API.subscriptionPlans}/${id}`); }

  // ---- lifecycle (idempotent) ------------------------------------------------
  activate(id: string) { return this.api.patch<SubscriptionPlanProfile>(`${API.subscriptionPlans}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<SubscriptionPlanProfile>(`${API.subscriptionPlans}/${id}/deactivate`, {}); }
}
