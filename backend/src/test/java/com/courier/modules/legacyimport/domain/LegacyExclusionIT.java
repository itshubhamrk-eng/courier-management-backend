package com.courier.modules.legacyimport.domain;

import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentSpecifications;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.config.JpaAuditingConfig;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Legacy rows must not appear as "pending POD upload": checked on the real schema (scratch MySQL only). */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "SECURITY_IT_DB_URL", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=${SECURITY_IT_DB_URL}",
        "spring.datasource.username=${SECURITY_IT_DB_USER:root}",
        "spring.datasource.password=${SECURITY_IT_DB_PASSWORD:}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class LegacyExclusionIT {

    @Autowired
    private ShipmentRepository shipments;

    private Shipment save(UUID company, UUID deliveryBranch, String no, boolean legacy) {
        Shipment s = Shipment.builder().shipmentNumber(no).trackingNumber(no)
                .bookingDate(LocalDate.of(2026, 11, 1)).bookingBranchId(TimeOrderedUuid.generate())
                .deliveryBranchId(deliveryBranch)
                .pickupPincode("000000").deliveryPincode("000000")
                .senderName("s").senderAddress("a").senderContact("0")
                .receiverName("r").receiverAddress("a").receiverContact("0")
                .serviceTypeId(TimeOrderedUuid.generate()).paymentModeId(TimeOrderedUuid.generate())
                .actualWeight(BigDecimal.ONE).chargeableWeight(BigDecimal.ONE)
                .declaredValue(BigDecimal.ONE).status(ShipmentStatus.DELIVERED).legacyImport(legacy).build();
        s.setCompanyId(company);
        return shipments.saveAndFlush(s);
    }

    @Test
    void dashboardPodCandidatesAndPodReviewCriteriaSkipLegacyRows() {
        UUID company = TimeOrderedUuid.generate();
        UUID branch = TimeOrderedUuid.generate();
        Shipment normal = save(company, branch, "N-" + UUID.randomUUID().toString().substring(0, 8), false);
        Shipment legacy = save(company, branch, "L-" + UUID.randomUUID().toString().substring(0, 8), true);
        Set<ShipmentStatus> relevant = Set.of(ShipmentStatus.DELIVERED, ShipmentStatus.OUT_FOR_DELIVERY);

        assertThat(shipments.findIdsByCompanyIdAndStatusIn(company, relevant)).containsExactly(normal.getId());
        assertThat(shipments.findIdsByCompanyIdAndDeliveryBranchIdAndStatusIn(company, branch, relevant))
                .containsExactly(normal.getId());

        ShipmentCriteria all = new ShipmentCriteria(relevant, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        List<Shipment> withLegacy = shipments.findAll(ShipmentSpecifications.matching(all)).stream()
                .filter(s -> company.equals(s.getCompanyId())).toList();
        List<Shipment> without = shipments.findAll(ShipmentSpecifications.matching(all.withoutLegacy())).stream()
                .filter(s -> company.equals(s.getCompanyId())).toList();

        assertThat(withLegacy).extracting(Shipment::getId).containsExactlyInAnyOrder(normal.getId(), legacy.getId());
        assertThat(without).extracting(Shipment::getId).containsExactly(normal.getId());
    }
}
