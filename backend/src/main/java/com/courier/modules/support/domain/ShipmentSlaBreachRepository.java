package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShipmentSlaBreachRepository extends JpaRepository<ShipmentSlaBreach, UUID> {

    boolean existsByCompanyIdAndShipmentIdAndStage(UUID companyId, UUID shipmentId, ShipmentSlaStage stage);
}
