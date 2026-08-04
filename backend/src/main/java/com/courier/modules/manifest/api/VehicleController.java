package com.courier.modules.manifest.api;

import com.courier.modules.manifest.api.dto.CreateVehicleRequest;
import com.courier.modules.manifest.api.dto.VehicleResponse;
import com.courier.modules.manifest.application.VehicleService;
import com.courier.modules.manifest.domain.Vehicle;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** The fleet Dispatch's "Assign Vehicle" picker reads from — see {@code Vehicle}'s own note on scope. */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Vehicles", description = "Fleet used to dispatch a manifest")
public class VehicleController {

    private final VehicleService vehicleService;
    private final ManifestMapper mapper;

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> create(
            @Valid @RequestBody CreateVehicleRequest request) {
        Vehicle created = vehicleService.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/vehicles/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Vehicle created"));
    }

    @GetMapping("/{id}")
    public ApiResponse<VehicleResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(vehicleService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List vehicles", description = "`activeOnly=true` (default) for a picker; false for fleet management.")
    public ApiResponse<List<VehicleResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<Vehicle> vehicles = activeOnly ? vehicleService.listActive() : vehicleService.listAll();
        return ApiResponse.success(vehicles.stream().map(mapper::toResponse).toList());
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<VehicleResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(vehicleService.activate(id)), "Vehicle activated");
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<VehicleResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(vehicleService.deactivate(id)), "Vehicle deactivated");
    }
}
