package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/shipments}.
 *
 * <p>Not accepted: {@code companyId} (from the JWT), {@code trackingNumber} (always
 * generated), {@code status} (a new shipment starts {@code BOOKED}),
 * {@code actualWeight}/{@code volumetricWeight}/{@code chargeableWeight} as top-level
 * writes (computed from {@code items}; the bare {@code actualWeight}/dimension fields here
 * are the fallback used only when {@code items} is empty — see
 * {@code ShipmentServiceImpl}).
 */
@Schema(name = "CreateShipmentRequest", description = "New shipment booking")
public record CreateShipmentRequest(
        @NotNull UUID bookingBranchId,
        @NotNull UUID deliveryBranchId,
        @Schema(description = "Optional — enter a specific shipment number instead of "
                + "the auto-generated \"<BRANCH_CODE>-<serial>\" one. Must be unique "
                + "within the company; booking is refused with a 422 if it's already in use.")
        @Size(max = 30) String manualShipmentNumber,
        @NotBlank @Size(max = 10) String pickupPincode,
        @NotBlank @Size(max = 10) String deliveryPincode,
        @NotBlank @Size(max = 150) String senderName,
        @NotBlank @Size(max = 500) String senderAddress,
        @NotBlank @Size(max = 20) String senderContact,
        @NotBlank @Size(max = 150) String receiverName,
        @NotBlank @Size(max = 500) String receiverAddress,
        @NotBlank @Size(max = 20) String receiverContact,
        @NotNull UUID serviceTypeId,
        @NotNull UUID packageTypeId,
        @NotNull UUID paymentModeId,
        ShipmentType shipmentType,
        @Schema(description = "Defaults to today") LocalDate bookingDate,
        @DecimalMin(value = "0") BigDecimal declaredValue,
        @Min(1) Integer numberOfPackages,
        @Size(max = 500) String remarks,
        @DecimalMin(value = "0") BigDecimal otherCharges,
        @Schema(description = "Optional override of the Pricing Engine's own ODA charge — "
                + "typed at booking time when the operator needs to adjust it. Null uses the "
                + "engine's own computed value unchanged. GST is recomputed on the difference "
                + "at the booking branch's GST percentage.")
        @DecimalMin(value = "0") BigDecimal odaCharge,
        @Schema(description = "Only meaningful when this lane falls back to the Freight "
                + "Factor grid (no route/rate available) — raises the matched cell's own "
                + "factor. Must be greater than or equal to the matched factor.")
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal freightFactorOverride,
        @Valid @Schema(description = "The packed item grid; may be empty if actualWeight is supplied instead")
        List<ShipmentItemRequest> items,
        @Schema(description = "Required only when items is empty") BigDecimal actualWeight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        @Schema(description = "Route this shipment through one or more intermediate "
                + "branches/hubs, in order, instead of straight to the delivery branch. "
                + "When true, crossingBranchIds must carry at least one branch.")
        Boolean crossing,
        @Schema(description = "Required when crossing is true — the intermediate "
                + "branches/hubs, in the order the shipment passes through them")
        List<UUID> crossingBranchIds,
        @Schema(description = "The whole route's crossing charge — not per hop")
        @DecimalMin(value = "0") BigDecimal crossingCharge,
        @Schema(description = "Drives whether an E-Way Bill is mandatory before AWB "
                + "generation — see GET /company-settings for the company's own threshold "
                + "(default 50000.00). Null is never mandatory.")
        @DecimalMin(value = "0") BigDecimal invoiceValue,
        @Valid
        @Schema(description = "Required when invoiceValue exceeds the mandatory threshold "
                + "— booking is refused with a 422 otherwise. Optional and simply attached "
                + "when supplied below the threshold.")
        EwayBillBookingRequest ewayBill,
        @Schema(description = "The specific Area of deliveryPincode picked from its Area "
                + "dropdown, if any. Resolves District Level Freight's District/ODA off that "
                + "exact pincode-area link instead of the pincode's legacy single area.")
        UUID destinationAreaId,
        @Schema(description = "Optional override of District Level Freight's own matched-slab "
                + "rate/KG. Must not be lower than that rate — refused with a 422 otherwise. "
                + "A higher figure raises freight (and its GST, on the difference only) above "
                + "the system-calculated figure.")
        @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
        BigDecimal ratePerKgOverride
) {
}
