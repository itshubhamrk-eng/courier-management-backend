package com.courier.modules.shipment.application.command;

import com.courier.modules.shipment.domain.ShipmentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything Shipment Booking needs to book one shipment.
 *
 * @param bookingDate      defaults to today when null, the same convention Rate Master's
 *                         and the Pricing Engine's own calculators use
 * @param items            the packed item grid; when empty, {@code actualWeight} (and
 *                         optionally the three dimensions, for a volumetric contribution)
 *                         must be supplied directly — a single-package booking with
 *                         nothing itemised is not refused, it just has one implicit item
 * @param actualWeight     required only when {@code items} is empty
 * @param length           optional, used only when {@code items} is empty
 * @param width            optional, used only when {@code items} is empty
 * @param height           optional, used only when {@code items} is empty
 */
public record CreateShipmentCommand(
        UUID bookingBranchId,
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
