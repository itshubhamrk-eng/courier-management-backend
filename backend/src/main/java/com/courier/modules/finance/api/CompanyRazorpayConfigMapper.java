package com.courier.modules.finance.api;

import com.courier.modules.finance.api.dto.CompanyRazorpayConfigRequest;
import com.courier.modules.finance.api.dto.CompanyRazorpayConfigResponse;
import com.courier.modules.finance.application.command.CompanyRazorpayConfigCommand;
import com.courier.modules.finance.domain.CompanyRazorpayConfig;
import org.springframework.stereotype.Component;

/** Wire contract ↔ application/domain types for a company's own Razorpay credentials. */
@Component
public class CompanyRazorpayConfigMapper {

    public CompanyRazorpayConfigCommand toCommand(CompanyRazorpayConfigRequest r) {
        return new CompanyRazorpayConfigCommand(r.enabled(), r.keyId(), r.keySecret());
    }

    public CompanyRazorpayConfigResponse toResponse(CompanyRazorpayConfig c) {
        return new CompanyRazorpayConfigResponse(
                c.isEnabled(),
                c.getKeyId(),
                c.getKeySecret() != null && !c.getKeySecret().isBlank(),
                c.getUpdatedAt());
    }
}
