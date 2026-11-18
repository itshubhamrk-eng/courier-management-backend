package com.courier.modules.legacyimport.domain;

import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations (V145/V146) on a scratch MySQL schema and checks the new
 * entity mappings with {@code ddl-auto=validate}. Skipped unless {@code SECURITY_IT_DB_URL}
 * is set — same gate and same warning as {@code SecurityIncidentRepositoryTest}: never point
 * it at a database that holds data you care about.
 */
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
class LegacyImportSchemaTest {

    @Autowired
    private ShipmentRepository shipments;
    @Autowired
    private LegacyImportBatchRepository batches;
    @Autowired
    private LegacyOriginBranchRepository origins;

    @Test
    void shipmentLegacyFlagRoundTripsAndDefaultsToFalse() {
        UUID company = TimeOrderedUuid.generate();
        UUID batch = TimeOrderedUuid.generate();
        Shipment legacy = shipment("LEG-1", true, batch);
        legacy.setCompanyId(company);
        Shipment normal = shipment("NRM-1", false, null);
        normal.setCompanyId(company);
        shipments.saveAndFlush(legacy);
        shipments.saveAndFlush(normal);

        assertThat(shipments.findByCompanyIdAndTrackingNumber(company, "LEG-1").orElseThrow().isLegacyImport()).isTrue();
        assertThat(shipments.findByCompanyIdAndTrackingNumber(company, "LEG-1").orElseThrow().getLegacyBatchId()).isEqualTo(batch);
        assertThat(shipments.findByCompanyIdAndTrackingNumber(company, "NRM-1").orElseThrow().isLegacyImport()).isFalse();
    }

    @Test
    void batchAndOriginMapRoundTrip() {
        UUID company = TimeOrderedUuid.generate();
        LegacyImportBatch b = LegacyImportBatch.builder().fileName("f.xls").fileHash("abc").newCount(2).build();
        b.setCompanyId(company);
        batches.saveAndFlush(b);
        LegacyOriginBranch o = LegacyOriginBranch.builder().originKey("BHOSARI").branchId(TimeOrderedUuid.generate()).build();
        o.setCompanyId(company);
        origins.saveAndFlush(o);

        assertThat(batches.findAllByCompanyIdOrderByCreatedAtDesc(company)).hasSize(1);
        assertThat(origins.findByCompanyIdAndOriginKey(company, "BHOSARI")).isPresent();
    }

    private static Shipment shipment(String number, boolean legacy, UUID batch) {
        return Shipment.builder().shipmentNumber(number).trackingNumber(number)
                .bookingDate(LocalDate.of(2026, 11, 1)).bookingBranchId(TimeOrderedUuid.generate())
                .pickupPincode("000000").deliveryPincode("000000")
                .senderName("s").senderAddress("a").senderContact("0")
                .receiverName("r").receiverAddress("a").receiverContact("0")
                .serviceTypeId(TimeOrderedUuid.generate()).paymentModeId(TimeOrderedUuid.generate())
                .actualWeight(BigDecimal.ONE).chargeableWeight(BigDecimal.ONE)
                .declaredValue(BigDecimal.ONE).legacyImport(legacy).legacyBatchId(batch).build();
    }
}
