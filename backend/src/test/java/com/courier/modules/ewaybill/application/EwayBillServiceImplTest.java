package com.courier.modules.ewaybill.application;

import com.courier.modules.company.application.CompanySettingsService;
import com.courier.modules.company.domain.CompanySettings;
import com.courier.modules.ewaybill.application.command.CreateEwayBillCommand;
import com.courier.modules.ewaybill.application.command.EwayBillDataCommand;
import com.courier.modules.ewaybill.application.provider.EwayBillProvider;
import com.courier.modules.ewaybill.domain.EwayBill;
import com.courier.modules.ewaybill.domain.EwayBillRepository;
import com.courier.modules.ewaybill.domain.EwayBillStatus;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EwayBillServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID SHIPMENT = UUID.randomUUID();

    @Mock private EwayBillRepository repository;
    @Mock private EwayBillProvider provider;
    @Mock private CompanySettingsService companySettingsService;
    @Mock private FileStoragePort fileStoragePort;
    @Mock private AuditService auditService;

    private EwayBillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EwayBillServiceImpl(repository, provider, companySettingsService, fileStoragePort, auditService);
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, COMPANY, "ops@test.com", Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));

        CompanySettings settings = CompanySettings.builder().build();
        settings.setCompanyId(COMPANY);
        when(companySettingsService.get()).thenReturn(settings); // default threshold 50000.0000

        when(repository.save(any(EwayBill.class))).thenAnswer(inv -> {
            EwayBill b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------- isRequired / gate

    @Test
    @DisplayName("invoice value at or under the threshold is never mandatory")
    void notRequiredAtOrUnderThreshold() {
        assertThat(service.isRequired(new BigDecimal("50000.00"))).isFalse();
        assertThat(service.isRequired(new BigDecimal("100.00"))).isFalse();
        assertThat(service.isRequired(null)).isFalse();
    }

    @Test
    @DisplayName("invoice value over the threshold is mandatory")
    void requiredOverThreshold() {
        assertThat(service.isRequired(new BigDecimal("50000.01"))).isTrue();
    }

    @Test
    @DisplayName("optional E-Way Bill: booking proceeds with no E-Way Bill at all")
    void optionalAllowsNoEwayBill() {
        service.enforceBookingRequirement(new BigDecimal("100.00"), null);
        // no exception
    }

    @Test
    @DisplayName("mandatory E-Way Bill missing entirely — booking is refused with the exact wording")
    void mandatoryMissingRefused() {
        assertThatThrownBy(() -> service.enforceBookingRequirement(new BigDecimal("60000"), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("E-Way Bill is mandatory because invoice value exceeds ₹50,000.");
    }

    @Test
    @DisplayName("mandatory E-Way Bill supplied but fails provider validation — booking is refused")
    void mandatoryInvalidRefused() {
        when(provider.validate(any())).thenReturn(EwayBillProvider.ValidationOutcome.invalid("bad number"));

        assertThatThrownBy(() -> service.enforceBookingRequirement(new BigDecimal("60000"), sampleData()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mandatory")
                .hasMessageContaining("bad number");
    }

    @Test
    @DisplayName("mandatory E-Way Bill supplied and valid — booking proceeds")
    void mandatoryValidPasses() {
        when(provider.validate(any())).thenReturn(EwayBillProvider.ValidationOutcome.ok());

        service.enforceBookingRequirement(new BigDecimal("60000"), sampleData());
        // no exception
    }

    // ------------------------------------------------------------- upsertForShipment

    @Test
    @DisplayName("upsertForShipment is a no-op when no E-Way Bill data is given")
    void upsertNoopWhenNull() {
        EwayBill result = service.upsertForShipment(SHIPMENT, null);

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("upsertForShipment creates a fresh VALIDATED row when none exists yet")
    void upsertCreatesWhenNone() {
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of());
        when(provider.validate(any())).thenReturn(EwayBillProvider.ValidationOutcome.ok());

        EwayBill saved = service.upsertForShipment(SHIPMENT, sampleData());

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.VALIDATED);
        assertThat(saved.getShipmentId()).isEqualTo(SHIPMENT);
    }

    @Test
    @DisplayName("upsertForShipment updates the existing non-cancelled row in place")
    void upsertUpdatesExisting() {
        EwayBill existing = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.PENDING)
                .invoiceNumber("OLD-INV").invoiceDate(LocalDate.now()).invoiceValue(BigDecimal.TEN).build();
        existing.setId(UUID.randomUUID());
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of(existing));
        when(provider.validate(any())).thenReturn(EwayBillProvider.ValidationOutcome.ok());

        EwayBill saved = service.upsertForShipment(SHIPMENT, sampleData());

        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.getInvoiceNumber()).isEqualTo("INV-001");
    }

    @Test
    @DisplayName("upsertForShipment issues a fresh row when the only existing one is cancelled")
    void upsertReissuesAfterCancellation() {
        EwayBill cancelled = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.CANCELLED)
                .invoiceNumber("OLD-INV").invoiceDate(LocalDate.now()).invoiceValue(BigDecimal.TEN).build();
        cancelled.setId(UUID.randomUUID());
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of(cancelled));
        when(provider.validate(any())).thenReturn(EwayBillProvider.ValidationOutcome.ok());

        EwayBill saved = service.upsertForShipment(SHIPMENT, sampleData());

        assertThat(saved.getId()).isNotEqualTo(cancelled.getId());
    }

    @Test
    @DisplayName("upsertForShipment marks the row INVALID when the provider refuses it, without throwing")
    void upsertMarksInvalidWithoutThrowing() {
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of());
        when(provider.validate(any())).thenReturn(EwayBillProvider.ValidationOutcome.invalid("bad"));

        EwayBill saved = service.upsertForShipment(SHIPMENT, sampleData());

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.INVALID);
    }

    // ------------------------------------------------------------- standalone lifecycle

    @Test
    @DisplayName("create translates a DB constraint violation (e.g. no such shipment) into a 422")
    void createTranslatesConstraintViolation() {
        when(repository.save(any(EwayBill.class))).thenThrow(new DataIntegrityViolationException("fk violation"));

        assertThatThrownBy(() -> service.create(new CreateEwayBillCommand(SHIPMENT, sampleData())))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("cancel refuses an already-cancelled row")
    void cancelRefusesDoubleCancel() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.CANCELLED)
                .invoiceNumber("INV").invoiceDate(LocalDate.now()).invoiceValue(BigDecimal.TEN).build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));

        assertThatThrownBy(() -> service.cancel(id, "oops"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("validate refuses a cancelled row")
    void validateRefusesCancelled() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.CANCELLED)
                .invoiceNumber("INV").invoiceDate(LocalDate.now()).invoiceValue(BigDecimal.TEN).build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));

        assertThatThrownBy(() -> service.validate(id))
                .isInstanceOf(BusinessRuleException.class);
    }

    private EwayBillDataCommand sampleData() {
        return new EwayBillDataCommand("123456789012", "INV-001", LocalDate.now(), new BigDecimal("60000"),
                "INVOICE", null, null, null, "MH12AB1234", 120, null, null, null, null);
    }
}
