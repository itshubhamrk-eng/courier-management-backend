package com.courier.modules.legacyimport.application;

import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.legacyimport.domain.LegacyImportBatch;
import com.courier.modules.legacyimport.domain.LegacyImportBatchRepository;
import com.courier.modules.legacyimport.domain.LegacyOriginBranch;
import com.courier.modules.legacyimport.domain.LegacyOriginBranchRepository;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.domain.PaymentModeRepository;
import com.courier.modules.master.domain.ServiceType;
import com.courier.modules.master.domain.ServiceTypeRepository;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyImportCommitTest {

    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ShipmentChargeRepository charges = mock(ShipmentChargeRepository.class);
    private final LegacyOriginBranchRepository origins = mock(LegacyOriginBranchRepository.class);
    private final LegacyImportBatchRepository batches = mock(LegacyImportBatchRepository.class);
    private final PaymentModeRepository paymentModes = mock(PaymentModeRepository.class);
    private final ServiceTypeRepository serviceTypes = mock(ServiceTypeRepository.class);
    private final LegacyShipmentWriter writer = mock(LegacyShipmentWriter.class);
    private final AuditService audit = mock(AuditService.class);
    private final LegacyImportService service = new LegacyImportService(shipments, charges,
            mock(BranchRepository.class), origins, batches, paymentModes, serviceTypes, writer, audit);

    private final UUID company = TimeOrderedUuid.generate();
    private final UUID branch = TimeOrderedUuid.generate();
    private final UUID paidId = TimeOrderedUuid.generate();
    private final UUID serviceId = TimeOrderedUuid.generate();

    @BeforeEach
    void setUp() {
        CompanyContext.setCompanyId(company);
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of());
        when(charges.findByShipmentIdIn(any())).thenReturn(List.of());
        when(origins.findAllByCompanyId(company)).thenReturn(List.of(
                LegacyOriginBranch.builder().originKey("BHOSARI").branchId(branch).build()));
        PaymentMode paid = mock(PaymentMode.class);
        when(paid.getId()).thenReturn(paidId);
        when(paymentModes.findByCodeWithinCompany("PAID", company)).thenReturn(Optional.of(paid));
        ServiceType standard = mock(ServiceType.class);
        when(standard.getId()).thenReturn(serviceId);
        when(standard.isActive()).thenReturn(true);
        when(serviceTypes.findByCodeWithinCompany("STANDARD", company)).thenReturn(Optional.of(standard));
        when(batches.save(any(LegacyImportBatch.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private static String tr(String no, String from) {
        return "<tr><td>15/11/2026</td><td>" + no + "</td><td>C</td><td>R</td><td>" + from
                + "</td><td>PUNE</td><td>1</td><td>2.5</td><td>50</td></tr>";
    }

    private static byte[] file(String... rows) {
        return ("<table><tr><th>Date</th><th>Consignment No</th><th>Consignor</th><th>Consignee</th><th>From</th>"
                + "<th>To</th><th>Package</th><th>Actual Weight</th><th>Total</th></tr>" + String.join("", rows)
                + "</table>").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void commitBooksEveryNewRowWithMappedBranchAndDefaults() {
        CommitResult r = service.commit("a.xls", file(tr("N1", "BHOSARI"), tr("N2", " bhosari")));

        assertThat(r.created()).isEqualTo(2);
        verify(writer, times(2)).insert(any(LegacyRow.class), eq(branch), eq(serviceId), eq(paidId), eq(r.batchId()));
        verify(audit).record(eq(AuditAction.LEGACY_IMPORT_COMMITTED), eq("LegacyImportBatch"), eq(r.batchId()), any());
    }

    @Test
    void existingShipmentIsNeverWritten() {
        Shipment s = Shipment.builder().trackingNumber("N1").shipmentNumber("N1").build();
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of(s));

        CommitResult r = service.commit("a.xls", file(tr("N1", "BHOSARI")));

        assertThat(r.created()).isZero();
        assertThat(r.skippedExisting()).isEqualTo(1);
        verify(writer, never()).insert(any(), any(), any(), any(), any());
        verify(shipments, never()).save(any());
    }

    @Test
    void secondCommitOfSameFileCreatesNothing() {
        service.commit("a.xls", file(tr("N1", "BHOSARI")));
        Shipment s = Shipment.builder().trackingNumber("N1").shipmentNumber("N1").build();
        when(shipments.findAllByCompanyIdAndTrackingNumberIn(any(), any())).thenReturn(List.of(s));

        CommitResult second = service.commit("a.xls", file(tr("N1", "BHOSARI")));

        assertThat(second.created()).isZero();
        assertThat(second.batchId()).isNull();
        // the first commit saves its batch twice (create, then final counts); the no-op re-run adds none
        verify(batches, times(2)).save(any(LegacyImportBatch.class));
        verify(writer, times(1)).insert(any(), any(), any(), any(), any());
    }

    @Test
    void refusesWhenOriginUnmappedAndWritesNothing() {
        assertThatThrownBy(() -> service.commit("a.xls", file(tr("N1", "BHIWANDI"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("BHIWANDI");

        verify(batches, never()).save(any());
        verify(writer, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void refusesWhenNoPaidModeAndWritesNothing() {
        when(paymentModes.findByCodeWithinCompany(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commit("a.xls", file(tr("N1", "BHOSARI"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("PAID");

        verify(batches, never()).save(any());
    }

    @Test
    void fallsBackToFirstActiveServiceTypeWhenNoStandard() {
        when(serviceTypes.findByCodeWithinCompany("STANDARD", company)).thenReturn(Optional.empty());
        ServiceType other = mock(ServiceType.class);
        UUID otherId = TimeOrderedUuid.generate();
        when(other.getId()).thenReturn(otherId);
        when(other.isActive()).thenReturn(true);
        when(other.getCompanyId()).thenReturn(company);
        when(serviceTypes.findAll()).thenReturn(List.of(other));

        service.commit("a.xls", file(tr("N1", "BHOSARI")));

        verify(writer).insert(any(), eq(branch), eq(otherId), eq(paidId), any());
    }

    @Test
    void oneBadRowDoesNotStopTheOthers() {
        doThrow(new IllegalStateException("boom")).when(writer)
                .insert(org.mockito.ArgumentMatchers.argThat(r -> r != null && r.consignmentNo().equals("N2")),
                        any(), any(), any(), any());

        CommitResult r = service.commit("a.xls", file(tr("N1", "BHOSARI"), tr("N2", "BHOSARI"), tr("N3", "BHOSARI")));

        assertThat(r.created()).isEqualTo(2);
        assertThat(r.invalid()).isEqualTo(1);
        assertThat(r.rows().get(1).reason()).contains("boom");
    }

    @Test
    void invalidRowsAreReportedNotImported() {
        CommitResult r = service.commit("a.xls", file(tr("N1", "BHOSARI"), tr(" ", "BHOSARI")));

        assertThat(r.created()).isEqualTo(1);
        assertThat(r.invalid()).isEqualTo(1);
    }

    /** The history-only guarantee, structurally: the module must not even be able to reach
     *  an event bus, the wallet, or any notification sender. */
    @Test
    void moduleHasNoDependencyOnEventsWalletOrNotifications() {
        for (Class<?> c : List.of(LegacyImportService.class, LegacyShipmentWriter.class)) {
            for (Field f : c.getDeclaredFields()) {
                assertThat(f.getType()).isNotEqualTo(ApplicationEventPublisher.class);
                assertThat(f.getType().getName().toLowerCase())
                        .doesNotContain("wallet").doesNotContain("notification").doesNotContain("whatsapp")
                        .doesNotContain("communication").doesNotContain("sms");
            }
        }
        assertThat(Arrays.stream(LegacyShipmentWriter.class.getDeclaredFields()).map(Field::getName))
                .noneMatch(n -> n.toLowerCase().contains("publisher"));
    }
}
