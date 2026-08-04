package com.courier.modules.company.infrastructure;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The company directory auth authenticates against, backed by the {@code companies}
 * table. The only implementation of {@link CompanyDirectoryPort}.
 *
 * <p>It answers auth's two questions:
 *
 * <ul>
 *   <li><b>Does this company exist?</b> By the company's ownership key, the value every
 *       row it owns is stamped with.</li>
 *   <li><b>May it authenticate?</b> Only while the company's status is operational —
 *       {@code TRIAL} or {@code ACTIVE}. A suspended, expired, inactive or soft-deleted
 *       company reports inactive, so login is refused rather than merely awkward.</li>
 * </ul>
 *
 * <p>Lookup by {@code companyCode} is what login actually uses: codes are stored
 * uppercase and the lookup normalises, so a user typing {@code acme_logistics}
 * succeeds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyDirectory implements CompanyDirectoryPort {

    private final CompanyRepository companyRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyRef> findById(UUID companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findByCompanyId(companyId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyRef> findByCode(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            return Optional.empty();
        }
        return companyRepository.findByCompanyCode(Company.normaliseCode(companyCode))
                .map(this::toRef);
    }

    /**
     * Exposes only what auth needs: id, code and whether login is allowed. Contact
     * details, tax numbers and commercial terms stay inside this module.
     */
    private CompanyRef toRef(Company company) {
        boolean active = company.getStatus().isOperational();
        if (!active) {
            log.debug("Company {} is {} — authentication refused",
                    company.getCompanyCode(), company.getStatus());
        }
        return new CompanyRef(company.getCompanyId(), company.getCompanyCode(), active,
                company.getCompanyName(), company.getLogo());
    }
}
