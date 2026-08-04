package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.CreateTopupRequestCommand;
import com.courier.modules.finance.domain.WalletTopupRequest;
import com.courier.modules.finance.domain.WalletTopupRequestCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * A branch's ask to fund its own wallet, and a company admin's decision on it.
 *
 * <p>Raising a request moves nothing. Only {@link #approve} moves money, and it does so
 * through {@link WalletService#credit}, the module's one existing money path — this
 * interface adds a workflow in front of that path, not a second way to touch a balance.
 */
public interface WalletTopupRequestService {

    /** Branch caller: their own branch, regardless of what is passed. Admin: must name one. */
    WalletTopupRequest create(CreateTopupRequestCommand command);

    /** One request, within the caller's scope. Foreign or out-of-scope answers 404. */
    WalletTopupRequest getById(UUID id);

    /** Paged, filtered. A branch caller's results are pinned to their own branch. */
    Page<WalletTopupRequest> search(WalletTopupRequestCriteria criteria, Pageable pageable);

    /**
     * Credits the wallet for the requested amount and marks the request APPROVED.
     * {@code COMPANY_ADMIN} only. Refused if the request is not PENDING.
     */
    WalletTopupRequest approve(UUID id, String decisionRemarks);

    /** Marks the request REJECTED. Moves nothing. {@code COMPANY_ADMIN} only. */
    WalletTopupRequest reject(UUID id, String decisionRemarks);
}
