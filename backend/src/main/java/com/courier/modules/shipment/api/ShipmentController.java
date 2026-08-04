package com.courier.modules.shipment.api;

import com.courier.modules.shipment.api.dto.AddShipmentDocumentRequest;
import com.courier.modules.shipment.api.dto.CreateShipmentRequest;
import com.courier.modules.shipment.api.dto.ShipmentChargeResponse;
import com.courier.modules.shipment.api.dto.ShipmentDocumentResponse;
import com.courier.modules.shipment.api.dto.ShipmentItemResponse;
import com.courier.modules.shipment.api.dto.ShipmentResponse;
import com.courier.modules.shipment.api.dto.ShipmentSearchRequest;
import com.courier.modules.shipment.api.dto.ShipmentStatusHistoryResponse;
import com.courier.modules.shipment.api.dto.ShipmentSummaryResponse;
import com.courier.modules.shipment.api.dto.UpdateShipmentRequest;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Shipment Booking: the core transaction of the platform. Enforced on
 * {@link ShipmentService}: {@code COMPANY_ADMIN}, {@code BRANCH_MANAGER} and
 * {@code OPERATOR} book, update, cancel and upload documents; every other authenticated
 * user of the company reads. The company is always the caller's, from the JWT.
 *
 * <p>The brief's {@code GET /shipments/{trackingNumber}} is exposed at
 * {@code GET /shipments/track/{trackingNumber}} instead — a bare second
 * {@code /shipments/{x}} route is ambiguous with {@code GET /shipments/{id}} at the Spring
 * MVC routing layer, the same "honest deviation from a literal path" every other module in
 * this project has made when the spec's literal shape did not fit (see, e.g., Branch
 * Wallet's singular path or Master Data's {@code new} before {@code :id}).
 */
