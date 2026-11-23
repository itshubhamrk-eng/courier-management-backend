package com.courier.modules.branchreport.api.dto;

import java.util.UUID;

/** Outcome of one branch's manual send: SENT, NO_PHONE, ALREADY_SENT, TEMPLATE_NOT_APPROVED or FAILED. */
public record SendOutcomeResponse(UUID branchId, String branchName, String result) {
}
