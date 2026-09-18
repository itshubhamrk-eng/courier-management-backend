package com.courier.modules.finance.application.command;

import com.courier.modules.finance.domain.RazorpayMode;

/**
 * A company's own Razorpay credentials, as submitted by their {@code COMPANY_ADMIN}. Test
 * and live are separate, independently-saved pairs — both can be filled in the same
 * request; {@code mode} only says which one is actually used.
 *
 * @param enabled       whether this company's own account should be used instead of the
 *                       platform-wide gateway
 * @param mode          which credential pair below is used for wallet recharge
 * @param testKeyId     the test publishable key id
 * @param testKeySecret the test signing secret; blank means "keep the one already stored"
 * @param liveKeyId     the live publishable key id
 * @param liveKeySecret the live signing secret; blank means "keep the one already stored"
 */
public record CompanyRazorpayConfigCommand(
        boolean enabled,
        RazorpayMode mode,
        String testKeyId,
        String testKeySecret,
        String liveKeyId,
        String liveKeySecret) {

    public boolean hasNewTestSecret() {
        return testKeySecret != null && !testKeySecret.isBlank();
    }

    public boolean hasNewLiveSecret() {
        return liveKeySecret != null && !liveKeySecret.isBlank();
    }
}
