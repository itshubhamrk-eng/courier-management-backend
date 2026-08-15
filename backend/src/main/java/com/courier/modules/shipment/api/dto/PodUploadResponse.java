package com.courier.modules.shipment.api.dto;

/** Response of {@code POST /shipment-movement/{shipmentId}/pod-upload} — the caller
 *  passes {@code url} straight into {@link DeliverRequest#signatureUrl()}/{@code photoUrl}. */
public record PodUploadResponse(String url) {
}
