package com.courier.modules.legacyimport.application;

import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentChargeRepository;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.config.JpaAuditingConfig;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.AfterEach;
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
 * Real-schema check of the writer: the mock tests cannot see NOT NULL columns. Same env-var
 * gate as {@code LegacyImportSchemaTest} — scratch MySQL only.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, LegacyShipmentWriter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "SECURITY_IT_DB_URL", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=${SECURITY_IT_DB_URL}",
        "spring.datasource.username=${SECURITY_IT_DB_USER:root}",
        "spring.datasource.password=${SECURITY_IT_DB_PASSWORD:}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class LegacyShipmentWriterIT {

    @Autowired
    private LegacyShipmentWriter writer;
    @Autowired
    private ShipmentRepository shipments;
    @Autowired
    private ShipmentChargeRepository charges;
    @Autowired
    private ShipmentStatusHistoryRepository history;

    @AfterEach
    void clear() {
        CompanyContext.clear();
    }

    @Test
    void insertsAllThreeRowsAgainstTheRealSchema() {
        UUID company = TimeOrderedUuid.generate();
        CompanyContext.setCompanyId(company);
        String no = "IT" + UUID.randomUUID().toString().substring(0, 8);
        LegacyRow row = new LegacyRow(1, no, LocalDate.of(2026, 11, 15), "C", "R", "BHOSARI", "PUNE", 1,
                new BigDecimal("0.00"), new BigDecimal("0.00"), null);

        UUID id = writer.insert(row, TimeOrderedUuid.generate(), TimeOrderedUuid.generate(),
                TimeOrderedUuid.generate(), TimeOrderedUuid.generate());

        Shipment s = shipments.findByCompanyIdAndTrackingNumber(company, no).orElseThrow();
        assertThat(s.getId()).isEqualTo(id);
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(s.isLegacyImport()).isTrue();
        assertThat(charges.findByShipmentIdIn(java.util.List.of(id))).hasSize(1);
        assertThat(history.findAll().stream().filter(h -> id.equals(h.getShipmentId()))).hasSize(1);
    }
}
