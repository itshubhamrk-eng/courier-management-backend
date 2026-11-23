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
            Optional<CompanyDirectoryPort.CompanyRef> company = companyDirectory.findById(companyId);
            String companyName = company.map(CompanyDirectoryPort.CompanyRef::name).orElse("");
            byte[] logo = company.map(CompanyDirectoryPort.CompanyRef::logo).map(BranchStatementAdapter::fetchLogo)
                    .orElse(null);
            return new GeneratedStatement(BranchStatementPdf.render(statement, companyName, logo),
                    slug(statement.branchName()) + "-" + FILE_MONTH.format(from) + ".pdf",
                    statement.branchName(), monthLabel(from, to));
        });
    }

    private static final int LOGO_MAX_BYTES = 2 * 1024 * 1024;
    private static final java.time.Duration LOGO_TIMEOUT = java.time.Duration.ofSeconds(3);

    /**
     * The company logo as image bytes, or null on any problem: the statement is still worth
     * sending without it. Only https URLs on the object store's own domain are fetched, so a
     * logo field pointing elsewhere cannot make the server call an arbitrary address.
     */
    static byte[] fetchLogo(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !host.toLowerCase(Locale.ROOT).endsWith(".amazonaws.com")) {
                return null;
            }
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(LOGO_TIMEOUT).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
            java.net.http.HttpResponse<byte[]> response = client.send(
                    java.net.http.HttpRequest.newBuilder(uri).timeout(LOGO_TIMEOUT).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            return response.statusCode() == 200 && body != null && body.length > 0 && body.length <= LOGO_MAX_BYTES
                    ? body : null;
        } catch (Exception e) {
            return null;
        }
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
