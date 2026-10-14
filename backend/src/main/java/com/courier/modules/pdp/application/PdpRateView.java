package com.courier.modules.pdp.application;

import com.courier.modules.pdp.domain.PdpRateCard;
import com.courier.modules.pdp.domain.PdpRateSlab;

import java.util.List;

public record PdpRateView(PdpRateCard rate, String branchCode, String branchName, List<PdpRateSlab> slabs) {
}
