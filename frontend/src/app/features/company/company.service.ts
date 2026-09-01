import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  AssignSubscriptionRequest, Company, CompanyProfile, CompanyRequest, CompanyStatistics,
  CreateCompanyRequest, CreatedCompanyResponse, PlatformDashboard, RenewSubscriptionRequest,
  SubscriptionPlanOption
} from '@core/models/company.model';
import { PageQuery } from '@core/models/page.model';

/** A geography row, reduced to what the cascading Country/State/City picker needs. */
export interface GeographyOption { id: string; name: string; }

interface GeographyRow { id: string; code: string; name: string; }

/**
 * Company reads, edits and lifecycle, plus the platform console. `SUPER_ADMIN` only —
 * every endpoint behind this service is guarded that way on the backend.
 */
@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly api = inject(ApiService);

  list(query: PageQuery) { return this.api.page<Company>(API.companies, query); }

  /**
   * Create a company — POST /companies.
   *
   * The response carries a `provisioning` block with the first administrator's temporary
   * password. That is the only time it exists in readable form, so the caller must show
   * it immediately.
   */
  create(body: CreateCompanyRequest) {
    return this.api.post<CreatedCompanyResponse>(API.companies, body);
  }

  /** Full profile — GET /companies/{id}. */
  getProfile(id: string) { return this.api.get<CompanyProfile>(`${API.companies}/${id}`); }

  /**
   * Uploads a logo or favicon and returns its URL. Not company-scoped — the create form
   * has no company id yet — so this only stores the file; the caller still writes the
   * returned URL into the company record via `create` or `update`.
   */
  uploadBranding(kind: 'LOGO' | 'FAVICON', file: File) {
    const body = new FormData();
    body.append('kind', kind);
    body.append('file', file);
    return this.api.post<{ url: string }>(`${API.companies}/branding-upload`, body);
  }

  /** Full replacement — PUT /companies/{id}. Body must carry the last-read version. */
  update(id: string, body: CompanyRequest) {
    return this.api.put<CompanyProfile>(`${API.companies}/${id}`, body);
  }

  activate(id: string) { return this.api.patch<CompanyProfile>(`${API.companies}/${id}/activate`, {}); }

  /**
   * Switch a dormant company off. Distinct from `suspend`, which is punitive and carries
   * a reason support will quote back — the two show up differently in a conversation
   * with the customer, so they are two calls, not one with a flag.
   */
  deactivate(id: string, reason?: string) {
    return this.api.patch<CompanyProfile>(`${API.companies}/${id}/deactivate`, { reason: reason ?? null });
  }

  suspend(id: string, reason: string) {
    return this.api.patch<CompanyProfile>(`${API.companies}/${id}/suspend`, { reason });
  }

  // ------------------------------------------------------------- subscription

  /** Move a company onto a plan and open a paid window. Activates it. */
  assignSubscription(id: string, body: AssignSubscriptionRequest) {
    return this.api.post<CompanyProfile>(`${API.companies}/${id}/subscription`, body);
  }

  /**
   * Extend the paid window. There is no start date: the server extends from the later of
   * the current end and today, so paying early keeps the days already bought and paying
   * late is not billed for the gap.
   */
  renewSubscription(id: string, body: RenewSubscriptionRequest) {
    return this.api.post<CompanyProfile>(`${API.companies}/${id}/subscription/renew`, body);
  }

  suspendSubscription(id: string, reason: string) {
    return this.api.post<CompanyProfile>(`${API.companies}/${id}/subscription/suspend`, { reason });
  }

  // --------------------------------------------------------------- dashboards

  /** Counts and subscription position for one company. Carries no shipment figure. */
  statistics(id: string) {
    return this.api.get<CompanyStatistics>(`${API.companies}/${id}/statistics`);
  }

  /** Platform-wide totals and the renewals worklist. */
  platformDashboard() {
    return this.api.get<PlatformDashboard>(`${API.superAdmin}/dashboard`);
  }

  /** Active plans for the edit form's plan dropdown. */
  plans() {
    return this.api
      .page<SubscriptionPlanOption>(API.subscriptionPlans, { page: 0, size: 100, sort: 'planName,asc' })
      .pipe(map((p) => p.content.filter((plan) => plan.isActive)));
  }

  // ---- geography pickers (global masters, cascading) --------------------------
  //
  // Company.country/state/city are free-text (no FK, no district column), unlike the
  // id-based address book Customer uses — see CustomerService's own copy of this seam.
  // District exists only to narrow the city list per the backend's own /cities filter
  // (districtId, not stateId); its id is never sent to the company create endpoint.
  countries() { return this.geography('countries'); }
  states(countryId: string) { return this.geography('states', { countryId }); }
  districts(stateId: string) { return this.geography('districts', { stateId }); }
  cities(districtId: string) { return this.geography('cities', { districtId }); }

  private geography(path: string, filter?: Record<string, string>): Observable<GeographyOption[]> {
    return this.api
      .page<GeographyRow>(`${API.globalMasters}/${path}`, { page: 0, size: 200, status: 'ACTIVE', ...filter })
      .pipe(map((p) => p.content.map((r) => ({ id: r.id, name: r.name }))));
  }
}
