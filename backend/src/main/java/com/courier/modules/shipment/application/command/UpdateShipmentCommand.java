package com.courier.modules.shipment.application.command;

import com.courier.modules.shipment.domain.ShipmentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full replacement of a shipment's editable fields, carrying the last-read {@code version}
 * for the optimistic-lock check every module's own full-replacement PUT follows.
 *
 * <p>{@code bookingBranchId} is absent: it is immutable once booked, the same treatment
 * {@code Rate}'s route or {@code Customer}'s code gets — see {@link
 * com.courier.modules.shipment.domain.Shipment}.
 */
public record UpdateShipmentCommand(
        Long expectedVersion,
        UUID deliveryBranchId,
        String pickupPincode,
        String deliveryPincode,
        String senderName,
        String senderAddress,
        String senderContact,
        String receiverName,
        String receiverAddress,
        String receiverContact,
        UUID serviceTypeId,
        UUID packageTypeId,
        UUID paymentModeId,
        ShipmentType shipmentType,
        LocalDate bookingDate,
        BigDecimal declaredValue,
        Integer numberOfPackages,
        String remarks,
        BigDecimal otherCharges,
        BigDecimal freightFactorOverride,
        List<ShipmentItemCommand> items,
        BigDecimal actualWeight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height
) {
}
