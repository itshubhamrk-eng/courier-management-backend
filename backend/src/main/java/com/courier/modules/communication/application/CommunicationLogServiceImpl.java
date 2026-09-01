package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogCriteria;
import com.courier.modules.communication.domain.CommunicationLogRepository;
import com.courier.modules.communication.domain.CommunicationLogSpecifications;
import com.courier.modules.communication.domain.CommunicationStatus;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationLogServiceImpl implements CommunicationLogService {

    private static final String ENTITY = "CommunicationLog";
    private static final String READERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";
    private static final String RETRY = READERS;

    private final CommunicationLogRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Page<CommunicationLog> search(CommunicationLogCriteria criteria, Pageable pageable) {
        CommunicationLogCriteria safe = criteria == null ? CommunicationLogCriteria.empty() : criteria;
        return repository.findAll(CommunicationLogSpecifications.matching(safe), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public CommunicationLog getById(UUID id) {
        return loadOrThrow(id, requireCompany());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public List<CommunicationLog> forShipment(UUID shipmentId) {
        return repository.findAllByShipmentIdAndCompanyIdOrderByEventTypeAscChannelAsc(shipmentId, requireCompany());
    }

    @Override
    @Transactional
    @PreAuthorize(RETRY)
    public CommunicationLog retry(UUID id) {
        UUID companyId = requireCompany();
        CommunicationLog logRow = loadOrThrow(id, companyId);
        if (logRow.getStatus() != CommunicationStatus.FAILED) {
            throw new BusinessRuleException(
                    "Only a FAILED communication attempt can be retried (currently " + logRow.getStatus() + ").");
        }
        logRow.setStatus(CommunicationStatus.PENDING);
        logRow.setNextRetryAt(null);
        CommunicationLog saved = repository.save(logRow);

        log.info("Communication log {} (shipment {}, {}+{}) requeued for retry in company {} by {}",
                saved.getId(), saved.getShipmentId(), saved.getEventType(), saved.getChannel(), companyId,
                currentActor());
        auditService.record(AuditAction.COMMUNICATION_RETRIED, ENTITY, saved.getId(),
                Map.of("shipmentId", saved.getShipmentId().toString(), "eventType", saved.getEventType().name(),
                        "channel", saved.getChannel().name()));
        return saved;
    }

    private CommunicationLog loadOrThrow(UUID id, UUID companyId) {
        return repository.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Communication logs belong to a company."));
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUserId().map(UUID::toString).orElse("system");
    }
}
