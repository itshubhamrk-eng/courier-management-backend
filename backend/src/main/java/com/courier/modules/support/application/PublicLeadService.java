package com.courier.modules.support.application;

import com.courier.modules.support.api.dto.PublicLeadRequest;
import com.courier.modules.support.api.dto.PublicLeadResponse;

/**
 * The marketing site's Contact/Request-a-Quote form, for an unauthenticated caller.
 * Deliberately separate from {@link TicketService}: every method there requires an
 * authenticated caller ({@code SecurityUtils.requireCurrentUser()}) or a resolved company
 * context, neither of which exists for an anonymous public submission — same "no auth, own
 * class" boundary {@code PublicTrackingService} draws in the shipment module.
 */
public interface PublicLeadService {

    /**
     * @throws com.courier.shared.exception.BusinessRuleException rate-limited, or no
     *         company/category is configured to receive it
     */
    PublicLeadResponse submit(PublicLeadRequest request, String clientIp);
}
