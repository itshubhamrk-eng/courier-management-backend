import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Page, PageQuery } from '@core/models/page.model';
import { IncidentAction, IncidentFilter, IncidentSummary, SecurityIncident } from './security-incident.model';

/** Security incident API — `/api/v1/security/incidents/**`. */
@Injectable({ providedIn: 'root' })
export class SecurityIncidentService {
  private readonly api = inject(ApiService);

  list(filter: IncidentFilter): Observable<Page<SecurityIncident>> {
    return this.api.page<SecurityIncident>(API.securityIncidents, filter as PageQuery);
  }

  get(id: string): Observable<SecurityIncident> {
    return this.api.get<SecurityIncident>(`${API.securityIncidents}/${id}`);
  }

  summary(): Observable<IncidentSummary> {
    return this.api.get<IncidentSummary>(API.securityIncidentSummary);
  }

  ban(id: string, note?: string | null): Observable<SecurityIncident> { return this.act(id, 'ban', note); }
  dismiss(id: string, note?: string | null): Observable<SecurityIncident> { return this.act(id, 'dismiss', note); }
  unban(id: string, note?: string | null): Observable<SecurityIncident> { return this.act(id, 'unban', note); }

  private act(id: string, action: IncidentAction, note?: string | null): Observable<SecurityIncident> {
    return this.api.post<SecurityIncident>(API.securityIncidentAction(id, action), { note: note ?? null });
  }
}
