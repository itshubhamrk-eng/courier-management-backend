package com.courier.modules.finance.domain;

/**
 * Which of a company's own two Razorpay credential slots ({@code CompanyRazorpayConfig}'s
 * test/live key id + secret pairs) is actually used for wallet recharge right now. Both
 * slots can hold credentials at once — switching modes doesn't require re-entering
 * anything, only flipping which slot {@link CompanyRazorpayConfig#getKeyId()}/
 * {@link CompanyRazorpayConfig#getKeySecret()} resolve to.
 */
public enum RazorpayMode {
    TEST,
    LIVE
}
