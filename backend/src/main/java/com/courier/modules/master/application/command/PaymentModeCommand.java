package com.courier.modules.master.application.command;

/** Input to create or update a payment mode. See {@link CountryCommand} for the code/version rule. */
public record PaymentModeCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        Boolean collectAtBooking,
        Boolean collectAtDelivery,
        Boolean requiresCreditAccount,
        Boolean cashOnDelivery,
        Long expectedVersion
) {
}
