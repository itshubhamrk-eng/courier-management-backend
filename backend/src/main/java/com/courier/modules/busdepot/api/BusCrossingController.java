package com.courier.modules.busdepot.api;

import com.courier.modules.busdepot.application.BusCrossingService;
import com.courier.modules.busdepot.application.BusCrossingService.RouteView;
import com.courier.modules.busdepot.application.BusCrossingService.ShipmentCrossingView;
import com.courier.modules.busdepot.domain.BusDepot;
import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDepotType;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** MSRTC crossing routes between depots / stations, and the crossing each shipment used. */
@RestController
@RequestMapping("/api/v1/bus-crossings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bus Crossings", description = "Reusable depot/station crossing routes for MSRTC bookings")
public class BusCrossingController {

    private final BusCrossingService service;

    public record DepotRef(UUID id, String depotCode, String depotName, BusDepotType depotType, String city) {
        static DepotRef of(BusDepot d) {
            return d == null ? null : new DepotRef(d.getId(), d.getDepotCode(), d.getDepotName(), d.getDepotType(), d.getCity());
        }
    }

    public record RouteResponse(UUID id, BusDepotStatus status, DepotRef from, DepotRef to, List<DepotRef> stops, Long version) {
    }

    public record ShipmentCrossingResponse(UUID id, UUID shipmentId, UUID routeId, DepotRef from, DepotRef to,
                                           List<DepotRef> stops) {
    }

    public record RouteRequest(@NotNull UUID fromDepotId, @NotNull UUID toDepotId, @NotEmpty List<@NotNull UUID> stops) {
    }

    public record ShipmentCrossingRequest(@NotNull UUID fromDepotId, @NotNull UUID toDepotId,
                                          @NotEmpty List<@NotNull UUID> stops, Boolean saveAsRoute) {
    }

    @GetMapping
    @Operation(summary = "List crossing routes", description = "Optionally by status, or any route that starts or ends at a depot / station.")
    public ApiResponse<List<RouteResponse>> list(@RequestParam(required = false) BusDepotStatus status,
                                                 @RequestParam(required = false) UUID depotId) {
        return ApiResponse.success(service.search(status, depotId).stream().map(this::toResponse).toList());
    }

    @GetMapping("/lookup")
    @Operation(summary = "Saved crossings for a trip (suggestions)",
            description = "Pass fromDepotId+toDepotId, or fromBranchId+toBranchId (any depot assigned to each branch counts). "
                    + "Every active route for the trip, newest first; empty when none is saved yet.")
    public ApiResponse<List<RouteResponse>> lookup(@RequestParam(required = false) UUID fromDepotId,
                                             @RequestParam(required = false) UUID toDepotId,
                                             @RequestParam(required = false) UUID fromBranchId,
                                             @RequestParam(required = false) UUID toBranchId) {
        return ApiResponse.success(service.lookup(fromDepotId, toDepotId, fromBranchId, toBranchId)
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<RouteResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Save a crossing route",
            description = "Adds a crossing for this start/end pair. A pair can hold several; an identical one is not stored twice.")
    public ApiResponse<RouteResponse> save(@Valid @RequestBody RouteRequest r) {
        return ApiResponse.success(toResponse(service.save(r.fromDepotId(), r.toDepotId(), r.stops())), "Crossing route saved");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a route's crossing points", description = "Start and end are fixed; they must match the saved route.")
    public ApiResponse<RouteResponse> update(@PathVariable UUID id, @Valid @RequestBody RouteRequest r) {
        RouteView current = service.getById(id);
        if (!current.from().getId().equals(r.fromDepotId()) || !current.to().getId().equals(r.toDepotId())) {
            throw new com.courier.shared.exception.BusinessRuleException(
                    "The start and end of a saved route cannot change — add a new crossing instead.");
        }
        return ApiResponse.success(toResponse(service.update(id, r.stops())), "Crossing route updated");
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<RouteResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.activate(id)), "Crossing route activated");
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<RouteResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.deactivate(id)), "Crossing route deactivated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Crossing route deleted");
    }

    @GetMapping("/shipments/{shipmentId}")
    @Operation(summary = "The crossing a shipment travels", description = "Data is null when the shipment has none.")
    public ApiResponse<ShipmentCrossingResponse> forShipment(@PathVariable UUID shipmentId) {
        return ApiResponse.success(service.getForShipment(shipmentId).map(this::toResponse).orElse(null));
    }

    @PutMapping("/shipments/{shipmentId}")
    @Operation(summary = "Set a shipment's crossing (at or after booking)",
            description = "`saveAsRoute` (default true) keeps it as the route for this start/end pair, so the next booking picks it up.")
    public ApiResponse<ShipmentCrossingResponse> setForShipment(@PathVariable UUID shipmentId,
                                                                @Valid @RequestBody ShipmentCrossingRequest r) {
        return ApiResponse.success(toResponse(service.setForShipment(shipmentId, r.fromDepotId(), r.toDepotId(),
                r.stops(), !Boolean.FALSE.equals(r.saveAsRoute()))), "Shipment crossing saved");
    }

    @DeleteMapping("/shipments/{shipmentId}")
    public ApiResponse<Void> clearForShipment(@PathVariable UUID shipmentId) {
        service.clearForShipment(shipmentId);
        return ApiResponse.success("Shipment crossing removed");
    }

    private RouteResponse toResponse(RouteView v) {
        return new RouteResponse(v.id(), v.status(), DepotRef.of(v.from()), DepotRef.of(v.to()),
                v.stops().stream().map(DepotRef::of).toList(), v.version());
    }

    private ShipmentCrossingResponse toResponse(ShipmentCrossingView v) {
        return new ShipmentCrossingResponse(v.id(), v.shipmentId(), v.routeId(), DepotRef.of(v.from()), DepotRef.of(v.to()),
                v.stops().stream().map(DepotRef::of).toList());
    }
}
