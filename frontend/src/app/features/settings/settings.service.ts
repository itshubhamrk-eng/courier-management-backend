import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly api = inject(ApiService);
  get() { return this.api.get<Record<string, unknown>>(API.companySettings); }
  patchSection(section: string, body: unknown) {
    return this.api.patch<Record<string, unknown>>(`${API.companySettings}/${section}`, body);
  }
}
