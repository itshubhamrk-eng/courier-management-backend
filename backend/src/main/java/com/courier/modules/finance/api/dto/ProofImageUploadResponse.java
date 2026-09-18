package com.courier.modules.finance.api.dto;

/** Response of {@code POST /branch-wallet/topup-requests/upload-proof} — the caller
 *  passes {@code url} straight into {@link CreateTopupRequestRequest#proofImageUrl()}. */
public record ProofImageUploadResponse(String url) {
}
