package com.courier.modules.company.infrastructure;

import com.courier.modules.auth.application.port.BranchDirectoryPort;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers auth's questions about branches for "login as branch" impersonation, the same
 * arrangement as {@link CompanyDirectory}: auth owns {@link BranchDirectoryPort}, this
 * module supplies the adapter, backed by the {@code branches} table.
 *
 * <p>Distinct from {@link CompanyBranchDirectory} (Finance's own seam) — a different
 * module, a different minimal projection, kept separate rather than shared so neither
 * module's view of "what a branch is" leaks into the other's contract.
 */
@Component
@RequiredArgsConstructor
public class AuthBranchDirectory implements BranchDirectoryPort {

    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchRef> findById(UUID branchId, UUID companyId) {
        if (branchId == null || companyId == null) {
            return Optional.empty();
        }
        return branchRepository.findByIdWithinCompany(branchId, companyId).map(this::toRef);
    }

    private BranchRef toRef(Branch branch) {
        return new BranchRef(branch.getId(), branch.getBranchCode(), branch.getBranchName(), branch.isActive());
    }
}
