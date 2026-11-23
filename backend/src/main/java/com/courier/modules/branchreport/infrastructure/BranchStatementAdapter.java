package com.courier.modules.branchreport.infrastructure;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.branchreport.application.BranchStatementPdf;
import com.courier.modules.branchreport.application.BranchStatementService;
import com.courier.modules.communication.domain.BranchStatementPort;
import com.courier.modules.finance.domain.BranchDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The data-owning side of {@link BranchStatementPort}. The caller must have bound
 * {@code CompanyContext} to {@code companyId}: the user lookup and the statement's shipment audit
 * both read through the company filter. Managers are the ACTIVE {@code BRANCH_MANAGER} users whose
 * {@code users.branch_id} is the branch (not {@code Branch.managerId}, which names one person).
 */
@Component
@RequiredArgsConstructor
public class BranchStatementAdapter implements BranchStatementPort {

    static final int MIN_PHONE_DIGITS = 10;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter FILE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);

    private final BranchStatementService statementService;
    private final BranchDirectoryPort branchDirectory;
    private final UserRepository userRepository;
    private final CompanyDirectoryPort companyDirectory;

    @Override
    public List<BranchTarget> targets(UUID companyId, LocalDate from, LocalDate to) {
        List<BranchTarget> out = new ArrayList<>();
        for (BranchDirectoryPort.BranchRef branch : branchDirectory.listBranches(companyId)) {
            out.add(new BranchTarget(branch.branchId(), branch.branchName(),
                    managerPhones(companyId, branch.branchId())));
        }
        return out;
    }

    @Override
    public Optional<GeneratedStatement> generate(UUID companyId, UUID branchId, LocalDate from, LocalDate to) {
        return statementService.build(companyId, branchId, from, to).map(statement -> {
            String companyName = companyDirectory.findById(companyId)
                    .map(CompanyDirectoryPort.CompanyRef::name).orElse("");
            return new GeneratedStatement(BranchStatementPdf.render(statement, companyName),
                    slug(statement.branchName()) + "-" + FILE_MONTH.format(from) + ".pdf",
                    statement.branchName(), monthLabel(from, to));
        });
    }

    private List<String> managerPhones(UUID companyId, UUID branchId) {
        Set<String> phones = new LinkedHashSet<>();
        for (User manager : userRepository.findByCompanyIdAndBranchIdAndRoleAndStatus(
                companyId, branchId, Role.BRANCH_MANAGER, UserStatus.ACTIVE)) {
            String phone = usable(manager.getPhone());
            if (phone == null) {
                phone = usable(manager.getMobile()); // some users carry the number only in mobile
            }
            if (phone != null) {
                phones.add(phone);
            }
        }
        return List.copyOf(phones);
    }

    /** Digits only; null when fewer than {@value #MIN_PHONE_DIGITS} remain, so a bad number can
     *  never burn a send attempt on a Meta 400. */
    static String usable(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() >= MIN_PHONE_DIGITS ? digits : null;
    }

    static String slug(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "branch" : s;
    }

    /** "December 2026" for a whole calendar month, else "01 Dec 2026 to 15 Dec 2026". */
    static String monthLabel(LocalDate from, LocalDate to) {
        YearMonth ym = YearMonth.from(from);
        if (from.equals(ym.atDay(1)) && to.equals(ym.atEndOfMonth())) {
            return MONTH.format(from);
        }
        return DAY.format(from) + " to " + DAY.format(to);
    }
}
