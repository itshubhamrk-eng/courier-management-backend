package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.manifest.domain.BranchDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Answers Manifest's questions about branches. See {@code CrossingBranchDirectory}, the
 *  identical adapter Crossing uses — the same arrangement, a different consumer. */
@Component
@RequiredArgsConstructor
public class ManifestBranchDirectory implements BranchDirectoryPort {

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
        return new BranchRef(branch.getId(), branch.getCompanyId(),
                branch.getBranchType() == null ? null : branch.getBranchType().name(), branch.isActive());
    }
}
