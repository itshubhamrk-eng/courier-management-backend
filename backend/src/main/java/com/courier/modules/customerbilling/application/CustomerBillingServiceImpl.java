package com.courier.modules.customerbilling.application;

import com.courier.modules.customerbilling.domain.ApprovalStatus;
import com.courier.modules.customerbilling.domain.CustomerInvoice;
import com.courier.modules.customerbilling.domain.EmailStatus;
import com.courier.modules.customerbilling.domain.CustomerInvoiceLine;
import com.courier.modules.customerbilling.domain.CustomerInvoiceLineRepository;
import com.courier.modules.customerbilling.domain.CustomerInvoicePayment;
import com.courier.modules.customerbilling.domain.CustomerInvoicePaymentRepository;
import com.courier.modules.customerbilling.domain.CustomerInvoiceRepository;
import com.courier.modules.customerbilling.domain.InvoiceSource;
import com.courier.modules.customerbilling.domain.InvoiceStatus;
import com.courier.modules.customerbilling.domain.PaymentMethod;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-facing invoice operations over {@link CustomerInvoiceGenerator}. Customer display data is
 * read with plain SQL (the customer module's service is gated for interactive callers and this
 * module only needs a few columns), always scoped to the bound company.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBillingServiceImpl implements CustomerBillingService {

    private static final String ENTITY = "CustomerInvoice";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final int MAX_PROOF_BYTES = 10 * 1024 * 1024;

    private final CustomerInvoiceRepository invoices;
    private final CustomerInvoiceLineRepository lines;
    private final CustomerInvoicePaymentRepository payments;
    private final CustomerInvoiceGenerator generator;
    private final BillingCycleService cycleService;
    private final InvoiceEmailSender emailSender;
    private final FileStoragePort storage;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public List<InvoiceSummary> search(UUID customerId, InvoiceStatus status, ApprovalStatus approval, LocalDate from, LocalDate to, String search) {
        UUID company = requireCompany();
        String pattern = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        List<CustomerInvoice> found = invoices.search(company, customerId, status, approval, from, to, pattern);
        Map<UUID, String> names = customerNames(company);
        return found.stream().map(i -> new InvoiceSummary(i, names.get(i.getCustomerId()), i.balance())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public InvoiceView get(UUID invoiceId) {
        UUID company = requireCompany();
        return view(company, load(invoiceId, company));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(ADMIN)
    public UnbilledSummary unbilled(UUID customerId, LocalDate asOf) {
        requireCompany();
        List<CustomerInvoiceGenerator.Unbilled> items = generator.unbilled(customerId, asOf == null ? today() : asOf);
        int qty = 0;
        BigDecimal weight = BigDecimal.ZERO, amount = BigDecimal.ZERO, prepaid = BigDecimal.ZERO;
        for (CustomerInvoiceGenerator.Unbilled u : items) {
            qty += u.quantity(); weight = weight.add(u.weight()); amount = amount.add(u.amount());
            if (u.prepaid()) prepaid = prepaid.add(u.amount());
        }
        return new UnbilledSummary(customerId, items.size(), qty, weight, amount, prepaid);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView generate(UUID customerId, LocalDate asOf) {
        UUID company = requireCompany();
        CustomerInvoice invoice = generator.generate(customerId, asOf == null ? today() : asOf, InvoiceSource.MANUAL)
                .orElseThrow(() -> new BusinessRuleException("This customer has no unbilled bookings up to that date."));
        return view(company, invoice);
    }

    @Override
    @PreAuthorize(ADMIN)
    public int runCycleNow() {
        requireCompany();
        return cycleService.runForBoundCompany(today());
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView recordPayment(UUID invoiceId, BigDecimal amount, LocalDate paidOn, PaymentMethod method,
                                     String reference, String remarks, String proofUrl, String proofName) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        invoice.requireApproved();
        invoice.applyPayment(amount);
        invoices.save(invoice);
        payments.save(CustomerInvoicePayment.builder().invoiceId(invoice.getId()).amount(amount)
                .paidOn(paidOn == null ? today() : paidOn).method(method == null ? PaymentMethod.OTHER : method)
                .reference(blankToNull(reference)).remarks(blankToNull(remarks))
                .proofUrl(blankToNull(proofUrl)).proofName(blankToNull(proofName)).build());
        log.info("Payment {} recorded on invoice {} in company {} by {}", amount, invoice.getInvoiceNumber(), company, actor());
        auditService.record(AuditAction.CUSTOMER_INVOICE_PAYMENT_RECORDED, ENTITY, invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "amount", amount.toPlainString()));
        return view(company, invoice);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView markPaid(UUID invoiceId, LocalDate paidOn, PaymentMethod method, String reference, String remarks,
                                String proofUrl, String proofName) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        if (invoice.balance().signum() == 0) {
            throw new BusinessRuleException("This invoice has nothing outstanding.");
        }
        return recordPayment(invoiceId, invoice.balance(), paidOn, method, reference, remarks, proofUrl, proofName);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView reversePayment(UUID invoiceId, UUID paymentId) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        CustomerInvoicePayment payment = payments.findOne(paymentId, invoice.getId(), company)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerInvoicePayment", paymentId));
        invoice.reversePayment(payment.getAmount());
        invoices.save(invoice);
        payment.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        payments.save(payment);
        log.warn("Payment {} on invoice {} reversed in company {} by {}", payment.getAmount(), invoice.getInvoiceNumber(), company, actor());
        auditService.record(AuditAction.CUSTOMER_INVOICE_PAYMENT_REVERSED, ENTITY, invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "amount", payment.getAmount().toPlainString()));
        return view(company, invoice);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView attachProof(UUID invoiceId, UUID paymentId, String proofUrl, String proofName) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        CustomerInvoicePayment payment = payments.findOne(paymentId, invoice.getId(), company)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerInvoicePayment", paymentId));
        if (blankToNull(proofUrl) == null) {
            throw new BusinessRuleException("Upload the proof file first.");
        }
        payment.setProofUrl(proofUrl.trim());
        payment.setProofName(blankToNull(proofName));
        payments.save(payment);
        auditService.record(AuditAction.CUSTOMER_INVOICE_PROOF_ATTACHED, ENTITY, invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber(), "paymentId", paymentId.toString()));
        return view(company, invoice);
    }

    @Override
    @PreAuthorize(ADMIN)
    public UploadedProof uploadProof(UUID invoiceId, byte[] content, String originalName, String contentType) {
        UUID company = requireCompany();
        load(invoiceId, company);
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("The uploaded file is empty.");
        }
        if (content.length > MAX_PROOF_BYTES) {
            throw new BusinessRuleException("The proof file is too large (max 10 MB).");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!(type.startsWith("image/") || type.equals("application/pdf"))) {
            throw new BusinessRuleException("The proof must be an image or a PDF.");
        }
        String safe = (originalName == null ? "proof" : originalName).replaceAll("[^A-Za-z0-9._-]", "_");
        String key = UUID.randomUUID() + "-" + safe;
        FileStoragePort.StoredFile stored = storage.upload(new FileStoragePort.UploadRequest(content, key, contentType,
                "customer-invoice-proof/" + company + "/" + invoiceId));
        return new UploadedProof(stored.url(), originalName == null ? safe : originalName);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView approve(UUID invoiceId) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        if (invoice.isApproved()) {
            return view(company, invoice);
        }
        invoice.approve(SecurityUtils.getCurrentUserId().orElse(null));
        invoices.save(invoice);
        log.info("Invoice {} approved in company {} by {}", invoice.getInvoiceNumber(), company, actor());
        auditService.record(AuditAction.CUSTOMER_INVOICE_APPROVED, ENTITY, invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber()));
        sendEmail(company, invoice);
        return view(company, invoice);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView resendEmail(UUID invoiceId) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        if (!invoice.isApproved()) {
            throw new BusinessRuleException("Only an approved invoice is e-mailed to the customer.");
        }
        sendEmail(company, invoice);
        return view(company, invoice);
    }

    @Override
    public String proofLink(String storedUrl) {
        return storedUrl == null ? null : storage.presignGet(storedUrl, java.time.Duration.ofHours(1));
    }

    /** Best effort: a mail failure never undoes the approval; it is recorded on the invoice for a retry. */
    private void sendEmail(UUID company, CustomerInvoice invoice) {
        CustomerInfo customer = customerInfo(company, invoice.getCustomerId());
        if (customer.email() == null || customer.email().isBlank()) {
            invoice.setEmailStatus(EmailStatus.NO_EMAIL);
            invoice.setEmailTo(null);
            invoice.setEmailError("The customer has no e-mail address on file.");
        } else {
            InvoiceEmailSender.Result result = emailSender.send(company, invoice,
                    lines.findByInvoice(company, invoice.getId()), customer.name(), customer.email().trim());
            invoice.setEmailTo(customer.email().trim());
            invoice.setEmailStatus(result.sent() ? EmailStatus.SENT : EmailStatus.FAILED);
            invoice.setEmailSentAt(result.sent() ? java.time.Instant.now() : null);
            invoice.setEmailError(result.error());
            if (result.sent()) {
                auditService.record(AuditAction.CUSTOMER_INVOICE_EMAILED, ENTITY, invoice.getId(),
                        Map.of("invoiceNumber", invoice.getInvoiceNumber(), "to", customer.email().trim()));
            }
        }
        invoices.save(invoice);
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public InvoiceView cancel(UUID invoiceId) {
        UUID company = requireCompany();
        CustomerInvoice invoice = load(invoiceId, company);
        if (invoice.isCancelled()) {
            return view(company, invoice);
        }
        invoice.cancel();
        invoices.save(invoice);
        UUID actor = SecurityUtils.getCurrentUserId().orElse(null);
        // Free the shipments: a cancelled invoice's lines no longer count as "billed".
        for (CustomerInvoiceLine line : lines.findByInvoice(company, invoice.getId())) {
            line.softDelete(actor);
            lines.save(line);
        }
        log.warn("Invoice {} cancelled in company {} by {}", invoice.getInvoiceNumber(), company, actor());
        auditService.record(AuditAction.CUSTOMER_INVOICE_CANCELLED, ENTITY, invoice.getId(),
                Map.of("invoiceNumber", invoice.getInvoiceNumber()));
        return new InvoiceView(invoice, invoice.balance(), customerInfo(company, invoice.getCustomerId()), List.of(),
                payments.findByInvoice(company, invoice.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private InvoiceView view(UUID company, CustomerInvoice invoice) {
        return new InvoiceView(invoice, invoice.balance(), customerInfo(company, invoice.getCustomerId()),
                lines.findByInvoice(company, invoice.getId()), payments.findByInvoice(company, invoice.getId()));
    }

    private CustomerInvoice load(UUID id, UUID company) {
        return invoices.findByIdWithinCompany(id, company).orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> customerNames(UUID company) {
        List<Object[]> rows = em.createNativeQuery("SELECT id, COALESCE(NULLIF(TRIM(company_name), ''), TRIM(CONCAT_WS(' ', first_name, last_name))) "
                + "FROM customers WHERE company_id = :c AND customer_type = 'BUSINESS'")
                .setParameter("c", TimeOrderedUuid.toBytes(company)).getResultList();
        return rows.stream().collect(Collectors.toMap(r -> uuid(r[0]), r -> String.valueOf(r[1]), (a, b) -> a, HashMap::new));
    }

    @SuppressWarnings("unchecked")
    private CustomerInfo customerInfo(UUID company, UUID customerId) {
        List<Object[]> rows = em.createNativeQuery("SELECT customer_code, COALESCE(NULLIF(TRIM(company_name), ''), TRIM(CONCAT_WS(' ', first_name, last_name))), "
                        + "mobile, email, gst_number, CONCAT_WS(', ', billing_address_line1, billing_address_line2, billing_city, billing_state, billing_pincode) "
                        + "FROM customers WHERE id = :id AND company_id = :c")
                .setParameter("id", TimeOrderedUuid.toBytes(customerId)).setParameter("c", TimeOrderedUuid.toBytes(company)).getResultList();
        if (rows.isEmpty()) {
            return new CustomerInfo(customerId, null, null, null, null, null, null);
        }
        Object[] r = rows.get(0);
        return new CustomerInfo(customerId, str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]));
    }

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static UUID uuid(Object bytes) {
        ByteBuffer b = ByteBuffer.wrap((byte[]) bytes);
        return new UUID(b.getLong(), b.getLong());
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Invoices belong to a company."));
    }

    private String actor() { return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system"); }
}
