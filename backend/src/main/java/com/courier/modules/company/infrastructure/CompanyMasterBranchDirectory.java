package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchCriteria;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.company.domain.BranchSpecifications;
import com.courier.modules.master.domain.BranchLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Answers Master Data's questions about branches, backed by the {@code branches} table.
 *
 * <p>The Route master needs to know that a booking and a delivery branch exist, belong to
 * the caller's company and are still operational. It gets a flat
 * {@link BranchLookupPort.BranchRef} and nothing it could mutate — Master never imports
 * {@code Branch} or {@code BranchRepository}.
 *
 * <p>A separate bean from {@link CompanyBranchDirectory}, which serves Finance, because
 * the two consumers own different interfaces. Collapsing them into one adapter would put
 * a Finance type on Master's dependency path for no gain.
 *
 * <p>The company is taken from the argument, never from the ambient {@code CompanyContext}:
 * a caller that forgot to bind one gets an empty result rather than another company's branch.
 */
@Component
@RequiredArgsConstructor
public class CompanyMasterBranchDirectory implements BranchLookupPort {

    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<BranchRef> findBranch(UUID branchId, UUID companyId) {
        if (branchId == null || companyId == null) {
            return Optional.empty();
        }
        return branchRepository.findByIdWithinCompany(branchId, companyId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BranchRef> findBranches(Collection<UUID> branchIds, UUID companyId) {
        if (branchIds == null || branchIds.isEmpty() || companyId == null) {
            return Map.of();
        }
        Set<UUID> wanted = branchIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        // Through the specification, not findAllById: a load by primary key is not
        // filtered by company, and this method exists to answer for one company only.
        BranchCriteria criteria = BranchCriteria.none().withCompanyId(companyId).withBranchIds(wanted);
        return branchRepository.findAll(BranchSpecifications.matching(criteria)).stream()
                .collect(Collectors.toMap(Branch::getId, this::toRef));
    }

    private BranchRef toRef(Branch branch) {
        return new BranchRef(branch.getId(), branch.getBranchCode(), branch.getBranchName(),
                branch.isActive());
    }
}
