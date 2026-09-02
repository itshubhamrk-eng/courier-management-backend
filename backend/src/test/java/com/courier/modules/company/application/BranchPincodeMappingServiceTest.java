package com.courier.modules.company.application;

import com.courier.modules.company.api.dto.AddBranchPincodesResponse;
import com.courier.modules.company.domain.Branch;
import com.courier.modules.company.domain.BranchPincodeMapping;
import com.courier.modules.company.domain.BranchPincodeMappingRepository;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.master.domain.Pincode;
import com.courier.modules.master.domain.PincodeRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Branch<->pincode mapping rules: one branch per pincode per company, with repositories
 *  and the audit trail mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BranchPincodeMappingServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();

    @Mock private BranchRepository branches;
    @Mock private BranchPincodeMappingRepository mappings;
    @Mock private PincodeRepository pincodes;
    @Mock private BranchService branchService;
    @Mock private AuditService auditService;

    private BranchPincodeMappingService service;

    @BeforeEach
    void setUp() {
        service = new BranchPincodeMappingService(branches, mappings, pincodes, branchService, auditService);
        CompanyContext.setCompanyId(TENANT);
        planted(CALLER, Roles.COMPANY_ADMIN);

        when(branches.findByIdWithinCompany(eq(BRANCH_A), eq(TENANT))).thenReturn(Optional.of(branch(BRANCH_A, "PUNE")));
        when(branches.findByIdWithinCompany(eq(BRANCH_B), eq(TENANT))).thenReturn(Optional.of(branch(BRANCH_B, "MUMBAI")));
        when(mappings.save(any(BranchPincodeMapping.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("New pincodes are mapped to the branch")
    void addPincodes_mapsNewPincodes() {
        Pincode p1 = pincode("411001");
        Pincode p2 = pincode("411002");
        when(pincodes.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));
        when(mappings.findByCompanyIdAndPincodeId(eq(TENANT), any())).thenReturn(Optional.empty());

        AddBranchPincodesResponse result = service.addPincodes(BRANCH_A, List.of(p1.getId(), p2.getId()));

        assertThat(result.added()).hasSize(2);
        assertThat(result.alreadyMapped()).isEmpty();
        assertThat(result.conflicts()).isEmpty();
        ArgumentCaptor<BranchPincodeMapping> captor = ArgumentCaptor.forClass(BranchPincodeMapping.class);
        verify(mappings, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(m -> assertThat(m.getBranchId()).isEqualTo(BRANCH_A));
    }

    @Test
    @DisplayName("A pincode already mapped to this branch is reported, not re-applied")
    void addPincodes_alreadyMappedToSameBranch_isSkipped() {
        Pincode p1 = pincode("411001");
        when(pincodes.findAll(any(Specification.class))).thenReturn(List.of(p1));
        BranchPincodeMapping existing = new BranchPincodeMapping();
        existing.setBranchId(BRANCH_A);
        existing.setPincodeId(p1.getId());
        when(mappings.findByCompanyIdAndPincodeId(TENANT, p1.getId())).thenReturn(Optional.of(existing));

        AddBranchPincodesResponse result = service.addPincodes(BRANCH_A, List.of(p1.getId()));

        assertThat(result.added()).isEmpty();
        assertThat(result.alreadyMapped()).containsExactly(p1.getId());
        assertThat(result.conflicts()).isEmpty();
        verify(mappings, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("A pincode owned by a different branch is a conflict, never moved")
    void addPincodes_conflictWithOtherBranch_isReported() {
        Pincode p1 = pincode("411001");
        when(pincodes.findAll(any(Specification.class))).thenReturn(List.of(p1));
        BranchPincodeMapping existing = new BranchPincodeMapping();
        existing.setBranchId(BRANCH_B);
        existing.setPincodeId(p1.getId());
        when(mappings.findByCompanyIdAndPincodeId(TENANT, p1.getId())).thenReturn(Optional.of(existing));

        AddBranchPincodesResponse result = service.addPincodes(BRANCH_A, List.of(p1.getId()));

        assertThat(result.added()).isEmpty();
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).branchId()).isEqualTo(BRANCH_B);
        assertThat(result.conflicts().get(0).branchCode()).isEqualTo("MUMBAI");
        verify(mappings, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("An unknown pincode id is refused")
    void addPincodes_unknownPincode_throws() {
        when(pincodes.findAll(any(Specification.class))).thenReturn(List.of());
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.addPincodes(BRANCH_A, List.of(unknown)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Removing a mapping soft-deletes it")
    void removePincode_softDeletes() {
        BranchPincodeMapping mapping = new BranchPincodeMapping();
        mapping.setBranchId(BRANCH_A);
        mapping.setPincodeId(UUID.randomUUID());
        when(mappings.findByIdAndCompanyIdAndBranchId(mapping.getId(), TENANT, BRANCH_A))
                .thenReturn(Optional.of(mapping));

        service.removePincode(BRANCH_A, mapping.getId());

        assertThat(mapping.isDeleted()).isTrue();
        verify(mappings).save(mapping);
    }

    @Test
    @DisplayName("Removing an unknown mapping is refused")
    void removePincode_unknownMapping_throws() {
        UUID mappingId = UUID.randomUUID();
        when(mappings.findByIdAndCompanyIdAndBranchId(mappingId, TENANT, BRANCH_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removePincode(BRANCH_A, mappingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------- helpers

    private static Branch branch(UUID id, String code) {
        Branch b = Branch.builder().branchCode(code).branchName(code).build();
        b.setId(id);
        b.setCompanyId(TENANT);
        return b;
    }

    private static Pincode pincode(String code) {
        Pincode p = new Pincode();
        p.setCode(code);
        p.setName(code + " locality");
        return p;
    }

    private void planted(UUID userId, String... roles) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, TENANT, "admin@legacy.test", Set.of(roles), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
