package com.courier.modules.ewaybill.application;

import com.courier.modules.company.application.CompanySettingsService;
import com.courier.modules.company.domain.CompanySettings;
import com.courier.modules.ewaybill.application.EwayBillService.ShipmentEwayBillContext;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("E-Way Bill not required: invoice value at or under the threshold")
    void notRequiredAtOrUnderThreshold() {
        assertThat(service.isRequired(new BigDecimal("50000.00"))).isFalse();
        assertThat(service.isRequired(new BigDecimal("100.00"))).isFalse();
        assertThat(service.isRequired(null)).isFalse();

        service.requireBookingData(new BigDecimal("100.00"), null);
        // no exception — optional, booking proceeds with no E-Way Bill at all
    }

    @Test
    @DisplayName("E-Way Bill required: invoice value over the threshold")
    void requiredOverThreshold() {
        assertThat(service.isRequired(new BigDecimal("50000.01"))).isTrue();
    }

    @Test
    @DisplayName("mandatory but the minimum Part-A data is missing entirely — booking is refused")
    void mandatoryMissingDataRefused() {
        assertThatThrownBy(() -> service.requireBookingData(new BigDecimal("60000"), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("E-Way Bill is mandatory because invoice value exceeds ₹50,000.");
    }

    @Test
    @DisplayName("mandatory but invoice number is blank — booking is refused")
    void mandatoryMissingInvoiceNumberRefused() {
        EwayBillDataCommand incomplete = new EwayBillDataCommand(null, "  ", LocalDate.now(),
                new BigDecimal("60000"), null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.requireBookingData(new BigDecimal("60000"), incomplete))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("mandatory with the minimum data present — booking proceeds, no provider call")
    void mandatoryWithDataPasses() {
        service.requireBookingData(new BigDecimal("60000"), sampleData());

        verify(provider, never()).generatePartA(any());
    }

    // ------------------------------------------------------------- Part-A generation

    @Test
    @DisplayName("Part-A generation success moves the row to PART_A_GENERATED with the provider's number")
    void partAGenerationSuccess() {
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of());
        when(provider.generatePartA(any())).thenReturn(EwayBillProvider.PartAResult.success(
                "123456789012", Instant.now(), Instant.now().plusSeconds(86400), "GSP", "ref-1"));

        EwayBill saved = service.generatePartAForShipment(SHIPMENT, sampleData(), sampleContext());

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.PART_A_GENERATED);
        assertThat(saved.getEwayBillNumber()).isEqualTo("123456789012");
        assertThat(saved.getShipmentId()).isEqualTo(SHIPMENT);
    }

    @Test
    @DisplayName("Part-A generation failure marks the row FAILED without throwing — booking must still commit")
    void partAGenerationFailureDoesNotThrow() {
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of());
        when(provider.generatePartA(any())).thenReturn(EwayBillProvider.PartAResult.failure("GSP", "GSTIN mismatch"));

        EwayBill saved = service.generatePartAForShipment(SHIPMENT, sampleData(), sampleContext());

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.FAILED);
        assertThat(saved.getLastError()).contains("GSTIN mismatch");
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a provider timeout/exception is caught and stored as FAILED, never rethrown")
    void providerTimeoutDoesNotThrow() {
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of());
        when(provider.generatePartA(any())).thenThrow(new RuntimeException("connect timed out"));

        EwayBill saved = service.generatePartAForShipment(SHIPMENT, sampleData(), sampleContext());

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.FAILED);
        assertThat(saved.getLastError()).contains("provider error");
    }

    @Test
    @DisplayName("duplicate generation for the same shipment+invoice is refused — no second provider call")
    void duplicateGenerationPrevented() {
        EwayBill existing = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.PART_A_GENERATED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(new BigDecimal("60000"))
                .ewayBillNumber("123456789012").build();
        existing.setId(UUID.randomUUID());
        when(repository.findAllByShipmentIdWithinCompany(SHIPMENT, COMPANY)).thenReturn(List.of(existing));

        EwayBill result = service.generatePartAForShipment(SHIPMENT, sampleData(), sampleContext());

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(provider, never()).generatePartA(any());
    }

    @Test
    @DisplayName("a null command is a no-op")
    void nullDataIsNoop() {
        assertThat(service.generatePartAForShipment(SHIPMENT, null, sampleContext())).isNull();
        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------- Part-B (dispatch)

    @Test
    @DisplayName("vehicle assignment triggers Part-B for a shipment sitting at PART_A_GENERATED")
    void vehicleAssignmentTriggersPartB() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.PART_A_GENERATED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(new BigDecimal("60000"))
                .ewayBillNumber("123456789012").build();
        bill.setId(UUID.randomUUID());
        when(repository.findAllByShipmentIdInWithinCompany(List.of(SHIPMENT), COMPANY)).thenReturn(List.of(bill));
        when(provider.updatePartB(any())).thenReturn(EwayBillProvider.PartBResult.success("ref-2"));

        service.triggerPartBForShipments(List.of(SHIPMENT), "MH12AB1234", null, "ROAD");

        assertThat(bill.getStatus()).isEqualTo(EwayBillStatus.GENERATED);
        assertThat(bill.getVehicleNumber()).isEqualTo("MH12AB1234");
    }

    @Test
    @DisplayName("Part-B failure marks FAILED without throwing — dispatch must still commit")
    void partBFailureDoesNotThrow() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.PART_A_GENERATED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(new BigDecimal("60000"))
                .ewayBillNumber("123456789012").build();
        bill.setId(UUID.randomUUID());
        when(repository.findAllByShipmentIdInWithinCompany(List.of(SHIPMENT), COMPANY)).thenReturn(List.of(bill));
        when(provider.updatePartB(any())).thenReturn(EwayBillProvider.PartBResult.failure("vehicle not registered"));

        service.triggerPartBForShipments(List.of(SHIPMENT), "MH12AB1234", null, "ROAD");

        assertThat(bill.getStatus()).isEqualTo(EwayBillStatus.FAILED);
        assertThat(bill.getLastError()).contains("vehicle not registered");
    }

    @Test
    @DisplayName("a shipment with no E-Way Bill, or one not past Part-A, is silently skipped at dispatch")
    void notPastPartAIsSkipped() {
        service.triggerPartBForShipments(List.of(SHIPMENT), "MH12AB1234", null, "ROAD");

        verify(provider, never()).updatePartB(any());
    }

    // ------------------------------------------------------------- retry

    @Test
    @DisplayName("retry refuses a row that is not FAILED/EXPIRED")
    void retryRefusesNonFailedRow() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.PART_A_GENERATED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(BigDecimal.TEN)
                .ewayBillNumber("123456789012").build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));

        assertThatThrownBy(() -> service.retry(id)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("retry re-attempts Part-A when the row never got a number")
    void retryReattemptsPartA() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.FAILED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(new BigDecimal("60000")).build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));
        when(provider.generatePartA(any())).thenReturn(EwayBillProvider.PartAResult.success(
                "123456789012", Instant.now(), Instant.now().plusSeconds(86400), "GSP", "ref-1"));

        EwayBill saved = service.retry(id);

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.PART_A_GENERATED);
        verify(provider, times(1)).generatePartA(any());
        verify(provider, never()).updatePartB(any());
    }

    @Test
    @DisplayName("retry re-attempts Part-B when Part-A already succeeded")
    void retryReattemptsPartB() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.FAILED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(new BigDecimal("60000"))
                .ewayBillNumber("123456789012").vehicleNumber("MH12AB1234").build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));
        when(provider.updatePartB(any())).thenReturn(EwayBillProvider.PartBResult.success("ref-2"));

        EwayBill saved = service.retry(id);

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.GENERATED);
        verify(provider, never()).generatePartA(any());
    }

    @Test
    @DisplayName("a failed retry throws — unlike the booking/dispatch flows, the user must see it")
    void retryThrowsOnRepeatedFailure() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.FAILED)
                .invoiceNumber("INV-001").invoiceDate(LocalDate.now()).invoiceValue(new BigDecimal("60000")).build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));
        when(provider.generatePartA(any())).thenReturn(EwayBillProvider.PartAResult.failure("GSP", "still invalid"));

        assertThatThrownBy(() -> service.retry(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still invalid");
        assertThat(bill.getStatus()).isEqualTo(EwayBillStatus.FAILED);
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
    @DisplayName("cancel calls the provider when a number was already issued")
    void cancelCallsProviderWhenNumberIssued() {
        EwayBill bill = EwayBill.builder().shipmentId(SHIPMENT).status(EwayBillStatus.GENERATED)
                .invoiceNumber("INV").invoiceDate(LocalDate.now()).invoiceValue(BigDecimal.TEN)
                .ewayBillNumber("123456789012").build();
        UUID id = UUID.randomUUID();
        bill.setId(id);
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(java.util.Optional.of(bill));
        when(provider.cancel("123456789012", "done")).thenReturn(EwayBillProvider.CancelResult.ok());

        EwayBill saved = service.cancel(id, "done");

        assertThat(saved.getStatus()).isEqualTo(EwayBillStatus.CANCELLED);
    }

    private EwayBillDataCommand sampleData() {
        return new EwayBillDataCommand(null, "INV-001", LocalDate.now(), new BigDecimal("60000"),
                "INVOICE", null, null, null, null, null, null, null, null, null, "27AAAAA0000A1Z5", "27BBBBB0000B1Z5");
    }

    private ShipmentEwayBillContext sampleContext() {
        return new ShipmentEwayBillContext("Sender Co", "123 Sender St", "411001",
                "Receiver Co", "456 Receiver St", "560001", "Electronics");
    }
}
