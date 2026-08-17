package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.crossing.domain.CrossingBranchDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Answers Crossing's questions about branches. See {@code CompanyBranchDirectory}, the
 *  identical adapter Finance uses — the same arrangement, a different consumer. */
@Component
@RequiredArgsConstructor
public class CrossingBranchDirectory implements CrossingBranchDirectoryPort {

    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchRef> findBranch(UUID branchId, UUID companyId) {
        if (branchId == null || companyId == null) {
            return Optional.empty();
        }
        return branchRepository.findByIdWithinCompany(branchId, companyId).map(this::toRef);
    }

    private BranchRef toRef(Branch branch) {
        return new BranchRef(branch.getId(), branch.getCompanyId(), branch.getBranchCode(),
                branch.getBranchName(), branch.isActive());
    }
}
