package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.CompanyRazorpayConfigCommand;
import com.courier.modules.finance.domain.CompanyRazorpayConfig;

/**
 * A company's own Razorpay credentials — {@code COMPANY_ADMIN} only, both read and write.
 * See {@code CompanyPaymentGatewayResolver} for how this feeds wallet recharge.
 */
public interface CompanyRazorpayConfigService {

    /**
     * The company's config, or an unconfigured default ({@code enabled=false}, no key) if
     * it has never saved one — never persists on read, unlike {@code CompanySettings}.
     */
    CompanyRazorpayConfig get();

    CompanyRazorpayConfig update(CompanyRazorpayConfigCommand command);
}
