package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepot;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * The MSRTC-style timetable board of a bus depot / station: per place, the times buses
 * depart for it and arrive from it. A board is edited and saved as a whole.
 */
public interface BusTimetableService {

    record RowInput(String placeName, boolean major, List<LocalTime> departures, List<LocalTime> arrivals) {
    }

    record RowView(UUID id, String placeName, boolean major, List<LocalTime> departures, List<LocalTime> arrivals) {
    }

    record BoardView(BusDepot depot, List<RowView> rows) {
    }

    /** A depot / station that has a board, with its row and bus counts. */
    record BoardSummary(BusDepot depot, int places, int departures, int arrivals) {
    }

    /** Every depot / station of the company that has a board. */
    List<BoardSummary> boards();

    /** The board of one depot / station; no rows when none is saved yet. */
    BoardView board(UUID depotId);

    /** Replaces the whole board with {@code rows} (order kept); an empty list clears it. */
    BoardView saveBoard(UUID depotId, List<RowInput> rows);
}
