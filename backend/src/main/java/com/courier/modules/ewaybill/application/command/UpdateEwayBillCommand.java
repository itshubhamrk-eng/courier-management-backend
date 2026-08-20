package com.courier.modules.ewaybill.application.command;

/** Body of {@code PUT /eway-bills/{id}}. Refused once the row is {@code CANCELLED}. */
public record UpdateEwayBillCommand(Long expectedVersion, EwayBillDataCommand data) {
}
