package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.User;
import com.courier.modules.printlog.domain.PrintLogUserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyPrintLogUserDirectory implements PrintLogUserPort {

    private final CompanyUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> fullName(UUID userId, UUID companyId) {
        if (userId == null || companyId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdWithinCompany(userId, companyId).map(this::name);
    }

    private String name(User u) {
        if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) {
            return u.getDisplayName();
        }
        return ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
    }
}
