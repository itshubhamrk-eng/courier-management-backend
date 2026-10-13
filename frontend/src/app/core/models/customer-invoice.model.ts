/**
 * Business Customer Billing — mirrors backend `com.courier.modules.customerbilling`
 * (`CustomerBillingDtos`). An invoice bills one business customer for every non-cancelled booking
 * not already on a live invoice; balance = total - prepaid (paid at the counter) - receipts.
 */

export type InvoiceStatus = 'UNPAID' | 'PARTLY_PAID' | 'PAID' | 'CANCELLED';
export type InvoiceSource = 'AUTO' | 'MANUAL';
export type ApprovalStatus = 'PENDING_APPROVAL' | 'APPROVED';
export type EmailStatus = 'NOT_SENT' | 'SENT' | 'FAILED' | 'NO_EMAIL';
export const APPROVAL_LABELS: Record<ApprovalStatus, string> = { PENDING_APPROVAL: 'Pending approval', APPROVED: 'Approved' };
export const EMAIL_LABELS: Record<EmailStatus, string> = {
  NOT_SENT: 'Not sent', SENT: 'E-mailed', FAILED: 'E-mail failed', NO_EMAIL: 'No e-mail on file'
};
export type PaymentMethod = 'CASH' | 'UPI' | 'BANK_TRANSFER' | 'CHEQUE' | 'OTHER';

export const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE', 'OTHER'];
export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash', UPI: 'UPI', BANK_TRANSFER: 'Bank transfer', CHEQUE: 'Cheque', OTHER: 'Other'
};
export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  UNPAID: 'Unpaid', PARTLY_PAID: 'Partly paid', PAID: 'Paid', CANCELLED: 'Cancelled'
};

export interface InvoiceSummary {
  id: string; invoiceNumber: string; customerId: string; customerName?: string | null;
  invoiceDate: string; periodFrom: string; periodTo: string; status: InvoiceStatus; approvalStatus: ApprovalStatus;
  emailStatus: EmailStatus; source: InvoiceSource;
  shipmentsCount: number; totalQuantity: number; totalWeight: number; totalAmount: number;
  prepaidAmount: number; amountPaid: number; balance: number;
}

export interface InvoiceLine {
  id: string; shipmentId: string; shipmentNumber: string; bookingDate?: string | null; consignee?: string | null;
  destinationPincode?: string | null; quantity: number; actualWeight: number; paymentMode?: string | null;
  prepaid: boolean; gstAmount: number; amount: number;
}

export interface InvoicePayment {
  id: string; amount: number; paidOn: string; method: PaymentMethod; reference?: string | null;
  remarks?: string | null; proofName?: string | null; proofLink?: string | null; createdDate?: string | null;
}

export interface InvoiceCustomer {
  id: string; code?: string | null; name?: string | null; mobile?: string | null; email?: string | null;
  gstNumber?: string | null; billingAddress?: string | null;
}

export interface Invoice {
  id: string; invoiceNumber: string; invoiceDate: string; periodFrom: string; periodTo: string;
  status: InvoiceStatus; approvalStatus: ApprovalStatus; approvedAt?: string | null; emailStatus: EmailStatus;
  emailTo?: string | null; emailSentAt?: string | null; emailError?: string | null;
  source: InvoiceSource; billingFrequency: string; shipmentsCount: number;
  totalQuantity: number; totalWeight: number; gstAmount: number; totalAmount: number; prepaidAmount: number;
  amountPaid: number; balance: number; customer: InvoiceCustomer; lines: InvoiceLine[];
  payments: InvoicePayment[]; createdDate?: string | null; version: number;
}

export interface UnbilledSummary {
  customerId: string; shipments: number; quantity: number; weight: number; amount: number; prepaid: number;
}

export interface RecordPaymentRequest {
  amount: number; paidOn?: string | null; method: PaymentMethod; reference?: string | null; remarks?: string | null;
  proofUrl?: string | null; proofName?: string | null;
}

export interface MarkPaidRequest {
  paidOn?: string | null; method: PaymentMethod; reference?: string | null; remarks?: string | null;
  proofUrl?: string | null; proofName?: string | null;
}

export interface UploadedProof { url: string; name: string; }
