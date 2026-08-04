package com.courier.modules.master.application;

import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterDataEntity;
import com.courier.modules.master.domain.MasterDataRepository;
import com.courier.modules.master.domain.MasterDataSpecifications;
import com.courier.shared.company.CompanyContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves parent ids to display names, in one query per page.
 *
 * <p>A state list has to read "Maharashtra, India", so every row needs its country's
 * name. Fetching it per row is the N+1 that makes a hundred-row page a hundred and one
 * queries; making the client fetch the whole country list to build its own map is the same
 * cost moved somewhere less visible. This collects the page's distinct parent ids and
 * asks once.
 *
 * <p><b>Through the specification, never {@code findAllById}.</b> A load by primary key
 * bypasses the Hibernate company filter (ARCHITECTURE §3), and this method exists to answer
 * for one company. An id from another company is simply absent from the result, so the
 * response shows no name rather than leaking one.
 */
@Service
public class MasterNameResolver {

    @Transactional(readOnly = true)
    public <E extends MasterDataEntity> Map<UUID, String> namesById(MasterDataRepository<E> repository,
                                                                    Collection<UUID> ids) {
        return resolve(repository, ids, CompanyContext.getCompanyId().orElse(null));
    }

    /**
     * The same, for a parent that lives in a global list.
     *
     * <p>Every geography parent does. Resolving one against the caller's own company
     * would find nothing — the rows belong to the platform — and the response would show
     * a state with no country rather than an error, which is the kind of blank nobody
     * investigates. The two spellings are separate methods so a caller has to say which
     * kind of parent it is holding.
     */
    @Transactional(readOnly = true)
    public <E extends MasterDataEntity> Map<UUID, String> globalNamesById(
            MasterDataRepository<E> repository, Collection<UUID> ids) {
        return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID,
                () -> resolve(repository, ids, GlobalMasters.PLATFORM_COMPANY_ID));
    }

    private <E extends MasterDataEntity> Map<UUID, String> resolve(
            MasterDataRepository<E> repository, Collection<UUID> ids, UUID companyId) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }

        MasterDataCriteria criteria = MasterDataCriteria.none()
                .withCompanyId(companyId)
                .withIds(wanted);

        Map<UUID, String> names = new LinkedHashMap<>();
        repository.findAll(MasterDataSpecifications.matching(criteria))
                .forEach(entity -> names.put(entity.getId(), entity.getName()));
        return names;
    }
}
