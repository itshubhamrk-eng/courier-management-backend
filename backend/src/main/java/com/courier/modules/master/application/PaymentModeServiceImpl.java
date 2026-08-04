package com.courier.modules.master.application;

import com.courier.modules.master.application.command.PaymentModeCommand;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.domain.PaymentModeRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.security.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Payment modes. */
@Slf4j
@Service
public class PaymentModeServiceImpl extends AbstractMasterDataService<PaymentMode>
        implements PaymentModeService {

    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    public PaymentModeServiceImpl(PaymentModeRepository paymentModes,
                                  MasterUniquenessChecker uniqueness,
                                  AuditService auditService) {
        super(paymentModes, uniqueness, auditService, "Payment mode", MasterTable.PAYMENT_MODES);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PaymentMode create(PaymentModeCommand command) {
        PaymentMode paymentMode = new PaymentMode();
        applyCommonFields(paymentMode, command.code(), command.name(), command.description(),
                command.displayOrder());
        applySpecific(paymentMode, command);
        return createEntity(paymentMode);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PaymentMode update(UUID id, PaymentModeCommand command) {
        return updateEntity(id, command.expectedVersion(), paymentMode -> {
            applyCommonFields(paymentMode, null, command.name(), command.description(),
                    command.displayOrder());
            applySpecific(paymentMode, command);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public PaymentMode getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<PaymentMode> search(MasterDataCriteria criteria, Pageable pageable) {
        return doSearch(criteria, pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        doDelete(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PaymentMode activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public PaymentMode deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(PaymentMode paymentMode, UUID companyId, UUID excludeId) {
        requireAvailable(companyId, excludeId, Map.of("name", paymentMode.getName()),
                "name", paymentMode.getName());
    }

    @Override
    protected Map<String, Object> snapshot(PaymentMode paymentMode) {
        Map<String, Object> values = super.snapshot(paymentMode);
        values.put("collectAtBooking", paymentMode.isCollectAtBooking());
        values.put("collectAtDelivery", paymentMode.isCollectAtDelivery());
        values.put("requiresCreditAccount", paymentMode.isRequiresCreditAccount());
        values.put("cashOnDelivery", paymentMode.isCashOnDelivery());
        return values;
    }

    private void applySpecific(PaymentMode paymentMode, PaymentModeCommand command) {
        paymentMode.setCollectAtBooking(Boolean.TRUE.equals(command.collectAtBooking()));
        paymentMode.setCollectAtDelivery(Boolean.TRUE.equals(command.collectAtDelivery()));
        paymentMode.setRequiresCreditAccount(Boolean.TRUE.equals(command.requiresCreditAccount()));
        paymentMode.setCashOnDelivery(Boolean.TRUE.equals(command.cashOnDelivery()));
    }
}
