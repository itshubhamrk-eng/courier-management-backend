package com.courier.modules.finance.application.command;

/**
 * A company's own Razorpay credentials, as submitted by their {@code COMPANY_ADMIN}.
 *
 * @param enabled   whether this company's own account should be used instead of the
 *                  platform-wide gateway
 * @param keyId     the publishable key id
 * @param keySecret the signing secret; blank means "keep the one already stored" — the
 *                  service never has anything to echo back, so this is the only way to
 *                  express "no change" without asking the admin to retype it
 */
public record CompanyRazorpayConfigCommand(boolean enabled, String keyId, String keySecret) {

    public boolean hasNewSecret() {
        return keySecret != null && !keySecret.isBlank();
    }
}
