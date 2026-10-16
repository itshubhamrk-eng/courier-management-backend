package com.courier.modules.busdepot.api;

import com.courier.modules.busdepot.api.BusDepotDtos.DepotResponse;
import com.courier.modules.busdepot.api.BusDepotDtos.SaveRequest;
import com.courier.modules.busdepot.api.BusDepotDtos.UpdateRequest;
import com.courier.modules.busdepot.application.BusDepotService;
import com.courier.modules.busdepot.application.BusDepotService.DepotInput;
import com.courier.modules.busdepot.application.BusDepotView;
import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDepotType;
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

/** MSRTC bus depot / station master. Writes are {@code COMPANY_ADMIN} (enforced on the service); reads any authenticated user. */
@RestController
@RequestMapping("/api/v1/bus-depots")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bus Depots", description = "MSRTC bus depots / stations and the branch or vendor assigned to each")
public class BusDepotController {

    private final BusDepotService service;

    @PostMapping
    @Operation(summary = "Create a bus depot / station", description = "Code and name must be unique. `branchId` "
            + "(optional) assigns an active branch or vendor (Pickup-Delivery Partner) to the depot.")
    public ResponseEntity<ApiResponse<DepotResponse>> create(@Valid @RequestBody SaveRequest r) {
        BusDepotView created = service.create(new DepotInput(r.depotCode(), r.depotName(), r.depotType(), r.branchId(),
                r.divisionId(), r.parentDepotId(), r.address(), r.city(), r.district(), r.state(), r.pincode(), r.contactPerson(), r.contactMobile()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toResponse(created), "Bus depot created"));
    }

    @GetMapping
    @Operation(summary = "List bus depots", description = "Optionally by status / type / assigned branch / search (code, name, city).")
    public ApiResponse<List<DepotResponse>> list(@RequestParam(required = false) BusDepotStatus status,
                                                 @RequestParam(required = false) BusDepotType type,
                                                 @RequestParam(required = false) UUID branchId,
                                                 @RequestParam(required = false) UUID divisionId,
                                                 @RequestParam(required = false) String search) {
        return ApiResponse.success(service.search(status, type, branchId, divisionId, search).stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a bus depot")
    public ApiResponse<DepotResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a bus depot", description = "`version` required; stale returns 409. A null `branchId` unassigns.")
    public ApiResponse<DepotResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        BusDepotView updated = service.update(id, new DepotInput(r.depotCode(), r.depotName(), r.depotType(), r.branchId(),
                r.divisionId(), r.parentDepotId(), r.address(), r.city(), r.district(), r.state(), r.pincode(), r.contactPerson(), r.contactMobile()), r.version());
        return ApiResponse.success(toResponse(updated), "Bus depot updated");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a bus depot")
    public ApiResponse<DepotResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.activate(id)), "Bus depot activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a bus depot")
    public ApiResponse<DepotResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.deactivate(id)), "Bus depot deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a bus depot", description = "Soft delete; the code and name are released.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Bus depot deleted");
    }

    private DepotResponse toResponse(BusDepotView v) {
        var d = v.depot();
        return new DepotResponse(d.getId(), d.getDepotCode(), d.getDepotName(), d.getDepotType(), d.getStatus(),
                d.getBranchId(), v.branchCode(), v.branchName(), v.branchType(),
                d.getDivisionId(), v.divisionName(), d.getParentDepotId(), v.parentDepotName(),
                d.getAddress(), d.getCity(), d.getDistrict(), d.getState(), d.getPincode(),
                d.getContactPerson(), d.getContactMobile(), d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
    }
}
