package com.courier.modules.crossing.application;

import com.courier.modules.crossing.domain.CrossingBranchDirectoryPort;
import com.courier.modules.crossing.domain.CrossingDetail;
import com.courier.modules.crossing.domain.CrossingDetailCriteria;
import com.courier.modules.crossing.domain.CrossingDetailRepository;
import com.courier.modules.crossing.domain.CrossingDetailSpecifications;
import com.courier.modules.crossing.domain.CrossingStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** See {@link CrossingService}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossingServiceImpl implements CrossingService {

    private static final String ENTITY = "CrossingDetail";

    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.HUB_MANAGER + "', '" + Roles.OPERATOR + "')";
    private static final String READERS = "isAuthenticated()";

    private final CrossingDetailRepository repository;
    private final CrossingBranchDirectoryPort branchDirectory;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public List<CrossingDetail> createLegs(UUID shipmentId, List<UUID> branchIds, BigDecimal charge) {
        UUID companyId = requireCompany();
        if (shipmentId == null) {
            throw new BusinessRuleException("A crossing needs a shipment behind it.");
        }
        if (branchIds == null || branchIds.isEmpty()) {
            throw new BusinessRuleException("Pick at least one branch this shipment is crossing through.");
        }
        BigDecimal safeCharge = charge == null || charge.signum() < 0 ? BigDecimal.ZERO : charge;

        List<CrossingDetail> saved = new java.util.ArrayList<>(branchIds.size());
        for (int i = 0; i < branchIds.size(); i++) {
            UUID branchId = branchIds.get(i);
            if (branchId == null) {
                throw new BusinessRuleException("Every crossing hop needs a branch.");
            }
            branchDirectory.findBranch(branchId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));

            CrossingDetail detail = CrossingDetail.builder()
                    .shipmentId(shipmentId)
                    .sequenceOrder(i)
                    .branchId(branchId)
                    .status(CrossingStatus.PENDING)
                    // The whole route's charge is carried on hop 0 only — no per-hop
                    // billing today.
                    .charge(i == 0 ? safeCharge : BigDecimal.ZERO)
                    .build();
            saved.add(repository.save(detail));
        }

        log.info("Crossing route of {} hop(s) created for shipment {} in company {} by {}",
                saved.size(), shipmentId, companyId, currentActor());
        auditService.record(AuditAction.CROSSING_CREATED, ENTITY, shipmentId,
                Map.of("shipmentId", shipmentId.toString(), "hopCount", saved.size(),
                        "charge", safeCharge.toPlainString()));

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public Optional<UUID> arriveAt(UUID shipmentId, UUID branchId) {
        UUID companyId = requireCompany();
        List<CrossingDetail> route = repository.findByShipmentWithinCompany(shipmentId, companyId);
        if (route.isEmpty()) {
            return Optional.empty();
        }

        CrossingDetail currentHop = route.stream()
                .filter(hop -> !hop.isTerminal())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "This shipment's crossing route is already complete."));
        if (!currentHop.getBranchId().equals(branchId)) {
            throw new BusinessRuleException(
                    "This shipment is not expected at this branch yet — its next crossing stop is elsewhere.");
        }
        currentHop.setStatus(CrossingStatus.COMPLETED);
        repository.save(currentHop);

        log.info("Crossing hop {} ({} of {}) completed for shipment {} at branch {} by {}",
                currentHop.getId(), currentHop.getSequenceOrder() + 1, route.size(), shipmentId, branchId,
                currentActor());
        auditService.record(AuditAction.CROSSING_STATUS_UPDATED, ENTITY, currentHop.getId(),
                Map.of("status", CrossingStatus.COMPLETED.name(), "shipmentId", shipmentId.toString()));

        return route.stream()
                .filter(hop -> hop.getSequenceOrder() == currentHop.getSequenceOrder() + 1)
                .map(CrossingDetail::getBranchId)
                .findFirst();
    }

    @Override
    @Transactional
    @PreAuthorize(READERS)
    public CrossingDetail getById(UUID id) {
        UUID companyId = requireCompany();
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    @Override
    @Transactional
    @PreAuthorize(READERS)
    public Page<CrossingDetail> search(CrossingDetailCriteria criteria, Pageable pageable) {
        UUID companyId = requireCompany();
        CrossingDetailCriteria safe =
                (criteria == null ? CrossingDetailCriteria.none() : criteria).scopedTo(companyId);
        return repository.findAll(CrossingDetailSpecifications.matching(safe), pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public CrossingDetail updateStatus(UUID id, CrossingStatus status, String remarks) {
        UUID companyId = requireCompany();
        if (status == null) {
            throw new BusinessRuleException("A crossing status is required.");
        }
        CrossingDetail detail = repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        if (detail.isTerminal()) {
            throw new BusinessRuleException(
                    "This crossing is already %s and cannot change status again."
                            .formatted(detail.getStatus().name().toLowerCase()));
        }
        detail.setStatus(status);
        CrossingDetail saved = repository.save(detail);

        log.info("Crossing {} moved to {} by {}", saved.getId(), status, currentActor());
        auditService.record(AuditAction.CROSSING_STATUS_UPDATED, ENTITY, saved.getId(),
                Map.of("status", status.name(), "remarks", remarks == null ? "" : remarks));

        return saved;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. A crossing belongs to a shipment of a "
                        + "company, so this operation must be performed by a user of that company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
