import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { ActivityLog, ActivityLogFilterOptions, ActivityLogSearchQuery, UserActivitySummary } from '@core/models/activity-log.model';
import { Page, PageQuery } from '@core/models/page.model';

/** Activity Log API — `/api/v1/activity-logs/**` and `/api/v1/users/{id}/activity`. */
@Injectable({ providedIn: 'root' })
export class ActivityLogService {
  private readonly api = inject(ApiService);

  search(query: ActivityLogSearchQuery): Observable<Page<ActivityLog>> {
    return this.api.page<ActivityLog>(API.activityLogs, query as PageQuery);
  }

  get(id: string): Observable<ActivityLog> {
    return this.api.get<ActivityLog>(`${API.activityLogs}/${id}`);
  }

  filterOptions(): Observable<ActivityLogFilterOptions> {
    return this.api.get<ActivityLogFilterOptions>(API.activityLogFilterOptions);
  }

  export(query: Omit<ActivityLogSearchQuery, 'page' | 'size' | 'sort'>): Observable<Blob> {
    return this.api.getBlob(API.activityLogExport, query as Record<string, unknown>);
  }

  userActivity(userId: string): Observable<UserActivitySummary> {
    return this.api.get<UserActivitySummary>(API.userActivity(userId));
  }
}
