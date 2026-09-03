package com.courier.modules.company.application;

import com.courier.modules.company.api.dto.AddBranchPincodesResponse;
import com.courier.modules.company.api.dto.AddBranchPincodesResponse.PincodeConflict;
import com.courier.modules.company.api.dto.BranchPincodeResponse;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchPincodeMapping;
import com.courier.modules.company.domain.BranchPincodeMappingRepository;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterDataSpecifications;
import com.courier.modules.master.domain.Pincode;
import com.courier.modules.master.domain.PincodeRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which branch serves which pincode. See {@link BranchPincodeMapping}.
 *
 * <p>The mapping row is company-owned for real (the caller's own company) — unlike
 * {@code PincodeAreaService}, this module never binds the platform reserved id for its own
 * writes. {@link com.courier.modules.master.domain.Pincode} is still the global master, so
 * every read of a pincode's own row crosses into {@code GlobalMasters.PLATFORM_COMPANY_ID}
 * only for the duration of that lookup, via {@link CompanyContext#runAs}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchPincodeMappingService {

    private static final String ENTITY = "BranchPincodeMapping";
    private static final String WRITE = "hasRole('" + Roles.COMPANY_ADMIN + "')";
    private static final String READ = "isAuthenticated()";

    private final BranchRepository branches;
    private final BranchPincodeMappingRepository mappings;
    private final PincodeRepository pincodes;
    private final BranchService branchService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public List<BranchPincodeResponse> list(UUID branchId) {
        UUID companyId = requireCompany();
        // Visibility (own-branch-only for a non-admin) is enforced by getById; the branch
        // itself is not otherwise needed here, only that the caller may see it.
        branchService.getById(branchId);
        List<BranchPincodeMapping> links =
                mappings.findByCompanyIdAndBranchIdOrderByCreatedAtAsc(companyId, branchId);
        Map<UUID, Pincode> byId = loadPincodes(links.stream().map(BranchPincodeMapping::getPincodeId).toList());
        return links.stream().map(link -> toResponse(link, byId.get(link.getPincodeId()))).toList();
    }

    @Transactional
    @PreAuthorize(WRITE)
    public AddBranchPincodesResponse addPincodes(UUID branchId, List<UUID> pincodeIds) {
        UUID companyId = requireCompany();
        Branch branch = loadBranch(branchId, companyId);

        Set<UUID> requested = new LinkedHashSet<>(pincodeIds);
        Map<UUID, Pincode> byId = loadPincodes(List.copyOf(requested));

        List<BranchPincodeResponse> added = new ArrayList<>();
        List<UUID> alreadyMapped = new ArrayList<>();
        List<PincodeConflict> conflicts = new ArrayList<>();

        for (UUID pincodeId : requested) {
            Pincode pincode = byId.get(pincodeId);
            if (pincode == null) {
                throw new ResourceNotFoundException("Pincode", pincodeId);
            }
            var existing = mappings.findByCompanyIdAndPincodeId(companyId, pincodeId);
            if (existing.isPresent()) {
                BranchPincodeMapping current = existing.get();
                if (current.getBranchId().equals(branchId)) {
                    alreadyMapped.add(pincodeId);
                } else {
                    String otherCode = loadBranch(current.getBranchId(), companyId).getBranchCode();
                    conflicts.add(new PincodeConflict(pincodeId, pincode.getCode(),
                            current.getBranchId(), otherCode));
                }
                continue;
            }
            BranchPincodeMapping mapping = new BranchPincodeMapping();
            mapping.setBranchId(branchId);
            mapping.setPincodeId(pincodeId);
            mapping.applyInvariants();
            BranchPincodeMapping saved = mappings.save(mapping);
            added.add(toResponse(saved, pincode));
        }

        if (!added.isEmpty()) {
            log.info("Branch {}: mapped {} pincode(s) by {}",
                    branch.getBranchCode(), added.size(), currentActor());
            auditService.record(AuditAction.BRANCH_PINCODES_MAPPED, ENTITY, branch.getId(),
                    Map.of("branchCode", branch.getBranchCode(),
                            "pincodeCodes", added.stream().map(BranchPincodeResponse::pincodeCode).toList()));
        }

        return new AddBranchPincodesResponse(added, alreadyMapped, conflicts);
    }

    @Transactional
    @PreAuthorize(WRITE)
    public void removePincode(UUID branchId, UUID mappingId) {
        UUID companyId = requireCompany();
        Branch branch = loadBranch(branchId, companyId);
        BranchPincodeMapping mapping = mappings.findByIdAndCompanyIdAndBranchId(mappingId, companyId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, mappingId));

        mapping.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        mappings.save(mapping);

        log.info("Branch {}: unmapped pincode {} by {}",
                branch.getBranchCode(), mapping.getPincodeId(), currentActor());
        auditService.record(AuditAction.BRANCH_PINCODE_UNMAPPED, ENTITY, branch.getId(),
                Map.of("branchCode", branch.getBranchCode(), "pincodeId", mapping.getPincodeId().toString()));
    }

    /** The one branch this pincode is mapped to, if any (V53's one-branch-per-pincode
     *  rule). Shipment Booking's Destination Pincode field uses this to auto-select
     *  Delivery Branch — empty when the pincode isn't mapped yet. */
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Optional<Branch> findBranchForPincode(UUID pincodeId) {
        UUID companyId = requireCompany();
        return mappings.findByCompanyIdAndPincodeId(companyId, pincodeId)
                .map(mapping -> loadBranch(mapping.getBranchId(), companyId));
    }

    // -------------------------------------------------------------------- helpers

    private BranchPincodeResponse toResponse(BranchPincodeMapping link, Pincode pincode) {
        return new BranchPincodeResponse(link.getId(), link.getPincodeId(),
                pincode == null ? null : pincode.getCode(), pincode == null ? null : pincode.getName());
    }

    private Map<UUID, Pincode> loadPincodes(List<UUID> pincodeIds) {
        if (pincodeIds.isEmpty()) {
            return Map.of();
        }
        return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, () -> {
            MasterDataCriteria criteria = MasterDataCriteria.none()
                    .withCompanyId(GlobalMasters.PLATFORM_COMPANY_ID)
                    .withIds(Set.copyOf(pincodeIds));
            Map<UUID, Pincode> byId = new LinkedHashMap<>();
            pincodes.findAll(MasterDataSpecifications.matching(criteria))
                    .forEach(pincode -> byId.put(pincode.getId(), pincode));
            return byId;
        });
    }

    private Branch loadBranch(UUID id, UUID companyId) {
        return branches.findByIdWithinCompany(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }

    private UUID requireCompany() {
        return CompanyContext.requireCompanyId();
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
