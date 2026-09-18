package com.courier.modules.shipment.application;

import com.courier.modules.shipment.api.dto.PublicTrackEventResponse;
import com.courier.modules.shipment.api.dto.PublicTrackResponse;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatusHistoryRepository;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicTrackingServiceImpl implements PublicTrackingService {

    private static final String ENTITY = "Shipment";

    private final ShipmentRepository shipmentRepository;
    private final ShipmentStatusHistoryRepository shipmentStatusHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public PublicTrackResponse track(String number) {
        String trimmed = number == null ? "" : number.trim();
        if (trimmed.isEmpty()) {
            throw new ResourceNotFoundException(ENTITY, number);
        }

        Shipment shipment = shipmentRepository.findByTrackingNumberOrShipmentNumberForPublicTracking(trimmed)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, trimmed));

        var timeline = shipmentStatusHistoryRepository.findAllByShipmentIdOrderByChangedAtAsc(shipment.getId())
                .stream()
                .map(h -> new PublicTrackEventResponse(h.getStatus(), h.getChangedAt()))
                .toList();

        return new PublicTrackResponse(
                shipment.getTrackingNumber(), shipment.getShipmentNumber(), shipment.getStatus(),
                shipment.getBookingDate(), shipment.getExpectedDeliveryDate(),
                shipment.getFromCity(), shipment.getToCity(), timeline);
    }
}
