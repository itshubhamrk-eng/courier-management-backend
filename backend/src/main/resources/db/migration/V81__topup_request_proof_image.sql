-- Proof-of-payment image for a branch's wallet top-up request. Nullable at the DB level
-- so existing rows (raised before this feature) stay valid; the API now requires it on
-- every new request (CreateTopupRequestRequest.proofImageUrl is @NotBlank).
ALTER TABLE wallet_topup_requests
    ADD COLUMN proof_image_url VARCHAR(500) NULL AFTER remarks;
