package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.LoginFailureAccountCount;
import com.courier.modules.auth.domain.LoginFailureIpCount;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Password spraying from one IP, or sustained guessing against one account. Flag-only:
 * CREDENTIAL_ATTACK is not an auto-ban rule and nothing here disables anyone.
 *
 * <p>What it measures: {@code login_history} rows with {@code event_type = LOGIN_FAILED}
 * (written only by {@code LoginAttemptService.recordFailure}, every reason included:
 * UNKNOWN_USER, BAD_PASSWORD, ACCOUNT_LOCKED, ACCOUNT_DISABLED, PASSWORD_EXPIRED,
 * GEO_RESTRICTED) inside the scan window. {@code SESSION_EXPIRED} rows are also
 * {@code success = false} and are excluded. {@code ip_address} there is the
 * {@code ClientIpResolver} address.
 * <ul>
 *   <li><b>IP level</b>: an IP whose failures hit at least
 *       {@code credentialAttack.distinctAccounts} distinct accounts, an account being
 *       (company, attempted email), summed across companies. One request per company
 *       the IP touched, with {@code userId = null}; {@code company_id} is NOT NULL on
 *       {@code login_history}, so the company is never ambiguous.</li>
 *   <li><b>Account level</b>: an account with at least
 *       {@code credentialAttack.failuresPerAccount} failures from any IPs. Raised for the
 *       user the email resolves to in that company (so {@code raise} dedups per user); an
 *       email that matches no user is skipped, since there is no account to protect and the
 *       IP-level check covers probing of made-up emails.</li>
 * </ul>
 *
 * <p>Self-dedup for the IP level: {@code raise} does not dedup when {@code userId} is null
 * and scan windows overlap by design, so before emitting the rule checks for a
 * CREDENTIAL_ATTACK incident in the same company whose evidence {@code ip} equals this IP,
 * detected within twice the scan window before the window end. A sustained attack therefore
 * yields about one incident per IP and company every 20 minutes (default window).
 */
@Component
@RequiredArgsConstructor
public class CredentialAttackRule implements ScanRule {

    private final LoginHistoryRepository loginHistoryRepository;
    private final UserRepository userRepository;
    private final SecurityIncidentRepository incidentRepository;
    private final SecurityProperties properties;

    @Override
    public IncidentRule code() {
        return IncidentRule.CREDENTIAL_ATTACK;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        List<IncidentRequest> out = new ArrayList<>(ipLevel(from, to));
        out.addAll(accountLevel(from, to));
        return out;
    }

    private List<IncidentRequest> ipLevel(Instant from, Instant to) {
        int threshold = properties.getCredentialAttack().getDistinctAccounts();
        Map<String, List<LoginFailureIpCount>> byIp = new LinkedHashMap<>();
        for (LoginFailureIpCount row : loginHistoryRepository.findLoginFailuresByIp(from, to)) {
            byIp.computeIfAbsent(row.ipAddress(), k -> new ArrayList<>()).add(row);
        }

        Instant dedupSince = to.minus(properties.getScanWindow().multipliedBy(2));
        List<IncidentRequest> out = new ArrayList<>();
        byIp.forEach((ip, rows) -> {
            long distinctAccounts = rows.stream().mapToLong(LoginFailureIpCount::distinctEmails).sum();
            if (distinctAccounts < threshold) {
                return;
            }
            for (LoginFailureIpCount row : rows) {
                if (incidentRepository.existsRecentForEvidenceIp(
                        IncidentRule.CREDENTIAL_ATTACK, row.companyId(), ip, dedupSince)) {
                    continue;
                }
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("ip", ip);
                evidence.put("distinctAccounts", distinctAccounts);
                evidence.put("distinctAccountsInCompany", row.distinctEmails());
                evidence.put("failuresInCompany", row.failures());
                evidence.put("companiesTargeted", rows.size());
                evidence.put("threshold", threshold);
                evidence.put("windowFrom", from.toString());
                evidence.put("windowTo", to.toString());
                out.add(new IncidentRequest(row.companyId(), null, IncidentRule.CREDENTIAL_ATTACK, evidence));
            }
        });
        return out;
    }

    private List<IncidentRequest> accountLevel(Instant from, Instant to) {
        int threshold = properties.getCredentialAttack().getFailuresPerAccount();
        List<IncidentRequest> out = new ArrayList<>();
        for (LoginFailureAccountCount row
                : loginHistoryRepository.findAccountsWithLoginFailuresAtLeast(from, to, threshold)) {
            Optional<UUID> userId = userRepository.findByEmailWithinCompany(row.attemptedEmail(), row.companyId())
                    .map(User::getId);
            if (userId.isEmpty()) {
                continue;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("attemptedEmail", row.attemptedEmail());
            evidence.put("failures", row.failures());
            evidence.put("distinctIps", row.distinctIps());
            evidence.put("threshold", threshold);
            evidence.put("windowFrom", from.toString());
            evidence.put("windowTo", to.toString());
            out.add(new IncidentRequest(row.companyId(), userId.get(), IncidentRule.CREDENTIAL_ATTACK, evidence));
        }
        return out;
    }
}
