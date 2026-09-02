package com.courier.modules.company.infrastructure;

import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.User;
import com.courier.modules.shipment.domain.UserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers Shipment Booking's question about who booked a shipment, backed by the
 * {@code users} table. A separate bean from {@link FollowUpDirectory}/{@link TicketDirectory}
 * for the same "each consumer owns its own interface" reasoning
 * {@link CompanyShipmentBranchDirectory}'s own class comment gives.
 */
@Component
@RequiredArgsConstructor
public class CompanyShipmentUserDirectory implements UserLookupPort {

    private final CompanyUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserRef> findUser(UUID userId, UUID companyId) {
        if (userId == null || companyId == null) {
            return Optional.empty();
        }
        return userRepository.findByIdWithinCompany(userId, companyId).map(this::toRef);
    }

    private UserRef toRef(User user) {
        String fullName = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : ((user.getFirstName() == null ? "" : user.getFirstName())
                        + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new UserRef(user.getId(), fullName);
    }
}
