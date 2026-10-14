import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import { PdpEarning, PdpBillingFrequency, PdpBillingSchedule, PdpEarningStatus, PdpRateType, PdpStatement, PdpStatementDetail, PdpStatementStatus } from '@core/models/pdp.model';

/** Partner earnings and billing statements — mirrors backend `/pdp-earnings` and `/pdp-statements`. */
@Injectable({ providedIn: 'root' })
export class PdpBillingService {
  private readonly api = inject(ApiService);

  earnings(query: PageQuery & { branchId?: string; type?: PdpRateType; status?: PdpEarningStatus; from?: string; to?: string }) {
    return this.api.page<PdpEarning>(API.pdpEarnings, query);
  }
  reprice(id: string) { return this.api.post<PdpEarning>(`${API.pdpEarnings}/${id}/reprice`, {}); }

  statements(query: PageQuery & { branchId?: string; status?: PdpStatementStatus }) {
    return this.api.page<PdpStatement>(API.pdpStatements, query);
  }
  statement(id: string) { return this.api.get<PdpStatementDetail>(`${API.pdpStatements}/${id}`); }
  generate(body: { branchId: string; from: string; to: string; remarks?: string | null }) {
    return this.api.post<PdpStatementDetail>(API.pdpStatements, body);
  }
  approve(id: string) { return this.api.patch<PdpStatementDetail>(`${API.pdpStatements}/${id}/approve`, {}); }
  pay(id: string, paidOn: string, reference?: string | null) {
    return this.api.patch<PdpStatementDetail>(`${API.pdpStatements}/${id}/pay`, { paidOn, reference: reference || null });
  }
  schedule() { return this.api.get<PdpBillingSchedule[]>(`${API.pdpStatements}/schedule`); }
  setFrequency(branchId: string, frequency: PdpBillingFrequency) {
    return this.api.put<{ frequency: PdpBillingFrequency }>(`/pdp-billing-frequency/${branchId}`, { frequency });
  }
  generateDue() { return this.api.post<{ created: number }>(`${API.pdpStatements}/generate-due`, {}); }
  remove(id: string) { return this.api.delete<void>(`${API.pdpStatements}/${id}`); }
}
