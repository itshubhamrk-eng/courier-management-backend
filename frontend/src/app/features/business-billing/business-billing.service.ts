import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  ApprovalStatus, Invoice, InvoiceStatus, InvoiceSummary, MarkPaidRequest, RecordPaymentRequest, UnbilledSummary, UploadedProof
} from '@core/models/customer-invoice.model';

/** Business customer invoices and receipts — mirrors the backend endpoints one-to-one. */
@Injectable({ providedIn: 'root' })
export class BusinessBillingService {
  private readonly api = inject(ApiService);

  invoices(filter: { customerId?: string; status?: InvoiceStatus; approval?: ApprovalStatus; from?: string; to?: string; search?: string } = {}) {
    return this.api.get<InvoiceSummary[]>(API.customerInvoices, filter);
  }
  invoice(id: string) { return this.api.get<Invoice>(`${API.customerInvoices}/${id}`); }
  unbilled(customerId: string, asOf?: string) { return this.api.get<UnbilledSummary>(`${API.customerInvoices}/unbilled`, { customerId, asOf }); }
  generate(customerId: string, asOf?: string | null) { return this.api.post<Invoice>(`${API.customerInvoices}/generate`, { customerId, asOf: asOf || null }); }
  runCycle() { return this.api.post<{ invoicesIssued: number }>(`${API.customerInvoices}/run-cycle`, {}); }
  pay(id: string, body: RecordPaymentRequest) { return this.api.post<Invoice>(`${API.customerInvoices}/${id}/payments`, body); }
  markPaid(id: string, body: MarkPaidRequest) { return this.api.post<Invoice>(`${API.customerInvoices}/${id}/mark-paid`, body); }
  reversePayment(id: string, paymentId: string) { return this.api.delete<Invoice>(`${API.customerInvoices}/${id}/payments/${paymentId}`); }
  attachProof(id: string, paymentId: string, proofUrl: string, proofName: string) {
    return this.api.post<Invoice>(`${API.customerInvoices}/${id}/payments/${paymentId}/proof`, { proofUrl, proofName });
  }
  uploadProof(id: string, file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.api.post<UploadedProof>(`${API.customerInvoices}/${id}/upload-proof`, form);
  }
  approve(id: string) { return this.api.post<Invoice>(`${API.customerInvoices}/${id}/approve`, {}); }
  resendEmail(id: string) { return this.api.post<Invoice>(`${API.customerInvoices}/${id}/resend-email`, {}); }
  cancel(id: string) { return this.api.post<Invoice>(`${API.customerInvoices}/${id}/cancel`, {}); }
}
