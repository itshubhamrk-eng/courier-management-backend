package com.courier.modules.busdepot.api;

import com.courier.modules.busdepot.application.BusDivisionService;
import com.courier.modules.busdepot.application.BusDivisionService.DivisionInput;
import com.courier.modules.busdepot.application.BusDivisionService.DivisionView;
import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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

/** MSRTC divisions. Writes {@code COMPANY_ADMIN} (enforced on the service); reads any authenticated user. */
@RestController
@RequestMapping("/api/v1/bus-divisions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bus Divisions", description = "MSRTC divisions grouping depots and bus stations")
public class BusDivisionController {

    public record SaveRequest(@NotBlank @Size(max = 30) String divisionCode, @NotBlank @Size(max = 100) String divisionName) {
    }

    public record UpdateRequest(@NotBlank @Size(max = 30) String divisionCode, @NotBlank @Size(max = 100) String divisionName,
                                @NotNull @PositiveOrZero Long version) {
    }

    public record DivisionResponse(UUID id, String divisionCode, String divisionName, BusDepotStatus status,
                                   long depotCount, long stationCount, Long version) {
    }

    private final BusDivisionService service;

    @PostMapping
    @Operation(summary = "Create a division")
    public ResponseEntity<ApiResponse<DivisionResponse>> create(@Valid @RequestBody SaveRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                toResponse(service.create(new DivisionInput(r.divisionCode(), r.divisionName()))), "Division created"));
    }

    @GetMapping
    @Operation(summary = "List divisions with depot / station counts")
    public ApiResponse<List<DivisionResponse>> list(@RequestParam(required = false) BusDepotStatus status,
                                                    @RequestParam(required = false) String search) {
        return ApiResponse.success(service.search(status, search).stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a division")
    public ApiResponse<DivisionResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a division", description = "`version` required; stale returns 409.")
    public ApiResponse<DivisionResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        return ApiResponse.success(toResponse(
                service.update(id, new DivisionInput(r.divisionCode(), r.divisionName()), r.version())), "Division updated");
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<DivisionResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.activate(id)), "Division activated");
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<DivisionResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.deactivate(id)), "Division deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a division", description = "Refused while depots/stations still belong to it.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Division deleted");
    }

    private DivisionResponse toResponse(DivisionView v) {
        var d = v.division();
        return new DivisionResponse(d.getId(), d.getDivisionCode(), d.getDivisionName(), d.getStatus(),
                v.depotCount(), v.stationCount(), d.getVersion());
    }
}
