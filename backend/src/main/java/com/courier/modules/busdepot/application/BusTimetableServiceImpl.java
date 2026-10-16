package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepot;
import com.courier.modules.busdepot.domain.BusDepotRepository;
import com.courier.modules.busdepot.domain.BusTimetableEntry;
import com.courier.modules.busdepot.domain.BusTimetableEntryRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusTimetableServiceImpl implements BusTimetableService {

    private static final String ENTITY = "BusTimetable";
    private static final String ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final BusTimetableEntryRepository entries;
    private final BusDepotRepository depots;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<BoardSummary> boards() {
        UUID companyId = requireCompany();
        Map<UUID, List<BusTimetableEntry>> byDepot = entries.findAllInCompany(companyId).stream()
                .collect(Collectors.groupingBy(BusTimetableEntry::getDepotId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, BusDepot> depotById = depots.findAllById(byDepot.keySet()).stream()
                .filter(d -> companyId.equals(d.getCompanyId()))
                .collect(Collectors.toMap(BusDepot::getId, Function.identity()));
        return byDepot.entrySet().stream()
                .filter(e -> depotById.containsKey(e.getKey()))
                .map(e -> new BoardSummary(depotById.get(e.getKey()), e.getValue().size(),
                        e.getValue().stream().mapToInt(r -> r.getDepartures().size()).sum(),
                        e.getValue().stream().mapToInt(r -> r.getArrivals().size()).sum()))
                .sorted((a, b) -> a.depot().getDepotName().compareToIgnoreCase(b.depot().getDepotName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public BoardView board(UUID depotId) {
        UUID companyId = requireCompany();
        return view(loadDepot(depotId, companyId), entries.findBoard(companyId, depotId));
    }

    @Override
    @Transactional
    @PreAuthorize(ADMIN)
    public BoardView saveBoard(UUID depotId, List<RowInput> rows) {
        UUID companyId = requireCompany();
        BusDepot depot = loadDepot(depotId, companyId);
        validate(rows);
        UUID user = SecurityUtils.getCurrentUserId().orElse(null);
        for (BusTimetableEntry old : entries.findBoard(companyId, depotId)) {
            old.softDelete(user);
            entries.save(old);
        }
        List<BusTimetableEntry> saved = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            RowInput r = rows.get(i);
            saved.add(entries.save(BusTimetableEntry.builder()
                    .depotId(depotId)
                    .placeName(r.placeName().trim())
                    .major(r.major())
                    .sortOrder(i)
                    .departures(new ArrayList<>(r.departures() == null ? List.of() : r.departures()))
                    .arrivals(new ArrayList<>(r.arrivals() == null ? List.of() : r.arrivals()))
                    .build()));
        }
        auditService.record(rows.isEmpty() ? AuditAction.BUS_TIMETABLE_CLEARED : AuditAction.BUS_TIMETABLE_SAVED,
                ENTITY, depotId, Map.of("places", rows.size()));
        return view(depot, saved);
    }

    // ------------------------------------------------------------------ helpers

    private void validate(List<RowInput> rows) {
        if (rows == null) {
            throw new BusinessRuleException("Send the timetable rows (an empty list clears the board).");
        }
        Set<String> seen = new HashSet<>();
        for (RowInput r : rows) {
            String name = r.placeName() == null ? "" : r.placeName().trim();
            if (name.isEmpty()) {
                throw new BusinessRuleException("Every timetable row needs a place name.");
            }
            if (name.length() > 150) {
                throw new BusinessRuleException("Place name is too long: " + name);
            }
            if (!seen.add(name.toLowerCase())) {
                throw new BusinessRuleException("\"" + name + "\" appears twice on this timetable.");
            }
        }
    }

    private BoardView view(BusDepot depot, List<BusTimetableEntry> list) {
        return new BoardView(depot, list.stream().map(e -> new RowView(e.getId(), e.getPlaceName(), e.isMajor(),
                List.copyOf(e.getDepartures()), List.copyOf(e.getArrivals()))).toList());
    }

    private BusDepot loadDepot(UUID depotId, UUID companyId) {
        return depots.findByIdWithinCompany(depotId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("BusDepot", depotId));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Timetables belong to a company."));
    }
}
