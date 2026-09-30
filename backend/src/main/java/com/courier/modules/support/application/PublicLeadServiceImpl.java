package com.courier.modules.support.application;

import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.support.api.dto.PublicLeadRequest;
import com.courier.modules.support.api.dto.PublicLeadResponse;
import com.courier.modules.support.domain.CompanyTicketSequenceRepository;
import com.courier.modules.support.domain.Ticket;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketCategoryRepository;
import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketRepository;
import com.courier.modules.support.domain.TicketStatus;
import com.courier.modules.support.domain.TicketStatusHistory;
import com.courier.modules.support.domain.TicketStatusHistoryRepository;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Backs {@code PublicLeadController} — see {@link PublicLeadService}'s doc for why this
 * bypasses {@link TicketService} entirely rather than trying to fake an authenticated
 * caller for it. Every submission lands under the single "Amazing Logistics" tenant this
 * marketing site belongs to (V95's category exists for exactly this flow).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicLeadServiceImpl implements PublicLeadService {

    private static final String COMPANY_CODE = "AMAZING_LOGISTICS";
    private static final String CATEGORY_NAME = "New Business / Quote Request";
    // TicketStatusHistory.changedByUserId is NOT NULL — see PublicTrackingServiceImpl's
    // matching constant for why this is a nil UUID rather than null.
    private static final UUID PUBLIC_ACTOR_ID = new UUID(0L, 0L);

    private final CompanyRepository companyRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketStatusHistoryRepository statusHistoryRepository;
    private final CompanyTicketSequenceRepository sequenceRepository;
    private final PublicLeadRateLimiter rateLimiter;

    @Override
    @Transactional
    public PublicLeadResponse submit(PublicLeadRequest request, String clientIp) {
        rateLimiter.checkAllowed(clientIp);

        UUID companyId = companyRepository.findByCompanyCode(COMPANY_CODE)
                .map(Company::getCompanyId)
                .orElseThrow(() -> {
                    log.error("Public lead submitted but no company with code {} exists", COMPANY_CODE);
                    return new BusinessRuleException(ErrorCode.SERVICE_UNAVAILABLE,
                            "Quote requests are not available right now. Please call or email us directly.");
                });

        TicketCategory category = categoryRepository.findByNameIgnoreCase(CATEGORY_NAME)
                .orElseThrow(() -> {
                    log.error("Public lead submitted but ticket category '{}' is missing", CATEGORY_NAME);
                    return new BusinessRuleException(ErrorCode.SERVICE_UNAVAILABLE,
                            "Quote requests are not available right now. Please call or email us directly.");
                });

        Ticket ticket = Ticket.builder()
                .ticketNumber(nextTicketNumber(companyId))
                .subject("Website enquiry: " + request.serviceRequired())
                .description(formatDescription(request))
                .categoryId(category.getId())
                .priority(TicketPriority.MEDIUM)
                .status(TicketStatus.OPEN)
                .escalated(false)
                .build();
        // No CompanyContext is bound for an unauthenticated request (CompanyResolutionFilter
        // only sets one from an authenticated caller), so CompanyEntityListener has nothing
        // to auto-stamp from — set it explicitly, the one legitimate exception to "code must
        // never set companyId by hand" on CompanyOwnedEntity's own doc comment.
        ticket.setCompanyId(companyId);
        Ticket saved = ticketRepository.save(ticket);

        TicketStatusHistory history = TicketStatusHistory.builder()
                .ticketId(saved.getId())
                .fromStatus(null)
                .toStatus(TicketStatus.OPEN)
                .changedByUserId(PUBLIC_ACTOR_ID)
                .remarks("Raised via public website form")
                .build();
        history.setCompanyId(companyId);
        statusHistoryRepository.save(history);

        log.info("Public lead {} raised from {} for service '{}'",
                saved.getTicketNumber(), clientIp, request.serviceRequired());

        return new PublicLeadResponse(saved.getTicketNumber());
    }

    private String formatDescription(PublicLeadRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Submitted via amazinglpl.com website.\n\n");
        sb.append("Name: ").append(r.name()).append('\n');
        if (r.company() != null && !r.company().isBlank()) {
            sb.append("Company: ").append(r.company()).append('\n');
        }
        sb.append("Email: ").append(r.email()).append('\n');
        sb.append("Phone: ").append(r.phone()).append('\n');
        sb.append("Service required: ").append(r.serviceRequired()).append('\n');
        sb.append("\nMessage:\n").append(r.message());
        return sb.toString();
    }

    private String nextTicketNumber(UUID companyId) {
        byte[] companyIdBytes = TimeOrderedUuid.toBytes(companyId);
        sequenceRepository.advance(companyIdBytes);
        long serial = sequenceRepository.nextValue();
        return "TKT-%06d".formatted(serial);
    }
}
