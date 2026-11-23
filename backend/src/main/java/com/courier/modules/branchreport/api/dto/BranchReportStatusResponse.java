package com.courier.modules.branchreport.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Per-branch WhatsApp send state for one period; lastStatus/lastSentAt are null if never attempted/sent. */
public record BranchReportStatusResponse(UUID branchId, String branchName, int managerPhoneCount,
                                         String lastStatus, Instant lastSentAt) {
}
