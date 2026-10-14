package com.courier.modules.pdp.domain;

/** ACCRUED: priced and waiting to be billed. UNRATED: no live rate card when earned (amount 0). BILLED: on a statement. */
public enum PdpEarningStatus { ACCRUED, UNRATED, BILLED, REVERSED }
