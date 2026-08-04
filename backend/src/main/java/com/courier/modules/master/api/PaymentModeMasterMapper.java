package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreatePaymentModeRequest;
import com.courier.modules.master.api.dto.PaymentModeResponse;
import com.courier.modules.master.api.dto.UpdatePaymentModeRequest;
import com.courier.modules.master.application.command.PaymentModeCommand;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Wire contract to application/domain types for payment modes. */
@Component
public class PaymentModeMasterMapper {

    public PaymentModeCommand toCommand(CreatePaymentModeRequest r) {
        return new PaymentModeCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.collectAtBooking(), r.collectAtDelivery(), r.requiresCreditAccount(),
                r.cashOnDelivery(), null);
    }

    public PaymentModeCommand toCommand(UpdatePaymentModeRequest r) {
        return new PaymentModeCommand(null, r.name(), r.description(), r.displayOrder(),
                r.collectAtBooking(), r.collectAtDelivery(), r.requiresCreditAccount(),
                r.cashOnDelivery(), r.version());
    }

    public PaymentModeResponse toResponse(PaymentMode p) {
        return new PaymentModeResponse(p.getId(), p.getCompanyId(), p.getCode(), p.getName(),
                p.getDescription(), p.getStatus(), p.getDisplayOrder(),
                p.isCollectAtBooking(), p.isCollectAtDelivery(),
                p.isRequiresCreditAccount(), p.isCashOnDelivery(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt(), p.getVersion());
    }

    public PageResponse<PaymentModeResponse> toPage(Page<PaymentMode> page) {
        return PageResponse.from(page, this::toResponse);
    }
}
