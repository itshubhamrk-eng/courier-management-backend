package com.courier.modules.customerbilling;

import com.courier.modules.customerbilling.application.BillingCycleService;
import com.courier.modules.customerbilling.application.CustomerBillingService;
import com.courier.modules.customerbilling.application.CustomerBillingServiceImpl;
import com.courier.modules.customerbilling.application.CustomerInvoiceGenerator;
import com.courier.modules.customerbilling.application.InvoiceEmailSender;
import com.courier.modules.customerbilling.domain.CustomerInvoice;
import com.courier.modules.customerbilling.domain.CustomerInvoiceLineRepository;
import com.courier.modules.customerbilling.domain.CustomerInvoicePaymentRepository;
import com.courier.modules.customerbilling.domain.CustomerInvoiceRepository;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proof upload and payment-gating rules of {@link CustomerBillingServiceImpl} against a mocked
 * file store — the real S3 store is not available in dev, so the upload path is covered here.
 */
class CustomerBillingProofTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private final CustomerInvoiceRepository invoices = mock(CustomerInvoiceRepository.class);
    private final CustomerInvoicePaymentRepository payments = mock(CustomerInvoicePaymentRepository.class);
    private final FileStoragePort storage = mock(FileStoragePort.class);
    private CustomerBillingServiceImpl service;
    private CustomerInvoice invoice;

    @BeforeEach
    void setUp() {
        service = new CustomerBillingServiceImpl(invoices, mock(CustomerInvoiceLineRepository.class), payments,
                mock(CustomerInvoiceGenerator.class), mock(BillingCycleService.class), mock(InvoiceEmailSender.class),
                storage, mock(AuditService.class));
        invoice = CustomerInvoice.builder().customerId(UUID.randomUUID()).invoiceNumber("BI2610-0001")
                .totalAmount(new BigDecimal("100.00")).prepaidAmount(BigDecimal.ZERO).build();
        // view() reads customer display data with native SQL: an empty result is enough here.
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "em", em);
        when(invoices.findByIdWithinCompany(any(), eq(COMPANY))).thenReturn(Optional.of(invoice));
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("an image proof is stored under the company/invoice prefix with a unique, sanitised key")
    void uploadStoresImage() {
        when(storage.upload(any())).thenReturn(new FileStoragePort.StoredFile("https://files/x", "k"));
        CustomerBillingService.UploadedProof up = service.uploadProof(UUID.randomUUID(), new byte[]{1, 2, 3}, "bank slip (1).png", "image/png");

        ArgumentCaptor<FileStoragePort.UploadRequest> req = ArgumentCaptor.forClass(FileStoragePort.UploadRequest.class);
        verify(storage).upload(req.capture());
        assertThat(req.getValue().keyPrefix()).startsWith("customer-invoice-proof/" + COMPANY);
        assertThat(req.getValue().filename()).endsWith("bank_slip__1_.png");
        assertThat(up.url()).isEqualTo("https://files/x");
        assertThat(up.name()).isEqualTo("bank slip (1).png");
    }

    @Test
    @DisplayName("proof must be an image or PDF, non-empty and at most 10 MB; nothing is stored otherwise")
    void uploadValidation() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.uploadProof(id, new byte[]{1}, "a.exe", "application/octet-stream"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.uploadProof(id, new byte[0], "a.png", "image/png")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.uploadProof(id, new byte[10 * 1024 * 1024 + 1], "a.png", "image/png"))
                .isInstanceOf(BusinessRuleException.class);
        verify(storage, never()).upload(any());
    }

    @Test
    @DisplayName("an unapproved invoice refuses payments; approving opens it")
    void paymentsNeedApproval() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.recordPayment(id, new BigDecimal("10"), null, null, null, null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Approve");
        invoice.approve(UUID.randomUUID());
        service.recordPayment(id, new BigDecimal("10"), null, null, "UTR1", null, "https://files/p.png", "p.png");
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("10");
        assertThat(invoice.getStatus().name()).isEqualTo("PARTLY_PAID");
    }
}
