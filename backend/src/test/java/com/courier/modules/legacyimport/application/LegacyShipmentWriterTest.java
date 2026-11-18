package com.courier.modules.legacyimport.application;

import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCharge;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyShipmentWriterTest {

    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final ShipmentChargeRepository charges = mock(ShipmentChargeRepository.class);
    private final ShipmentStatusHistoryRepository history = mock(ShipmentStatusHistoryRepository.class);
    private final LegacyShipmentWriter writer = new LegacyShipmentWriter(shipments, charges, history);
    private final UUID company = TimeOrderedUuid.generate();
    private final UUID branch = TimeOrderedUuid.generate();
    private final UUID batch = TimeOrderedUuid.generate();

    @BeforeEach
    void setUp() {
        CompanyContext.setCompanyId(company);
        when(shipments.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private static LegacyRow row(String weight, String total) {
        return new LegacyRow(1, "SO912C2627215", LocalDate.of(2026, 11, 15), "RIYANSH MULTITRADE PVT LTD",
                "VENUBAI VAMAN BHISE", "BHOSARI", "CHINCHWAD", 2, new BigDecimal(weight), new BigDecimal(total), null);
    }

    @Test
    void writesDeliveredHistoryShipmentWithOneChargeAndOneHistoryRow() {
        UUID service = TimeOrderedUuid.generate();
        UUID paid = TimeOrderedUuid.generate();

        UUID id = writer.insert(row("3.82", "57.30"), branch, service, paid, batch);

        ArgumentCaptor<Shipment> s = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).save(s.capture());
        Shipment saved = s.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getTrackingNumber()).isEqualTo("SO912C2627215");
        assertThat(saved.getShipmentNumber()).isEqualTo("SO912C2627215");
        assertThat(saved.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(saved.isLegacyImport()).isTrue();
        assertThat(saved.getLegacyBatchId()).isEqualTo(batch);
        assertThat(saved.isPodApproved()).isTrue();
        assertThat(saved.isCommissionCredited()).isTrue();
        assertThat(saved.getBookingBranchId()).isEqualTo(branch);
        assertThat(saved.getCurrentLocationId()).isEqualTo(branch);
        assertThat(saved.getDeliveryBranchId()).isNull();
        assertThat(saved.getBookingDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(saved.getFromCity()).isEqualTo("BHOSARI");
        assertThat(saved.getToCity()).isEqualTo("CHINCHWAD");
        assertThat(saved.getSenderName()).isEqualTo("RIYANSH MULTITRADE PVT LTD");
        assertThat(saved.getReceiverName()).isEqualTo("VENUBAI VAMAN BHISE");
        assertThat(saved.getSenderAddress()).isEqualTo("N/A (legacy import)");
        assertThat(saved.getReceiverContact()).isEqualTo("0000000000");
        assertThat(saved.getPickupPincode()).isEqualTo("000000");
        assertThat(saved.getNumberOfPackages()).isEqualTo(2);
        assertThat(saved.getServiceTypeId()).isEqualTo(service);
        assertThat(saved.getPaymentModeId()).isEqualTo(paid);
        assertThat(saved.getDeclaredValue()).isEqualByComparingTo("57.30");

        ArgumentCaptor<ShipmentCharge> c = ArgumentCaptor.forClass(ShipmentCharge.class);
        verify(charges).save(c.capture());
        assertThat(c.getValue().getShipmentId()).isEqualTo(id);
        assertThat(c.getValue().getFreight()).isEqualByComparingTo("57.30");
        assertThat(c.getValue().getNetAmount()).isEqualByComparingTo("57.30");
        assertThat(c.getValue().getTotalCommission()).isEqualByComparingTo("0");

        ArgumentCaptor<ShipmentStatusHistory> h = ArgumentCaptor.forClass(ShipmentStatusHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(h.getValue().getPreviousStatus()).isNull();
        assertThat(h.getValue().getRemarks()).contains("legacy").contains(batch.toString());
        assertThat(h.getValue().getCompanyId()).isEqualTo(company);
    }

    @Test
    void zeroTotalAndZeroWeightStillImportWithPlaceholderDeclaredValue() {
        writer.insert(row("0.00", "0.00"), branch, TimeOrderedUuid.generate(), TimeOrderedUuid.generate(), batch);

        ArgumentCaptor<Shipment> s = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).save(s.capture());
        assertThat(s.getValue().getDeclaredValue()).isEqualByComparingTo("1.00");
        assertThat(s.getValue().getActualWeight()).isEqualByComparingTo("0.001");
        assertThat(s.getValue().getChargeableWeight()).isEqualByComparingTo("0.001");
        assertThat(s.getValue().getRemarks()).contains("weight not recorded");
    }
}
