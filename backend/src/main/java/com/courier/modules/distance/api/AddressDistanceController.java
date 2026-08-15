package com.courier.modules.distance.api;

import com.courier.modules.distance.api.dto.AddressDistanceResponse;
import com.courier.modules.distance.application.AddressDistanceService;
import com.courier.modules.distance.domain.AddressType;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Address-to-address distance: resolved on first request via the routing provider,
 * cached from then on. {@code BRANCH} pairs read {@code Branch}'s own coordinates;
 * {@code CUSTOMER} pairs read {@code CustomerAddress} — {@code fromAddressId}/
 * {@code toAddressId} there are {@code customer_addresses.id}, not {@code customers.id}.
 */
@RestController
@RequestMapping("/api/v1/distances")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Address Distance", description = "Resolved road distance between two addresses")
public class AddressDistanceController {

    private final AddressDistanceService service;
    private final AddressDistanceMapper mapper;

    @GetMapping("/branches")
    @Operation(summary = "Resolve the distance between two branches", description =
            "Returns the cached row if this pair was resolved before; otherwise looks it up "
                    + "via the routing provider and stores it. Both branches must already "
                    + "carry a latitude/longitude.")
    public ApiResponse<AddressDistanceResponse> branchDistance(
            @RequestParam UUID fromBranchId, @RequestParam UUID toBranchId) {
        var result = service.resolveBranchDistance(fromBranchId, toBranchId);
        return ApiResponse.success(mapper.toResponse(result), "Distance resolved");
    }

    @GetMapping("/customer-addresses")
    @Operation(summary = "Resolve the distance between two customer addresses", description =
            "Same cache-or-resolve behaviour as /branches. fromAddressId/toAddressId are "
                    + "customer address ids, not customer ids.")
    public ApiResponse<AddressDistanceResponse> customerAddressDistance(
            @RequestParam UUID fromAddressId, @RequestParam UUID toAddressId) {
        var result = service.resolveCustomerAddressDistance(fromAddressId, toAddressId);
        return ApiResponse.success(mapper.toResponse(result), "Distance resolved");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a resolved distance by id")
    public ApiResponse<AddressDistanceResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.get(id)), "Distance retrieved");
    }

    @GetMapping
    @Operation(summary = "List resolved distances for this company", description =
            "All filters optional. With none given, returns every resolved pair.")
    public ApiResponse<List<AddressDistanceResponse>> search(
            @RequestParam(required = false) AddressType addressType,
            @RequestParam(required = false) UUID fromId,
            @RequestParam(required = false) UUID toId) {
        var results = service.search(addressType, fromId, toId).stream()
                .map(mapper::toResponse).toList();
        return ApiResponse.success(results, "Distances retrieved");
    }

    @PostMapping("/{id}/refresh")
    @Operation(summary = "Recompute a resolved distance in place", description =
            "For when the underlying address's location changed since it was first resolved.")
    public ApiResponse<AddressDistanceResponse> refresh(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.refresh(id)), "Distance refreshed");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a resolved distance", description =
            "Soft delete. The pair can be resolved again afterwards, computing a fresh row.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Distance deleted");
    }
}
