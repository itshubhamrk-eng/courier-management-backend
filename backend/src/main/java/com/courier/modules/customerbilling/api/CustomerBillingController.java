package com.courier.modules.customerbilling.api;

import com.courier.modules.customerbilling.api.CustomerBillingDtos.CustomerInfoResponse;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.CycleResult;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.GenerateRequest;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.InvoiceResponse;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.InvoiceSummaryResponse;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.LineResponse;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.MarkPaidRequest;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.ProofRequest;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.UploadProofResponse;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.PaymentRequest;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.PaymentResponse;
import com.courier.modules.customerbilling.api.CustomerBillingDtos.UnbilledResponse;
import com.courier.modules.customerbilling.application.CustomerBillingService;
import com.courier.modules.customerbilling.application.CustomerBillingService.InvoiceView;
import com.courier.modules.customerbilling.domain.ApprovalStatus;
import com.courier.modules.customerbilling.domain.InvoiceStatus;
import com.courier.shared.exception.BusinessRuleException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Business customer invoices. {@code COMPANY_ADMIN} only (enforced on the service). */
@RestController
@RequestMapping("/api/v1/customer-invoices")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer Billing", description = "Business customer invoices and receipts")
public class CustomerBillingController {

    private final CustomerBillingService service;

    @GetMapping
    @Operation(summary = "List invoices", description = "Optionally by customer, status, invoice-date range and invoice number.")
    public ApiResponse<List<InvoiceSummaryResponse>> list(
            @RequestParam(required = false) UUID customerId, @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) ApprovalStatus approval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(service.search(customerId, status, approval, from, to, search).stream().map(s -> {
            var i = s.invoice();
            return new InvoiceSummaryResponse(i.getId(), i.getInvoiceNumber(), i.getCustomerId(), s.customerName(),
                    i.getInvoiceDate(), i.getPeriodFrom(), i.getPeriodTo(), i.getStatus(), i.getApprovalStatus(), i.getEmailStatus(), i.getSource(),
                    i.getShipmentsCount(), i.getTotalQuantity(), i.getTotalWeight(), i.getTotalAmount(),
                    i.getPrepaidAmount(), i.getAmountPaid(), s.balance());
        }).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch an invoice with its lines and payments")
    public ApiResponse<InvoiceResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.get(id)));
    }

    @GetMapping("/unbilled")
    @Operation(summary = "What a customer has not been billed for yet",
            description = "Non-cancelled bookings on or before `asOf` (default today) not on a live invoice.")
    public ApiResponse<UnbilledResponse> unbilled(@RequestParam UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var u = service.unbilled(customerId, asOf);
        return ApiResponse.success(new UnbilledResponse(u.customerId(), u.shipments(), u.quantity(), u.weight(), u.amount(), u.prepaid()));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate an invoice now", description = "Bills every unbilled booking of the customer up to `asOf` "
            + "(default today). 422 when there is nothing to bill.")
    public ResponseEntity<ApiResponse<InvoiceResponse>> generate(@Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                toResponse(service.generate(request.customerId(), request.asOf())), "Invoice generated"));
    }

    @PostMapping("/run-cycle")
    @Operation(summary = "Run the billing cycle now", description = "Does for this company what the daily job does: invoices each "
            + "active business customer whose weekly / 15-day / monthly cycle has closed. Idempotent.")
    public ApiResponse<CycleResult> runCycle() {
        return ApiResponse.success(new CycleResult(service.runCycleNow()), "Billing cycle run");
    }

    @PostMapping("/{id}/payments")
    @Operation(summary = "Record a payment", description = "Full or part payment; cannot exceed the outstanding balance.")
    public ApiResponse<InvoiceResponse> pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest r) {
        return ApiResponse.success(toResponse(service.recordPayment(id, r.amount(), r.paidOn(), r.method(), r.reference(), r.remarks(), r.proofUrl(), r.proofName())),
                "Payment recorded");
    }

    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Change status to Paid", description = "Records one receipt for the whole outstanding balance, with an optional proof file.")
    public ApiResponse<InvoiceResponse> markPaid(@PathVariable UUID id, @Valid @RequestBody MarkPaidRequest r) {
        return ApiResponse.success(toResponse(service.markPaid(id, r.paidOn(), r.method(), r.reference(), r.remarks(), r.proofUrl(), r.proofName())),
                "Invoice marked as paid");
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    @Operation(summary = "Reverse a payment", description = "Removes one receipt and reopens the invoice (Unpaid / Partly paid).")
    public ApiResponse<InvoiceResponse> reversePayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        return ApiResponse.success(toResponse(service.reversePayment(id, paymentId)), "Payment reversed");
    }

    @PostMapping("/{id}/payments/{paymentId}/proof")
    @Operation(summary = "Attach proof to a payment", description = "`proofUrl` comes from upload-proof.")
    public ApiResponse<InvoiceResponse> attachProof(@PathVariable UUID id, @PathVariable UUID paymentId, @Valid @RequestBody ProofRequest r) {
        return ApiResponse.success(toResponse(service.attachProof(id, paymentId, r.proofUrl(), r.proofName())), "Proof attached");
    }

    @PostMapping(value = "/{id}/upload-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a proof file", description = "Image or PDF up to 10 MB (bank slip, UPI screenshot, cheque photo). Returns the "
            + "URL to pass as `proofUrl` when recording a payment, marking paid, or attaching proof.")
    public ApiResponse<UploadProofResponse> uploadProof(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read. Please retry.");
        }
        var up = service.uploadProof(id, content, file.getOriginalFilename(), file.getContentType());
        return ApiResponse.success(new UploadProofResponse(up.url(), up.name()), "File uploaded");
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an invoice", description = "Approving e-mails the invoice to the business customer's e-mail address. "
            + "A mail failure does not undo the approval; see emailStatus / emailError and use resend-email.")
    public ApiResponse<InvoiceResponse> approve(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.approve(id)), "Invoice approved");
    }

    @PostMapping("/{id}/resend-email")
    @Operation(summary = "E-mail the invoice again", description = "Approved invoices only.")
    public ApiResponse<InvoiceResponse> resendEmail(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.resendEmail(id)), "Invoice e-mail attempted");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an invoice", description = "Only when no payment is recorded. Its bookings become billable again.")
    public ApiResponse<InvoiceResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.cancel(id)), "Invoice cancelled");
    }

    private InvoiceResponse toResponse(InvoiceView v) {
        var i = v.invoice();
        var c = v.customer();
        return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getPeriodFrom(), i.getPeriodTo(),
                i.getStatus(), i.getApprovalStatus(), i.getApprovedAt(), i.getEmailStatus(), i.getEmailTo(), i.getEmailSentAt(), i.getEmailError(),
                i.getSource(), i.getBillingFrequency().name(), i.getShipmentsCount(), i.getTotalQuantity(),
                i.getTotalWeight(), i.getGstAmount(), i.getTotalAmount(), i.getPrepaidAmount(), i.getAmountPaid(), v.balance(),
                new CustomerInfoResponse(c.id(), c.code(), c.name(), c.mobile(), c.email(), c.gstNumber(), c.billingAddress()),
                v.lines().stream().map(l -> new LineResponse(l.getId(), l.getShipmentId(), l.getShipmentNumber(), l.getBookingDate(),
                        l.getConsignee(), l.getDestinationPincode(), l.getQuantity(), l.getActualWeight(), l.getPaymentMode(),
                        l.isPrepaid(), l.getGstAmount(), l.getAmount())).toList(),
                v.payments().stream().map(p -> new PaymentResponse(p.getId(), p.getAmount(), p.getPaidOn(), p.getMethod(),
                        p.getReference(), p.getRemarks(), p.getProofName(), service.proofLink(p.getProofUrl()), p.getCreatedAt())).toList(),
                i.getCreatedAt(), i.getVersion());
    }
}
