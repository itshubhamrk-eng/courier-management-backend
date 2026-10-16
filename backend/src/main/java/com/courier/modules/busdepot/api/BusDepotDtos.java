package com.courier.modules.busdepot.api;

import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDepotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Wire contract of {@code /api/v1/bus-depots}. */
public final class BusDepotDtos {

    private BusDepotDtos() {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 30) String depotCode,
            @NotBlank @Size(max = 150) String depotName,
            @NotNull BusDepotType depotType,
            UUID branchId,
            UUID divisionId,
            UUID parentDepotId,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 100) String state,
            @Size(max = 10) String pincode,
            @Size(max = 150) String contactPerson,
            @Size(max = 20) String contactMobile) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 30) String depotCode,
            @NotBlank @Size(max = 150) String depotName,
            @NotNull BusDepotType depotType,
            UUID branchId,
            UUID divisionId,
            UUID parentDepotId,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 100) String state,
            @Size(max = 10) String pincode,
            @Size(max = 150) String contactPerson,
            @Size(max = 20) String contactMobile,
            @NotNull @PositiveOrZero Long version) {
    }

    public record DepotResponse(
            UUID id, String depotCode, String depotName, BusDepotType depotType, BusDepotStatus status,
            UUID branchId, String branchCode, String branchName, String branchType,
            UUID divisionId, String divisionName, UUID parentDepotId, String parentDepotName,
            String address, String city, String district, String state, String pincode,
            String contactPerson, String contactMobile,
            Instant createdDate, Instant updatedDate, Long version) {
    }
}
