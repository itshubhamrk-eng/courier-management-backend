package com.courier.modules.support.application;

import com.courier.modules.support.application.command.UpsertSlaRuleCommand;
import com.courier.modules.support.domain.TicketSlaRule;
import com.courier.modules.support.domain.TicketSlaRuleRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketSlaRuleServiceImpl implements TicketSlaRuleService {

    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String ENTITY = "TicketSlaRule";

    private final TicketSlaRuleRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<TicketSlaRule> list() {
        return repository.findAllByCompanyIdOrderByPriorityAsc(requireCompany());
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public TicketSlaRule upsert(UpsertSlaRuleCommand command) {
        if (command.priority() == null) {
            throw new BusinessRuleException("Priority is required.");
        }
        if (command.firstResponseMinutes() <= 0 || command.resolutionMinutes() <= 0) {
            throw new BusinessRuleException("First response and resolution targets must be greater than zero minutes.");
        }
        if (command.resolutionMinutes() < command.firstResponseMinutes()) {
            throw new BusinessRuleException("The resolution target cannot be shorter than the first-response target.");
        }
        UUID companyId = requireCompany();

        TicketSlaRule rule = repository.findByCompanyIdAndPriorityAndActiveTrue(companyId, command.priority())
                .orElseGet(() -> TicketSlaRule.builder()
                        .priority(command.priority())
                        .active(true)
                        .build());
        rule.setFirstResponseMinutes(command.firstResponseMinutes());
        rule.setResolutionMinutes(command.resolutionMinutes());
        TicketSlaRule saved = repository.save(rule);

        boolean created = saved.getVersion() == null || saved.getVersion() == 0;
        auditService.record(created ? AuditAction.TICKET_SLA_RULE_CREATED : AuditAction.TICKET_SLA_RULE_UPDATED,
                ENTITY, saved.getId(),
                Map.of("priority", command.priority().name(),
                        "firstResponseMinutes", command.firstResponseMinutes(),
                        "resolutionMinutes", command.resolutionMinutes()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public TicketSlaRule setActive(UUID id, boolean active) {
        UUID companyId = requireCompany();
        TicketSlaRule rule = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        rule.setActive(active);
        TicketSlaRule saved = repository.save(rule);
        auditService.record(AuditAction.TICKET_SLA_RULE_UPDATED, ENTITY, saved.getId(), Map.of("active", active));
        return saved;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
    }
}
