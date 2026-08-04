package com.courier.modules.shipment.api.dto;

public record MovementOutcomeResponse(String reference, boolean success, String message) {
}
