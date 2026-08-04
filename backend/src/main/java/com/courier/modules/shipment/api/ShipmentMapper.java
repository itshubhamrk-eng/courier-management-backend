package com.courier.modules.shipment.api;

import com.courier.modules.shipment.api.dto.AddShipmentDocumentRequest;
import com.courier.modules.shipment.api.dto.CreateShipmentRequest;
import com.courier.modules.shipment.api.dto.ShipmentChargeResponse;
import com.courier.modules.shipment.api.dto.ShipmentDocumentResponse;
import com.courier.modules.shipment.api.dto.ShipmentItemRequest;
import com.courier.modules.shipment.api.dto.ShipmentItemResponse;
import com.courier.modules.shipment.api.dto.ShipmentResponse;
import com.courier.modules.shipment.api.dto.ShipmentSearchRequest;
import com.courier.modules.shipment.api.dto.ShipmentStatusHistoryResponse;
import com.courier.modules.shipment.api.dto.ShipmentSummaryResponse;
import com.courier.modules.shipment.api.dto.UpdateShipmentRequest;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.application.command.CreateShipmentCommand;
import com.courier.modules.shipment.application.command.ShipmentItemCommand;
import com.courier.modules.shipment.application.command.UpdateShipmentCommand;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentDocument;
import com.courier.modules.shipment.domain.ShipmentItem;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Wire contract <-> application/domain types for Shipment Booking. */
@Component
public class ShipmentMapper {

    public CreateShipmentCommand toCommand(CreateShipmentRequest r) {
        return new CreateShipmentCommand(
                r.bookingBranchId(), r.deliveryBranchId(),
                r.pickupPincode(), r.deliveryPincode(),
                r.senderName(), r.senderAddress(), r.senderContact(),
                r.receiverName(), r.receiverAddress(), r.receiverContact(),
                r.serviceTypeId(), r.packageTypeId(), r.paymentModeId(),
                r.shipmentType(), r.bookingDate(), r.declaredValue(), r.numberOfPackages(),
                r.remarks(), toItemCommands(r.items()),
                r.actualWeight(), r.length(), r.width(), r.height());
    }

    public UpdateShipmentCommand toCommand(UpdateShipmentRequest r) {
        return new UpdateShipmentCommand(
                r.version(), r.deliveryBranchId(),
                r.pickupPincode(), r.deliveryPincode(),
                r.senderName(), r.senderAddress(), r.senderContact(),
                r.receiverName(), r.receiverAddress(), r.receiverContact(),
                r.serviceTypeId(), r.packageTypeId(), r.paymentModeId(),
                r.shipmentType(), r.bookingDate(), r.declaredValue(), r.numberOfPackages(),
                r.remarks(), toItemCommands(r.items()),
                r.actualWeight(), r.length(), r.width(), r.height());
    }

    private List<ShipmentItemCommand> toItemCommands(List<ShipmentItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(i -> new ShipmentItemCommand(
                i.itemName(), i.quantity(), i.weight(), i.lengthCm(), i.widthCm(), i.heightCm(),
                i.declaredValue(), i.fragile(), i.dangerousGoods())).toList();
    }

    public ShipmentCriteria toCriteria(ShipmentSearchRequest r) {
        ShipmentSearchRequest safe = r == null ? ShipmentSearchRequest.empty() : r;
        return new ShipmentCriteria(safe.status(), safe.bookingBranchId(), safe.deliveryBranchId(),
                safe.manifestId(), safe.bookingDateFrom(), safe.bookingDateTo(), safe.search());
    }

    public ShipmentService.AddDocumentCommand toCommand(AddShipmentDocumentRequest r) {
        return new ShipmentService.AddDocumentCommand(
                r.documentType(), r.documentName(), r.documentUrl(), r.remarks());
    }

