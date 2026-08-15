package com.courier.modules.shipment.api.dto;

/** Response of {@code POST /shipments/{id}/image-upload}. */
public record ShipmentImageUploadResponse(String url) {
}
