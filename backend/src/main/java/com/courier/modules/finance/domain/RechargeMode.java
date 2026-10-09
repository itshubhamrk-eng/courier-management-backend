package com.courier.modules.finance.domain;

/** How a recharge reached the wallet: paid online through the gateway, or credited by hand. */
public enum RechargeMode {
    ONLINE,
    MANUAL
}
