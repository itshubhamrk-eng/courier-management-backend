package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyMenuLock;
import com.courier.modules.company.domain.CompanyMenuLockRepository;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CompanyMenuLockServiceImpl implements CompanyMenuLockService {

    private static final String SUPER_ADMIN_ONLY = "hasRole('" + Roles.SUPER_ADMIN + "')";
    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,79}");
    /** Never lockable: a company must always keep a way in and a way to manage itself. */
    private static final Set<String> NEVER_LOCKED = Set.of("dashboard", "settings");

    private final CompanyMenuLockRepository repository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    @Override
    @PreAuthorize(SUPER_ADMIN_ONLY)
    @Transactional(readOnly = true)
    public List<String> lockedMenus(UUID companyId) {
        requireCompany(companyId);
        return sorted(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> lockedMenusOfCurrentCompany() {
        return CompanyContext.getCompanyId().map(this::sorted).orElse(List.of());
    }

    @Override
    @PreAuthorize(SUPER_ADMIN_ONLY)
    @Transactional
    public List<String> replaceLockedMenus(UUID companyId, List<String> menuCodes) {
        requireCompany(companyId);
        Set<String> wanted = new TreeSet<>();
        for (String code : menuCodes == null ? List.<String>of() : menuCodes) {
            String c = code == null ? "" : code.trim();
            if (!CODE.matcher(c).matches()) {
                throw new BusinessRuleException("Invalid menu code: '" + c + "'");
            }
            if (NEVER_LOCKED.contains(c)) {
                throw new BusinessRuleException("Menu '" + c + "' cannot be locked");
            }
            wanted.add(c);
        }
        List<CompanyMenuLock> existing = repository.findAllByCompanyId(companyId);
        Set<String> have = new TreeSet<>();
        for (CompanyMenuLock lock : existing) {
            if (wanted.contains(lock.getMenuCode())) {
                have.add(lock.getMenuCode());
            } else {
                repository.delete(lock);
            }
        }
        // Flush the deletes first so a re-add of the same code never trips the unique key.
        repository.flush();
        for (String code : wanted) {
            if (!have.contains(code)) {
                repository.save(CompanyMenuLock.builder().companyId(companyId).menuCode(code).build());
            }
        }
        auditService.record(AuditAction.COMPANY_MENU_LOCKS_UPDATED, "Company", companyId,
                Map.of("lockedMenus", String.join(",", wanted)));
        return List.copyOf(wanted);
    }

    private List<String> sorted(UUID companyId) {
        return repository.findAllByCompanyId(companyId).stream()
                .map(CompanyMenuLock::getMenuCode).sorted().toList();
    }

    private void requireCompany(UUID companyId) {
        if (companyRepository.findByCompanyId(companyId).isEmpty()) {
            throw new ResourceNotFoundException("Company", companyId);
        }
    }
}
