package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.User;
import com.courier.modules.followup.domain.FollowUpDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Answers Follow-up Management's questions about users and branches, backed by the
 * {@code users} and {@code branches} tables. Same arrangement as
 * {@link TicketDirectory}/{@link CompanyBranchDirectory}: the consuming module owns the
 * interface, this module supplies the adapter.
 */
@Component
@RequiredArgsConstructor
public class FollowUpDirectory implements FollowUpDirectoryPort {

    private final CompanyUserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;

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
    public List<UUID> listActiveCompanyIds() {
        return companyRepository.findAllActiveCompanyIds();
    }

    private UserRef toRef(User user) {
        String fullName = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : ((user.getFirstName() == null ? "" : user.getFirstName())
                        + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new UserRef(user.getId(), user.getCompanyId(), fullName, user.getEmail());
    }
}
