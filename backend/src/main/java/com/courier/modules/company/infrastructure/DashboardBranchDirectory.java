package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchCriteria;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.BranchSpecifications;
import com.courier.modules.dashboard.domain.DashboardBranchDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Answers the dashboard's questions about branches, backed by the {@code branches} table.
 * See {@code CompanyMasterBranchDirectory}, the identical batch-lookup adapter Master Data
 * uses — the same arrangement, a different consumer.
 */
@Component
@RequiredArgsConstructor
public class DashboardBranchDirectory implements DashboardBranchDirectoryPort {

    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BranchRef> findBranches(Collection<UUID> branchIds, UUID companyId) {
        if (branchIds == null || branchIds.isEmpty() || companyId == null) {
            return Map.of();
        }
        Set<UUID> wanted = branchIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        BranchCriteria criteria = BranchCriteria.none().withCompanyId(companyId).withBranchIds(wanted);
        return branchRepository.findAll(BranchSpecifications.matching(criteria)).stream()
                .collect(Collectors.toMap(Branch::getId, this::toRef));
    }

    private BranchRef toRef(Branch branch) {
        return new BranchRef(branch.getId(), branch.getBranchCode(), branch.getBranchName());
    }
}
