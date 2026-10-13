package com.courier.modules.customerbilling.api;

import com.courier.modules.customerbilling.domain.ApprovalStatus;
import com.courier.modules.customerbilling.domain.EmailStatus;
import com.courier.modules.customerbilling.domain.InvoiceSource;
import com.courier.modules.customerbilling.domain.InvoiceStatus;
import com.courier.modules.customerbilling.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Wire contract of {@code /api/v1/customer-invoices}. */
public final class CustomerBillingDtos {

    private CustomerBillingDtos() {
    }

    public record GenerateRequest(@NotNull UUID customerId, LocalDate asOf) {
    }

    public record PaymentRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
            LocalDate paidOn,
            @NotNull PaymentMethod method,
            @Size(max = 100) String reference,
            @Size(max = 255) String remarks,
            @Size(max = 500) String proofUrl,
            @Size(max = 255) String proofName) {
    }

    /** Change status to PAID: the whole outstanding balance is received. */
    public record MarkPaidRequest(
            LocalDate paidOn,
            @NotNull PaymentMethod method,
            @Size(max = 100) String reference,
            @Size(max = 255) String remarks,
            @Size(max = 500) String proofUrl,
            @Size(max = 255) String proofName) {
    }

    public record ProofRequest(@NotNull @Size(max = 500) String proofUrl, @Size(max = 255) String proofName) {
    }

    public record UploadProofResponse(String url, String name) {
    }

    public record InvoiceSummaryResponse(
            UUID id, String invoiceNumber, UUID customerId, String customerName, LocalDate invoiceDate,
            LocalDate periodFrom, LocalDate periodTo, InvoiceStatus status, ApprovalStatus approvalStatus, EmailStatus emailStatus,
            InvoiceSource source, int shipmentsCount, int totalQuantity, BigDecimal totalWeight, BigDecimal totalAmount,
            BigDecimal prepaidAmount, BigDecimal amountPaid, BigDecimal balance) {
    }

    public record CustomerInfoResponse(UUID id, String code, String name, String mobile, String email,
                                       String gstNumber, String billingAddress) {
    }

    public record LineResponse(UUID id, UUID shipmentId, String shipmentNumber, LocalDate bookingDate, String consignee,
                               String destinationPincode, int quantity, BigDecimal actualWeight, String paymentMode,
                               boolean prepaid, BigDecimal gstAmount, BigDecimal amount) {
    }

    public record PaymentResponse(UUID id, BigDecimal amount, LocalDate paidOn, PaymentMethod method,
                                  String reference, String remarks, String proofName, String proofLink,
                                  Instant createdDate) {
    }

    public record InvoiceResponse(
            UUID id, String invoiceNumber, LocalDate invoiceDate, LocalDate periodFrom, LocalDate periodTo,
            InvoiceStatus status, ApprovalStatus approvalStatus, Instant approvedAt, EmailStatus emailStatus, String emailTo,
            Instant emailSentAt, String emailError, InvoiceSource source, String billingFrequency, int shipmentsCount, int totalQuantity,
            BigDecimal totalWeight, BigDecimal gstAmount, BigDecimal totalAmount, BigDecimal prepaidAmount,
            BigDecimal amountPaid, BigDecimal balance, CustomerInfoResponse customer,
            List<LineResponse> lines, List<PaymentResponse> payments, Instant createdDate, Long version) {
    }

    public record UnbilledResponse(UUID customerId, int shipments, int quantity, BigDecimal weight,
                                   BigDecimal amount, BigDecimal prepaid) {
    }

    public record CycleResult(int invoicesIssued) {
    }
}
