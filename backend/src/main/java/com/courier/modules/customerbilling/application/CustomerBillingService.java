package com.courier.modules.customerbilling.application;

import com.courier.modules.customerbilling.domain.ApprovalStatus;
import com.courier.modules.customerbilling.domain.CustomerInvoice;
import com.courier.modules.customerbilling.domain.CustomerInvoiceLine;
import com.courier.modules.customerbilling.domain.CustomerInvoicePayment;
import com.courier.modules.customerbilling.domain.InvoiceStatus;
import com.courier.modules.customerbilling.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Business customer invoices and receipts. {@code COMPANY_ADMIN} only — the menu that hosts it is company-only too. */
public interface CustomerBillingService {

    List<InvoiceSummary> search(UUID customerId, InvoiceStatus status, ApprovalStatus approval, LocalDate from, LocalDate to, String search);

    InvoiceView get(UUID invoiceId);

    /** What would be billed now for a customer: count, quantity, weight and amount of unbilled bookings up to {@code asOf}. */
    UnbilledSummary unbilled(UUID customerId, LocalDate asOf);

    /** Bills everything unbilled up to {@code asOf} (default today). Refused when there is nothing to bill. */
    InvoiceView generate(UUID customerId, LocalDate asOf);

    /** Runs the billing cycle for the caller's company now — what the daily job does for every company. */
    int runCycleNow();

    /** Receipt for any amount up to the balance; the optional proof is a URL from {@link #uploadProof}. Approved invoices only. */
    InvoiceView recordPayment(UUID invoiceId, BigDecimal amount, LocalDate paidOn, PaymentMethod method,
                              String reference, String remarks, String proofUrl, String proofName);

    /** Change status to PAID: records one receipt for the whole outstanding balance. */
    InvoiceView markPaid(UUID invoiceId, LocalDate paidOn, PaymentMethod method, String reference, String remarks,
                         String proofUrl, String proofName);

    /** Reverses one receipt (soft-deletes it), reopening the invoice towards UNPAID / PARTLY_PAID. */
    InvoiceView reversePayment(UUID invoiceId, UUID paymentId);

    /** Attaches (or replaces) the proof file of an existing receipt. */
    InvoiceView attachProof(UUID invoiceId, UUID paymentId, String proofUrl, String proofName);

    /** Stores a proof file and returns its URL, to pass into a payment / mark-paid / attach call. */
    UploadedProof uploadProof(UUID invoiceId, byte[] content, String originalName, String contentType);

    /** Approves a pending invoice and e-mails it to the customer. */
    InvoiceView approve(UUID invoiceId);

    /** Sends the approved invoice to the customer's e-mail again (after a failure, or an address fix). */
    InvoiceView resendEmail(UUID invoiceId);

    /** A short-lived signed link for a stored proof file (the plain stored URL when nothing can be signed). */
    String proofLink(String storedUrl);

    record UploadedProof(String url, String name) {
    }

    InvoiceView cancel(UUID invoiceId);

    record InvoiceSummary(CustomerInvoice invoice, String customerName, BigDecimal balance) {
    }

    record InvoiceView(CustomerInvoice invoice, BigDecimal balance, CustomerInfo customer,
                       List<CustomerInvoiceLine> lines, List<CustomerInvoicePayment> payments) {
    }

    record CustomerInfo(UUID id, String code, String name, String mobile, String email, String gstNumber, String billingAddress) {
    }

    record UnbilledSummary(UUID customerId, int shipments, int quantity, BigDecimal weight, BigDecimal amount, BigDecimal prepaid) {
    }
}
