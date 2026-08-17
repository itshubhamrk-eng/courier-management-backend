package com.courier.modules.support.application;

import com.courier.modules.support.application.command.CreateTicketCommand;
import com.courier.modules.support.domain.ShipmentSlaBreach;
import com.courier.modules.support.domain.ShipmentSlaBreachRepository;
import com.courier.modules.support.domain.ShipmentSlaConfig;
import com.courier.modules.support.domain.ShipmentSlaPort;
import com.courier.modules.support.domain.ShipmentSlaStage;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketCategoryRepository;
import com.courier.modules.support.domain.TicketDirectoryPort;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The shipment-lifecycle SLA sweep: for every company, finds shipments that have sat in
 * their current status past that company's configured threshold and raises one ticket
 * per (shipment, stage), ever — {@link ShipmentSlaBreachRepository} is the idempotency
 * record, so a re-run never double-raises.
 *
 * <p>Runs with no authenticated caller — {@code ShipmentSlaSweepJob} is a {@code
 * @Scheduled} entry point, not a controller — so every company is entered explicitly via
 * {@link CompanyContext#runAs}, and ticket creation goes through {@code
 * TicketService#raiseSystemTicket}, the one path that does not require a current user.
 *
 * <p>Known gap: shipments mid-crossing ({@code READY_FOR_MANIFEST}) are not checked —
 * that status means "awaiting the next leg's own loading sheet", which does not map
 * cleanly onto one of the five stages below without guessing which leg's clock should be
 * running. Flagged rather than approximated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentSlaSweepService {

    private static final String SLA_BREACH_CATEGORY = "SLA Breach";

    private final TicketDirectoryPort directory;
    private final ShipmentSlaPort shipmentSlaPort;
    private final ShipmentSlaBreachRepository breachRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketService ticketService;

    public void sweepAllCompanies() {
        Instant now = Instant.now();
        List<UUID> companyIds = directory.listActiveCompanyIds();
        log.info("SLA sweep starting for {} companies", companyIds.size());
        for (UUID companyId : companyIds) {
            try {
                sweepCompany(companyId, now);
            } catch (Exception e) {
                log.error("SLA sweep failed for company {}", companyId, e);
            }
        }
    }

    private void sweepCompany(UUID companyId, Instant now) {
        ShipmentSlaConfig config = directory.shipmentSlaSettings(companyId);
        if (config == null || !config.enabled()) {
            return;
        }
        CompanyContext.runAs(companyId, () -> {
            List<ShipmentSlaPort.Candidate> candidates =
                    shipmentSlaPort.findBreachCandidates(companyId, config.thresholds(), now);
            for (ShipmentSlaPort.Candidate candidate : candidates) {
                if (candidate.stage() == null) {
                    continue;
                }
                if (breachRepository.existsByCompanyIdAndShipmentIdAndStage(
                        companyId, candidate.shipmentId(), candidate.stage())) {
                    continue;
                }
                raiseTicketFor(companyId, candidate);
            }
        });
    }

    /**
     * Not one transaction: {@code raiseSystemTicket} and the breach-record save each run
     * in their own (a same-class self-invocation would silently drop a wrapping
     * {@code @Transactional} here anyway). A crash between the two leaves a ticket raised
     * with no breach row — the next sweep would raise a duplicate. Accepted risk, same
     * shape as every other cross-aggregate write in this codebase (e.g. wallet credit
     * after dispatch) — narrow window, not silent data loss, worth reconciling manually
     * if it ever fires.
     */
    public void raiseTicketFor(UUID companyId, ShipmentSlaPort.Candidate candidate) {
        TicketCategory category = categoryRepository.findByNameIgnoreCase(SLA_BREACH_CATEGORY).orElse(null);
        if (category == null) {
            log.error("No '{}' ticket category found — cannot raise SLA breach tickets", SLA_BREACH_CATEGORY);
            return;
        }
        UUID assigneeUserId = candidate.branchId() == null
                ? null
                : directory.managerOfBranch(candidate.branchId(), companyId).orElse(null);

        CreateTicketCommand command = new CreateTicketCommand(
                subjectFor(candidate),
                descriptionFor(candidate),
                category.getId(),
                null,
                TicketPriority.HIGH,
                candidate.shipmentId(),
                null,
                candidate.branchId(),
                companyId);

        Ticket ticket = ticketService.raiseSystemTicket(command, assigneeUserId);

        breachRepository.save(ShipmentSlaBreach.builder()
                .shipmentId(candidate.shipmentId())
                .stage(candidate.stage())
                .ticketId(ticket.getId())
                .hoursElapsed((int) candidate.hoursElapsed())
                .detectedAt(Instant.now())
                .build());

        log.info("SLA breach ticket {} raised for shipment {} ({}), {}h elapsed",
                ticket.getTicketNumber(), candidate.trackingNumber(), candidate.stage(), candidate.hoursElapsed());
    }

    private String subjectFor(ShipmentSlaPort.Candidate candidate) {
        return "SLA Breach: %s — shipment %s".formatted(label(candidate.stage()), candidate.trackingNumber());
    }

    private String descriptionFor(ShipmentSlaPort.Candidate candidate) {
        return ("Shipment %s has been in its current stage for %d hours, past the "
                + "configured SLA for \"%s\". Raised automatically by the SLA sweep.")
                .formatted(candidate.trackingNumber(), candidate.hoursElapsed(), label(candidate.stage()));
    }

    private String label(ShipmentSlaStage stage) {
        return switch (stage) {
            case BOOKING_TO_LOADING_SHEET -> "Booked, no loading sheet";
            case LOADING_SHEET_TO_THC -> "Loading sheet created, no THC";
            case THC_TO_INSCAN -> "THC generated, not in-scanned";
            case INSCAN_TO_DRS -> "In-scanned, no DRS";
            case DRS_TO_DELIVERY -> "DRS generated, not delivered";
        };
    }
}
