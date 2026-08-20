import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';

/** Never carries the key secret itself — only whether one has been saved. */
export interface RazorpayConfigResponse {
  enabled: boolean;
  keyId: string | null;
  keySecretConfigured: boolean;
}

/** `keySecret` omitted or blank means "keep the one already stored". */
export interface RazorpayConfigRequest {
  enabled: boolean;
  keyId: string;
  keySecret?: string | null;
}

@Injectable({ providedIn: 'root' })
export class RazorpayConfigService {
  private readonly api = inject(ApiService);

  get() { return this.api.get<RazorpayConfigResponse>(API.companyRazorpayConfig); }
  update(body: RazorpayConfigRequest) {
    return this.api.put<RazorpayConfigResponse>(API.companyRazorpayConfig, body);
  }
}
