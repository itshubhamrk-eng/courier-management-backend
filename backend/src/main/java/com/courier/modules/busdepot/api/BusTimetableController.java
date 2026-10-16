package com.courier.modules.busdepot.api;

import com.courier.modules.busdepot.application.BusTimetableService;
import com.courier.modules.busdepot.application.BusTimetableService.BoardSummary;
import com.courier.modules.busdepot.application.BusTimetableService.BoardView;
import com.courier.modules.busdepot.application.BusTimetableService.RowInput;
import com.courier.modules.busdepot.application.BusTimetableService.RowView;
import com.courier.modules.busdepot.domain.BusDepotType;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** MSRTC-style timetable board per bus depot / station (place → departure and arrival times). */
@RestController
@RequestMapping("/api/v1/bus-timetables")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Bus Timetable", description = "Depot / station timetable boards (वेळापत्रक)")
public class BusTimetableController {

    private final BusTimetableService service;

    public record DepotInfo(UUID id, String depotCode, String depotName, BusDepotType depotType, String city,
                            String divisionName) {
    }

    public record BoardSummaryResponse(DepotInfo depot, int places, int departures, int arrivals) {
    }

    /** Times go out as "HH:mm" strings (LocalTime.toString drops zero seconds). */
    public record RowResponse(UUID id, String placeName, boolean major, List<String> departures, List<String> arrivals) {
    }

    public record BoardResponse(DepotInfo depot, List<RowResponse> rows) {
    }

    public record RowRequest(@NotBlank @Size(max = 150) String placeName, boolean major,
                             List<@NotNull LocalTime> departures, List<@NotNull LocalTime> arrivals) {
    }

    public record SaveBoardRequest(@NotNull List<@Valid RowRequest> rows) {
    }

    @GetMapping
    @Operation(summary = "Depots / stations that have a timetable", description = "With place and bus counts.")
    public ApiResponse<List<BoardSummaryResponse>> boards() {
        return ApiResponse.success(service.boards().stream().map(this::toResponse).toList());
    }

    @GetMapping("/depots/{depotId}")
    @Operation(summary = "The timetable board of one depot / station", description = "Rows empty when none is saved yet.")
    public ApiResponse<BoardResponse> board(@PathVariable UUID depotId) {
        return ApiResponse.success(toResponse(service.board(depotId)));
    }

    @PutMapping("/depots/{depotId}")
    @Operation(summary = "Replace the board of a depot / station",
            description = "The whole board is saved in the order sent; an empty list clears it.")
    public ApiResponse<BoardResponse> save(@PathVariable UUID depotId, @Valid @RequestBody SaveBoardRequest r) {
        List<RowInput> rows = r.rows().stream()
                .map(x -> new RowInput(x.placeName(), x.major(), x.departures(), x.arrivals())).toList();
        return ApiResponse.success(toResponse(service.saveBoard(depotId, rows)), "Timetable saved");
    }

    private DepotInfo info(com.courier.modules.busdepot.domain.BusDepot d) {
        return new DepotInfo(d.getId(), d.getDepotCode(), d.getDepotName(), d.getDepotType(), d.getCity(), null);
    }

    private BoardSummaryResponse toResponse(BoardSummary s) {
        return new BoardSummaryResponse(info(s.depot()), s.places(), s.departures(), s.arrivals());
    }

    private BoardResponse toResponse(BoardView v) {
        return new BoardResponse(info(v.depot()), v.rows().stream().map(this::toResponse).toList());
    }

    private RowResponse toResponse(RowView r) {
        return new RowResponse(r.id(), r.placeName(), r.major(),
                r.departures().stream().map(LocalTime::toString).toList(), r.arrivals().stream().map(LocalTime::toString).toList());
    }
}
