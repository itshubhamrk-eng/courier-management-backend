package com.courier.modules.shipment.api.dto;

public record DispatchOtpRequestResponse(
        String maskedMobile,
        int expiresInMinutes
) {
}
