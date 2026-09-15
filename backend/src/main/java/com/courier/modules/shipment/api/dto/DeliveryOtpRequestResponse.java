package com.courier.modules.shipment.api.dto;

public record DeliveryOtpRequestResponse(
        String maskedMobile,
        int expiresInMinutes
) {
}