    public ShipmentResponse toResponse(Shipment s, List<ShipmentItem> items) {
        return new ShipmentResponse(
                s.getId(), s.getCompanyId(), s.getShipmentNumber(), s.getTrackingNumber(),
                s.getBookingDate(), s.getBookingBranchId(), s.getDeliveryBranchId(), s.getManifestId(),
                s.getPickupPincode(), s.getDeliveryPincode(),
                s.getSenderName(), s.getSenderAddress(), s.getSenderContact(),
                s.getReceiverName(), s.getReceiverAddress(), s.getReceiverContact(),
                s.getServiceTypeId(), s.getPackageTypeId(), s.getPaymentModeId(),
                s.getShipmentType(), s.getExpectedDeliveryDate(),
                s.getActualWeight(), s.getVolumetricWeight(), s.getChargeableWeight(),
                s.getDeclaredValue(), s.getNumberOfPackages(), s.getStatus(), s.getRemarks(),
                s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedBy(), s.getUpdatedAt(), s.getVersion(),
                items.stream().map(this::toResponse).toList());
    }

    public ShipmentSummaryResponse toSummary(Shipment s, BigDecimal netAmount) {
        return new ShipmentSummaryResponse(
                s.getId(), s.getShipmentNumber(), s.getTrackingNumber(), s.getBookingDate(),
                s.getBookingBranchId(), s.getDeliveryBranchId(), s.getManifestId(),
                s.getSenderName(), s.getSenderContact(), s.getReceiverName(), s.getReceiverContact(),
                s.getChargeableWeight(), netAmount, s.getStatus(), s.getCreatedAt(), s.getVersion());
    }

    public ShipmentItemResponse toResponse(ShipmentItem i) {
        return new ShipmentItemResponse(
                i.getId(), i.getItemName(), i.getQuantity(), i.getWeight(),
                i.getLengthCm(), i.getWidthCm(), i.getHeightCm(),
                i.getDeclaredValue(), i.isFragile(), i.isDangerousGoods());
    }

    public ShipmentChargeResponse toResponse(ShipmentService.ShipmentCharges c) {
        return new ShipmentChargeResponse(
                c.charge().getShipmentId(),
                c.charge().getFreight(), c.charge().getFuelCharge(), c.charge().getHandlingCharge(),
                c.charge().getOdaCharge(), c.charge().getInsuranceCharge(), c.charge().getGstAmount(),
                c.charge().getDiscountAmount(), c.charge().getRoundOff(), c.charge().getNetAmount(),
                c.charge().getMatchedRouteId(), c.matchedRouteCode(),
                c.charge().getMatchedRateId(), c.matchedRateCode());
    }

    public ShipmentStatusHistoryResponse toResponse(ShipmentStatusHistory h) {
        return new ShipmentStatusHistoryResponse(
                h.getId(), h.getStatus(), h.getPreviousStatus(), h.getRemarks(),
                h.getBranchId(), h.getManifestId(), h.getVehicleId(),
                h.getChangedBy(), h.getChangedAt());
    }

    public com.courier.modules.shipment.api.dto.BulkMovementResponse toResponse(
            ShipmentService.BulkMovementResult r) {
        List<com.courier.modules.shipment.api.dto.MovementOutcomeResponse> results = r.results().stream()
                .map(o -> new com.courier.modules.shipment.api.dto.MovementOutcomeResponse(
                        o.reference(), o.success(), o.message()))
                .toList();
        return new com.courier.modules.shipment.api.dto.BulkMovementResponse(
                results, r.successCount(), r.failureCount());
    }

    public com.courier.modules.shipment.api.dto.TimelineStepResponse toResponse(ShipmentService.TimelineStep s) {
        return new com.courier.modules.shipment.api.dto.TimelineStepResponse(
                s.status(), s.label(), s.changedAt(), s.changedBy(), s.completed());
    }

    public ShipmentDocumentResponse toResponse(ShipmentDocument d) {
        return new ShipmentDocumentResponse(
                d.getId(), d.getDocumentType(), d.getDocumentName(), d.getDocumentUrl(),
                d.getRemarks(), d.getCreatedBy(), d.getCreatedAt());
    }
}
