package com.courier.modules.shipment.application.command;

import com.courier.modules.ewaybill.application.command.EwayBillDataCommand;
import com.courier.modules.shipment.domain.ShipmentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything Shipment Booking needs to book one shipment.
 *
 * @param manualShipmentNumber optional — used verbatim instead of the auto-generated
 *                              shipment number when supplied; must be unique within the
 *                              company
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
 * @param crossing          routes this shipment through {@code crossingBranchIds} in order,
 *                          instead of straight to the delivery branch
 * @param crossingBranchIds required, at least one branch, when {@code crossing} is true —
 *                          the intermediate hubs in the order the shipment passes through them
 * @param crossingCharge    the whole route's crossing charge (not per hop); defaults to
 *                          zero when {@code crossing} is true and this is null
 * @param invoiceValue      drives whether an E-Way Bill is mandatory — see
 *                          {@code EwayBillService.isRequired}; null is never mandatory
 * @param ewayBill          the E-Way Bill's own data, required when {@code invoiceValue}
 *                          exceeds the company's threshold (booking is refused otherwise),
 *                          optional and simply attached-if-given below it
 */
public record CreateShipmentCommand(
        UUID bookingBranchId,
        UUID deliveryBranchId,
        String manualShipmentNumber,
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
        BigDecimal odaCharge,
        BigDecimal freightFactorOverride,
        List<ShipmentItemCommand> items,
        BigDecimal actualWeight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        Boolean crossing,
        List<UUID> crossingBranchIds,
        BigDecimal crossingCharge,
        BigDecimal invoiceValue,
        EwayBillDataCommand ewayBill
) {
}
