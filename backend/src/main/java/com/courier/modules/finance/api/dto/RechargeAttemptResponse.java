package com.courier.modules.finance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "RechargeAttemptResponse", description = "One online recharge attempt: PENDING, SUCCESS or FAILED")
public record RechargeAttemptResponse(
        UUID id,
        String transactionNo,
        UUID branchId,
        String branchCode,
        String branchName,
        BigDecimal amount,
        String currency,
        String mode,
        String gateway,
        String gatewayOrderId,
        String gatewayPaymentId,
        String status,
        String failureReason,
        String remarks,
        String recordedByName,
        UUID ledgerTransactionId,
        Instant lastCheckedAt,
        Instant settledAt,
        Instant createdAt
) {
}
