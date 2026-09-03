package com.courier.modules.master.infrastructure;

import com.courier.modules.districtfreight.domain.DistrictLookupPort;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.DistrictRepository;
import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterDataSpecifications;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers District Level Freight's questions about the (global) District master, backed
 * by {@code master_districts}.
 *
 * <p>District is global — every row is owned by {@link GlobalMasters#PLATFORM_COMPANY_ID}
 * — so every read here crosses into that reserved id via {@link CompanyContext#runAs},
 * the same pattern {@code BranchPincodeMappingService.loadPincodes} already uses to
 * resolve a Pincode. District Level Freight never sees the reserved id or imports
 * {@code District}/{@code DistrictRepository} directly.
 */
@Component
@RequiredArgsConstructor
public class MasterDistrictFreightDistrictDirectory implements DistrictLookupPort {

    private final DistrictRepository districtRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<DistrictRef> findDistrict(UUID districtId) {
        if (districtId == null) {
            return Optional.empty();
        }
        return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, () ->
                districtRepository.findByIdWithinCompany(districtId, GlobalMasters.PLATFORM_COMPANY_ID)
                        .map(this::toRef));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, DistrictRef> findDistricts(Collection<UUID> districtIds) {
        if (districtIds == null || districtIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> wanted = districtIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, () -> {
            MasterDataCriteria criteria = MasterDataCriteria.none()
                    .withCompanyId(GlobalMasters.PLATFORM_COMPANY_ID)
                    .withIds(wanted);
            Map<UUID, DistrictRef> byId = new LinkedHashMap<>();
            districtRepository.findAll(MasterDataSpecifications.matching(criteria))
                    .forEach(district -> byId.put(district.getId(), toRef(district)));
            return byId;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DistrictRef> findDistrictByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, () ->
                districtRepository.findByCompanyIdAndNameIgnoreCase(GlobalMasters.PLATFORM_COMPANY_ID, trimmed)
                        .map(this::toRef));
    }

    private DistrictRef toRef(District district) {
        return new DistrictRef(district.getId(), district.getCode(), district.getName(), district.isActive());
    }
}
