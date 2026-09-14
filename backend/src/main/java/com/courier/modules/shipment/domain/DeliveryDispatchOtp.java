package com.courier.modules.shipment.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A re-issuable OTP challenge for one delivery user, ahead of Generate DRS — same
 * hash/expiry/attempt-limit shape {@code manifest.domain.Manifest}'s own dispatch OTP
 * uses, kept standalone rather than a column on {@link DeliveryAssignment} because the
 * challenge/response happens before any assignment row exists (Out For Delivery still
 * has no separate DRS/batch table — see V31's own migration comment). One row per
 * company+delivery user (upserted on every "Send OTP" click), not per DRS run.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "delivery_dispatch_otp",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_dispatch_otp_company_user",
                columnNames = {"company_id", "delivery_user_id"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class DeliveryDispatchOtp extends CompanyOwnedEntity {

    private static final int OTP_MAX_ATTEMPTS = 5;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID deliveryUserId;

    @Column(name = "otp_hash", length = 100)
    private String otpHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public void issue(String otpHash, Instant expiresAt) {
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.verifiedAt = null;
    }

    /**
     * The hash comparison itself happens in the service layer (it owns the
     * {@code PasswordEncoder}) — this only applies the resulting business rules: wrong
     * codes count against {@link #OTP_MAX_ATTEMPTS} before the code is invalidated
     * outright, and a right one is only good until {@link #expiresAt}.
     *
     * @throws BusinessRuleException no OTP requested yet, expired, or the code didn't match
     */
    public void registerVerificationAttempt(boolean codeMatched) {
        if (otpHash == null || expiresAt == null) {
            throw new BusinessRuleException("Request a delivery OTP before verifying it.");
        }
        if (Instant.now().isAfter(expiresAt)) {
            throw new BusinessRuleException("OTP has expired — request a new one.");
        }
        if (!codeMatched) {
            attempts++;
            if (attempts >= OTP_MAX_ATTEMPTS) {
                otpHash = null;
                expiresAt = null;
            }
            throw new BusinessRuleException("Incorrect OTP.");
        }
        this.verifiedAt = Instant.now();
    }
}
