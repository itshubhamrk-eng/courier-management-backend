package com.courier.modules.branchreport.infrastructure;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.branchreport.application.BranchStatementService;
import com.courier.modules.branchreport.domain.BranchStatement;
import com.courier.modules.communication.domain.BranchStatementPort.BranchTarget;
import com.courier.modules.communication.domain.BranchStatementPort.GeneratedStatement;
import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import com.courier.modules.finance.domain.BranchDirectoryPort.BranchRef;
import com.courier.modules.shipment.domain.VendorAuditRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchStatementAdapterTest {

    private static final LocalDate FROM = LocalDate.of(2026, 12, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private final BranchStatementService statementService = mock(BranchStatementService.class);
    private final BranchDirectoryPort branchDirectory = mock(BranchDirectoryPort.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CompanyDirectoryPort companyDirectory = mock(CompanyDirectoryPort.class);
    private final BranchStatementAdapter adapter =
            new BranchStatementAdapter(statementService, branchDirectory, userRepository, companyDirectory);

    private final UUID companyId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private static User user(String phone, String mobile) {
        User u = mock(User.class);
        when(u.getPhone()).thenReturn(phone);
        when(u.getMobile()).thenReturn(mobile);
        return u;
    }

    @Test
    void targetsAskOnlyForActiveBranchManagersOfThatBranch() {
        when(branchDirectory.listBranches(companyId)).thenReturn(List.of(
                new BranchRef(branchId, companyId, "PNQ", "Pune Central", true)));
        when(userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(companyId, branchId, Role.BRANCH_MANAGER,
                UserStatus.ACTIVE)).thenReturn(List.of());

        adapter.targets(companyId, FROM, TO);

        verify(userRepository).findByCompanyIdAndBranchIdAndRoleAndStatus(companyId, branchId, Role.BRANCH_MANAGER,
                UserStatus.ACTIVE);
    }

    @Test
    void phonesAreDigitsOnlyValidAndDeduplicated() {
        when(branchDirectory.listBranches(companyId)).thenReturn(List.of(
                new BranchRef(branchId, companyId, "PNQ", "Pune Central", true)));
        List<User> managers = List.of(
                user("+91 98765-43210", null),
                user("919876543210", null),     // same number again
                user("  ", null),               // blank
                user("n/a", null),              // no digits
                user("12345", null),            // too short
                user("123", "9123456780"));     // phone unusable, falls back to mobile
        when(userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(any(), any(), any(), any()))
                .thenReturn(managers);

        List<BranchTarget> targets = adapter.targets(companyId, FROM, TO);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).branchName()).isEqualTo("Pune Central");
        assertThat(targets.get(0).managerPhones()).containsExactly("919876543210", "9123456780");
    }

    @Test
    void branchWithoutManagersHasNoPhones() {
        when(branchDirectory.listBranches(companyId)).thenReturn(List.of(
                new BranchRef(branchId, companyId, "PNQ", "Pune Central", true)));
        when(userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(adapter.targets(companyId, FROM, TO).get(0).managerPhones()).isEmpty();
    }

    @Test
    void generateIsEmptyForAnUnknownBranch() {
        when(statementService.build(companyId, branchId, FROM, TO)).thenReturn(Optional.empty());
        assertThat(adapter.generate(companyId, branchId, FROM, TO)).isEmpty();
    }

    @Test
    void generateRendersAPdfWithSlugFileNameAndMonthLabel() {
        BigDecimal d = new BigDecimal("10");
        VendorAuditRow audit = new VendorAuditRow(branchId, 3, 7, d, 2, 4, d, d, d, 5, 4, d, d, d, d, 1, null, null,
                null, null, null);
        WalletService.PeriodSummary w = new WalletService.PeriodSummary(d, d, 2, d, d, d, d, d);
        BranchStatement statement = new BranchStatement(branchId, "PNQ", "Pune Central (HQ)", FROM, TO, audit, w,
                List.of());
        when(statementService.build(companyId, branchId, FROM, TO)).thenReturn(Optional.of(statement));
        when(companyDirectory.findById(companyId)).thenReturn(Optional.of(
                new CompanyDirectoryPort.CompanyRef(companyId, "ACME", true, "Acme Logistics", null)));

        GeneratedStatement out = adapter.generate(companyId, branchId, FROM, TO).orElseThrow();

        assertThat(out.fileName()).isEqualTo("pune-central-hq-2026-12.pdf");
        assertThat(out.monthLabel()).isEqualTo("December 2026");
        assertThat(out.branchName()).isEqualTo("Pune Central (HQ)");
        assertThat(new String(out.pdf(), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void customRangeLabelAndSlugEdgeCases() {
        assertThat(BranchStatementAdapter.monthLabel(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 15)))
                .isEqualTo("01 Dec 2026 to 15 Dec 2026");
        assertThat(BranchStatementAdapter.monthLabel(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)))
                .isEqualTo("February 2028");
        assertThat(BranchStatementAdapter.slug("***")).isEqualTo("branch");
    }
}