@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shipment Booking", description = "Book, price, track and cancel shipments")
public class ShipmentController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("shipmentNumber", "shipmentNumber"),
            Map.entry("trackingNumber", "trackingNumber"),
            Map.entry("bookingDate", "bookingDate"),
            Map.entry("status", "status"),
            Map.entry("chargeableWeight", "chargeableWeight"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final ShipmentService shipmentService;
    private final ShipmentMapper mapper;

    @PostMapping
    @Operation(summary = "Book a shipment",
            description = "`COMPANY_ADMIN`, `BRANCH_MANAGER` or `OPERATOR`. Validates the "
                    + "sender/receiver/addresses, prices the booking through the Pricing "
                    + "Engine (route, serviceability, rate and weight-slab validation all "
                    + "happen inside that one call), generates the AWB and shipment number, "
                    + "and — for a payment mode that collects at booking — checks the "
                    + "branch wallet can afford it before committing, then debits it once "
                    + "the booking has committed.")
    public ResponseEntity<ApiResponse<ShipmentResponse>> create(
            @Valid @RequestBody CreateShipmentRequest request) {
        Shipment created = shipmentService.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/shipments/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(
                        mapper.toResponse(created, shipmentService.getItems(created.getId())),
                        "Shipment booked"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a shipment",
            description = "Full replacement, only while the shipment is still `BOOKED`. "
                    + "`version` required; a stale value returns 409. Re-prices the "
                    + "booking; does not touch a wallet already debited at booking time.")
    public ApiResponse<ShipmentResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateShipmentRequest request) {
        Shipment updated = shipmentService.update(id, mapper.toCommand(request));
        return ApiResponse.success(
                mapper.toResponse(updated, shipmentService.getItems(id)), "Shipment updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a shipment", description = "Includes its item grid.")
    public ApiResponse<ShipmentResponse> get(@PathVariable UUID id) {
        Shipment shipment = shipmentService.getById(id);
        return ApiResponse.success(mapper.toResponse(shipment, shipmentService.getItems(id)));
    }

    @GetMapping("/track/{trackingNumber}")
    @Operation(summary = "Fetch a shipment by tracking number (AWB)",
            description = "See the class-level note on why this is not a second "
                    + "`/shipments/{x}` route.")
    public ApiResponse<ShipmentResponse> getByTrackingNumber(@PathVariable String trackingNumber) {
        Shipment shipment = shipmentService.getByTrackingNumber(trackingNumber);
        return ApiResponse.success(mapper.toResponse(shipment, shipmentService.getItems(shipment.getId())));
    }

    @GetMapping
    @Operation(summary = "List shipments",
            description = """
                    Paged, sorted, filtered, searchable. Sort: `shipmentNumber`,
                    `trackingNumber`, `bookingDate`, `status`, `chargeableWeight`,
                    `createdDate`. `size` capped at 100.
                    """)
    public ApiResponse<PageResponse<ShipmentSummaryResponse>> list(
            @Valid @ParameterObject ShipmentSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<Shipment> page = shipmentService.search(mapper.toCriteria(search), sanitise(pageable));
        Map<UUID, BigDecimal> netAmounts = shipmentService.netAmountsFor(
                page.getContent().stream().map(Shipment::getId).toList());
        return ApiResponse.success(PageResponse.from(page, s -> mapper.toSummary(s, netAmounts.get(s.getId()))));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a shipment",
            description = "Refused once the shipment has left the branch (DISPATCHED "
                    + "onward). Does not reverse a PREPAID debit already posted at booking.")
    public ApiResponse<ShipmentResponse> cancel(@PathVariable UUID id,
                                                @RequestParam(required = false) String remarks) {
        Shipment cancelled = shipmentService.cancel(id, remarks);
        return ApiResponse.success(
                mapper.toResponse(cancelled, shipmentService.getItems(id)), "Shipment cancelled");
    }

    @PostMapping("/{id}/documents")
    @Operation(summary = "Attach a document to a shipment",
            description = "`documentType` one of `INVOICE`, `EWAY_BILL`, `PACKING_LIST`, "
                    + "`LR_COPY`, `POD`. There is no file-storage backend yet — "
                    + "`documentUrl` is supplied by the caller.")
    public ResponseEntity<ApiResponse<ShipmentDocumentResponse>> addDocument(
            @PathVariable UUID id, @Valid @RequestBody AddShipmentDocumentRequest request) {
        var created = shipmentService.addDocument(id, mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/shipments/{id}/documents/{docId}")
                        .buildAndExpand(id, created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Document attached"));
    }

    @GetMapping("/{id}/charges")
    @Operation(summary = "Fetch a shipment's charge breakup",
            description = "The Pricing Engine's own charge lines, persisted at booking time.")
    public ApiResponse<ShipmentChargeResponse> getCharges(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(shipmentService.getCharges(id)));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Fetch a shipment's status timeline", description = "Oldest first, append-only.")
    public ApiResponse<List<ShipmentStatusHistoryResponse>> getHistory(@PathVariable UUID id) {
        List<ShipmentStatusHistoryResponse> history = shipmentService.getHistory(id).stream()
                .map(mapper::toResponse).toList();
        return ApiResponse.success(history);
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "List a shipment's documents", description = "Newest first.")
    public ApiResponse<List<ShipmentDocumentResponse>> getDocuments(@PathVariable UUID id) {
        List<ShipmentDocumentResponse> documents = shipmentService.getDocuments(id).stream()
                .map(mapper::toResponse).toList();
        return ApiResponse.success(documents);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Fetch a shipment's movement timeline",
            description = "Booked -> Manifest Created -> Out Scan -> Dispatched -> Received -> "
                    + "Out For Delivery -> Delivered, each step flagged completed/pending with "
                    + "when and who — built from the same append-only log as `/history`, but one "
                    + "row per named step rather than the raw sequence of transitions.")
    public ApiResponse<List<com.courier.modules.shipment.api.dto.TimelineStepResponse>> getTimeline(
            @PathVariable UUID id) {
        List<com.courier.modules.shipment.api.dto.TimelineStepResponse> steps =
                shipmentService.timeline(id).stream().map(mapper::toResponse).toList();
        return ApiResponse.success(steps);
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "Fetch a shipment's item grid")
    public ApiResponse<List<ShipmentItemResponse>> getItems(@PathVariable UUID id) {
        List<ShipmentItemResponse> items = shipmentService.getItems(id).stream()
                .map(mapper::toResponse).toList();
        return ApiResponse.success(items);
    }

    // -------------------------------------------------------------------- helpers

    private Pageable sanitise(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORTABLE.get(order.getProperty());
                    if (property == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(),
                                                String.join(", ", new TreeSet<>(SORTABLE.keySet()))));
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .toList();
        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Order.desc("createdAt"))
                : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
