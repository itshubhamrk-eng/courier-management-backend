package com.courier.modules.finance.api;

import com.courier.modules.finance.api.dto.CreateTopupRequestRequest;
import com.courier.modules.finance.api.dto.TopupRequestResponse;
import com.courier.modules.finance.api.dto.TopupRequestSearchRequest;
import com.courier.modules.finance.application.command.CreateTopupRequestCommand;
import com.courier.modules.finance.domain.WalletTopupRequest;
import com.courier.modules.finance.domain.WalletTopupRequestCriteria;
import org.springframework.stereotype.Component;

@Component
public class TopupRequestMapper {

    public CreateTopupRequestCommand toCommand(CreateTopupRequestRequest request) {
        return new CreateTopupRequestCommand(request.branchId(), request.amount(), request.remarks());
    }

    public WalletTopupRequestCriteria toCriteria(TopupRequestSearchRequest search) {
        if (search == null) {
            return WalletTopupRequestCriteria.none();
        }
        return new WalletTopupRequestCriteria(null, search.branchId(), search.status());
    }

    public TopupRequestResponse toResponse(WalletTopupRequest r) {
        return new TopupRequestResponse(
                r.getId(), r.getCompanyId(), r.getWalletId(), r.getBranchId(),
                r.getRequestedAmount(), r.getRemarks(), r.getStatus(),
                r.getRequestedBy(), r.getCreatedAt(),
                r.getDecidedBy(), r.getDecidedAt(), r.getDecisionRemarks(), r.getTransactionId(),
                r.getVersion());
    }
}
