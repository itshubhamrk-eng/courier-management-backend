package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanySettings;
import com.courier.modules.company.domain.CompanySettingsRepository;
import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.User;
import com.courier.modules.support.domain.ShipmentSlaConfig;
import com.courier.modules.support.domain.ShipmentSlaThresholds;
import com.courier.modules.support.domain.TicketDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Answers Ticket Support's questions about users and branches, backed by the
 * {@code users} and {@code branches} tables. Same arrangement as
 * {@link CompanyBranchDirectory}: the consuming module owns the interface, this module
 * supplies the adapter.
 */
@Component
@RequiredArgsConstructor
public class TicketDirectory implements TicketDirectoryPort {

    private final CompanyUserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> findUser(UUID userId, UUID companyId) {
        if (userId == null || companyId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdWithinCompany(userId, companyId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean branchExists(UUID branchId, UUID companyId) {
        if (branchId == null || companyId == null) {
            return false;
        }
        return branchRepository.findByIdWithinCompany(branchId, companyId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> branchOfUser(UUID userId, UUID companyId) {
        if (userId == null || companyId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdWithinCompany(userId, companyId).map(User::getBranchId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> branchManagedBy(UUID userId, UUID companyId) {
        if (userId == null || companyId == null) {
            return Optional.empty();
        }
        return branchRepository.findFirstByCompanyIdAndManagerId(companyId, userId, PageRequest.of(0, 1))
                .stream().findFirst().map(Branch::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> managerOfBranch(UUID branchId, UUID companyId) {
        if (branchId == null || companyId == null) {
            return Optional.empty();
        }
        return branchRepository.findByIdWithinCompany(branchId, companyId).map(Branch::getManagerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listActiveCompanyIds() {
        return companyRepository.findAllActiveCompanyIds();
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentSlaConfig shipmentSlaSettings(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId)
                .map(this::toSlaConfig)
                .orElseGet(() -> new ShipmentSlaConfig(true,
                        new ShipmentSlaThresholds(24, 24, 48, 12, 12)));
    }

    private ShipmentSlaConfig toSlaConfig(CompanySettings s) {
        return new ShipmentSlaConfig(s.isSlaBreachTicketEnabled(), new ShipmentSlaThresholds(
                s.getSlaBookingToLoadingSheetHours(), s.getSlaLoadingSheetToThcHours(),
                s.getSlaThcToInscanHours(), s.getSlaInscanToDrsHours(), s.getSlaDrsToDeliveryHours()));
    }

    private UserRef toRef(User user) {
        String fullName = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : ((user.getFirstName() == null ? "" : user.getFirstName())
                        + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new UserRef(user.getId(), user.getCompanyId(), fullName, user.getEmail());
    }
}
