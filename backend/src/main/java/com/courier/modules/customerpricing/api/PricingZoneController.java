package com.courier.modules.customerpricing.api;

import com.courier.modules.customerpricing.api.PricingZoneDtos.CreateRequest;
import com.courier.modules.customerpricing.api.PricingZoneDtos.LocationRequest;
import com.courier.modules.customerpricing.api.PricingZoneDtos.LocationResponse;
import com.courier.modules.customerpricing.api.PricingZoneDtos.UpdateRequest;
import com.courier.modules.customerpricing.api.PricingZoneDtos.ZoneResponse;
import com.courier.modules.customerpricing.application.PricingZoneService;
import com.courier.modules.customerpricing.application.PricingZoneService.LocationInput;
import com.courier.modules.customerpricing.application.PricingZoneView;
import com.courier.modules.customerpricing.domain.ZoneStatus;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** Business customer pricing zones. Writes are {@code COMPANY_ADMIN} (enforced on the service); reads are any authenticated user. */
@RestController
@RequestMapping("/api/v1/pricing-zones")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Pricing Zones", description = "Customer pricing zones and their districts/cities")
public class PricingZoneController {

    private final PricingZoneService service;

    @PostMapping
    @Operation(summary = "Create a pricing zone", description = "For one customer; the name must be unique among "
            + "that customer's zones and no location may already sit in another of its zones.")
    public ResponseEntity<ApiResponse<ZoneResponse>> create(@Valid @RequestBody CreateRequest request) {
        List<LocationInput> locations = request.locations() == null ? List.of()
                : request.locations().stream().map(l -> new LocationInput(l.districtId(), l.cityId())).toList();
        PricingZoneView created = service.create(request.customerId(), request.zoneName(),
                request.description(), locations);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toResponse(created), "Pricing zone created"));
    }

    @GetMapping
    @Operation(summary = "List pricing zones", description = "Optionally for one customer / status / name search.")
    public ApiResponse<List<ZoneResponse>> list(@RequestParam(required = false) UUID customerId,
                                                @RequestParam(required = false) ZoneStatus status,
                                                @RequestParam(required = false) String search) {
        return ApiResponse.success(service.search(customerId, status, search).stream().map(this::toResponse).toList());
    }

    @GetMapping("/resolve")
    @Operation(summary = "Resolve the zone for a destination", description = "The customer's ACTIVE zone covering "
            + "the district/city (city match beats whole-district). `data` is null when none does.")
    public ApiResponse<ZoneResponse> resolve(@RequestParam UUID customerId, @RequestParam UUID districtId,
                                             @RequestParam(required = false) UUID cityId) {
        return ApiResponse.success(service.resolve(customerId, districtId, cityId).map(this::toResponse).orElse(null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a pricing zone")
    public ApiResponse<ZoneResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename / re-describe a pricing zone", description = "`version` required; stale returns 409.")
    public ApiResponse<ZoneResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.success(toResponse(
                service.update(id, request.zoneName(), request.description(), request.version())), "Pricing zone updated");
    }

    @PostMapping("/{id}/locations")
    @Operation(summary = "Add a district or city to a zone")
    public ApiResponse<ZoneResponse> addLocation(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        return ApiResponse.success(toResponse(
                service.addLocation(id, new LocationInput(request.districtId(), request.cityId()))), "Location added");
    }

    @DeleteMapping("/{id}/locations/{locationId}")
    @Operation(summary = "Remove a location from a zone")
    public ApiResponse<ZoneResponse> removeLocation(@PathVariable UUID id, @PathVariable UUID locationId) {
        return ApiResponse.success(toResponse(service.removeLocation(id, locationId)), "Location removed");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a pricing zone")
    public ApiResponse<ZoneResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.activate(id)), "Pricing zone activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a pricing zone", description = "A deactivated zone is skipped when resolving a destination.")
    public ApiResponse<ZoneResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.deactivate(id)), "Pricing zone deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a pricing zone", description = "Soft-deletes the zone with its locations and rate card.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Pricing zone deleted");
    }

    private ZoneResponse toResponse(PricingZoneView v) {
        var z = v.zone();
        return new ZoneResponse(z.getId(), z.getCustomerId(), v.customerName(), z.getZoneName(), z.getDescription(),
                z.getStatus(), v.hasRate(),
                v.locations().stream().map(l -> new LocationResponse(l.id(), l.districtId(), l.districtName(),
                        l.cityId(), l.cityName())).toList(),
                z.getCreatedAt(), z.getUpdatedAt(), z.getVersion());
    }
}
