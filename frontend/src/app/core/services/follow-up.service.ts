import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  AddFollowUpNoteRequest, AssignFollowUpRequest, ChangeFollowUpStatusRequest, CreateFollowUpRequest,
  FollowUp, FollowUpDashboardStats, FollowUpHistoryEntry, RescheduleFollowUpRequest, UpdateFollowUpRequest
} from '@core/models/follow-up.model';

/** Follow-up Management API — talks to `/api/v1/follow-ups/**` via ApiService, mirroring
 *  the backend one-to-one. See MEMORY/modules/follow-up.md. */
@Injectable({ providedIn: 'root' })
export class FollowUpService {
  private readonly api = inject(ApiService);
  private readonly base = API.followUps;

  create(body: CreateFollowUpRequest): Observable<FollowUp> {
    return this.api.post<FollowUp>(this.base, body);
  }
  update(id: string, body: UpdateFollowUpRequest): Observable<FollowUp> {
    return this.api.put<FollowUp>(`${this.base}/${id}`, body);
  }
  get(id: string): Observable<FollowUp> {
    return this.api.get<FollowUp>(`${this.base}/${id}`);
  }
  search(query: PageQuery) {
    return this.api.page<FollowUp>(this.base, query);
  }
  dashboard(): Observable<FollowUpDashboardStats> {
    return this.api.get<FollowUpDashboardStats>(`${this.base}/dashboard`);
  }
  changeStatus(id: string, body: ChangeFollowUpStatusRequest): Observable<FollowUp> {
    return this.api.patch<FollowUp>(`${this.base}/${id}/status`, body);
  }
  reschedule(id: string, body: RescheduleFollowUpRequest): Observable<FollowUp> {
    return this.api.post<FollowUp>(`${this.base}/${id}/reschedule`, body);
  }
  assign(id: string, body: AssignFollowUpRequest): Observable<FollowUp> {
    return this.api.patch<FollowUp>(`${this.base}/${id}/assign`, body);
  }
  addNote(id: string, body: AddFollowUpNoteRequest): Observable<FollowUpHistoryEntry> {
    return this.api.post<FollowUpHistoryEntry>(`${this.base}/${id}/notes`, body);
  }
  history(id: string): Observable<FollowUpHistoryEntry[]> {
    return this.api.get<FollowUpHistoryEntry[]>(`${this.base}/${id}/history`);
  }
}
