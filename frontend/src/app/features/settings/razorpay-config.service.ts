import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';

export type RazorpayMode = 'TEST' | 'LIVE';

/** Never carries either key secret itself — only whether each one has been saved. */
export interface RazorpayConfigResponse {
  enabled: boolean;
  mode: RazorpayMode;
  testKeyId: string | null;
  testKeySecretConfigured: boolean;
  liveKeyId: string | null;
  liveKeySecretConfigured: boolean;
}

/** Either `keySecret` field omitted or blank means "keep the one already stored" for that pair. */
export interface RazorpayConfigRequest {
  enabled: boolean;
  mode: RazorpayMode;
  testKeyId: string;
  testKeySecret?: string | null;
  liveKeyId: string;
  liveKeySecret?: string | null;
}

@Injectable({ providedIn: 'root' })
export class RazorpayConfigService {
  private readonly api = inject(ApiService);

  get() { return this.api.get<RazorpayConfigResponse>(API.companyRazorpayConfig); }
  update(body: RazorpayConfigRequest) {
    return this.api.put<RazorpayConfigResponse>(API.companyRazorpayConfig, body);
  }
}
