package com.courier.modules.company.domain;

import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyTest {

    private static Company.CompanyBuilder company(CompanyStatus status) {
        return Company.builder()
                .companyId(Company.newCompanyId())
                .companyCode("  acme_logistics  ")
                .companyName("  Acme Logistics  ")
                .subscriptionPlanId(UUID.randomUUID())
                .status(status)
                .email("  OPS@Acme-Logistics.com  ")
                .mobile("+91 9876543210")
                .gstNumber(" 27aapfu0939f1zv ")
                .panNumber(" aapfu0939f ")
                .currency("inr");
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("code, email, tax numbers and currency are normalised on write")
        void normalises() {
            Company subject = company(CompanyStatus.TRIAL).build();

            subject.applyInvariants();

            assertThat(subject.getCompanyCode()).isEqualTo("ACME_LOGISTICS");
            assertThat(subject.getCompanyName()).isEqualTo("Acme Logistics");
            assertThat(subject.getEmail()).isEqualTo("ops@acme-logistics.com");
            assertThat(subject.getGstNumber()).isEqualTo("27AAPFU0939F1ZV");
            assertThat(subject.getPanNumber()).isEqualTo("AAPFU0939F");
            assertThat(subject.getCurrency()).isEqualTo("INR");
        }

        @Test
        @DisplayName("a blank tax number becomes null, so the unique key ignores it")
        void blankTaxIdBecomesNull() {
            // MySQL permits repeated NULLs in a unique index but not repeated empty
            // strings — two companies without a GSTIN must both be storable.
            Company subject = company(CompanyStatus.ACTIVE).gstNumber("   ").panNumber("").build();

            subject.applyInvariants();

            assertThat(subject.getGstNumber()).isNull();
            assertThat(subject.getPanNumber()).isNull();
        }

        @Test
        @DisplayName("missing localisation falls back to platform defaults")
        void defaults() {
            Company subject = company(CompanyStatus.ACTIVE)
                    .currency(null).timezone(null).language(null).build();

            subject.applyInvariants();

            assertThat(subject.getCurrency()).isEqualTo(Company.DEFAULT_CURRENCY);
            assertThat(subject.getTimezone()).isEqualTo(Company.DEFAULT_TIMEZONE);
            assertThat(subject.getLanguage()).isEqualTo(Company.DEFAULT_LANGUAGE);
        }

        @Test
        @DisplayName("displayName falls back to companyName")
        void displayNameFallback() {
            Company subject = company(CompanyStatus.ACTIVE).displayName(null).build();
            subject.applyInvariants();

            assertThat(subject.effectiveDisplayName()).isEqualTo("Acme Logistics");
        }

        @Test
        @DisplayName("an end date before its start date is rejected")
        void rejectsInvertedDates() {
            Company subject = company(CompanyStatus.TRIAL)
                    .trialStartDate(LocalDate.of(2026, 7, 10))
                    .trialEndDate(LocalDate.of(2026, 7, 1))
                    .build();

            assertThatThrownBy(subject::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Trial end date");
        }

        @Test
        @DisplayName("an inverted subscription window is rejected too")
        void rejectsInvertedSubscription() {
            Company subject = company(CompanyStatus.ACTIVE)
                    .subscriptionStartDate(LocalDate.of(2027, 1, 1))
                    .subscriptionEndDate(LocalDate.of(2026, 1, 1))
                    .build();

            assertThatThrownBy(subject::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Subscription end date");
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("every legal transition is accepted")
        void legalTransitions() {
            assertThat(CompanyStatus.TRIAL.canTransitionTo(CompanyStatus.ACTIVE)).isTrue();
            assertThat(CompanyStatus.TRIAL.canTransitionTo(CompanyStatus.SUSPENDED)).isTrue();
            assertThat(CompanyStatus.TRIAL.canTransitionTo(CompanyStatus.EXPIRED)).isTrue();
            assertThat(CompanyStatus.ACTIVE.canTransitionTo(CompanyStatus.SUSPENDED)).isTrue();
            assertThat(CompanyStatus.ACTIVE.canTransitionTo(CompanyStatus.EXPIRED)).isTrue();
            assertThat(CompanyStatus.SUSPENDED.canTransitionTo(CompanyStatus.ACTIVE)).isTrue();
            assertThat(CompanyStatus.EXPIRED.canTransitionTo(CompanyStatus.ACTIVE)).isTrue();
            assertThat(CompanyStatus.INACTIVE.canTransitionTo(CompanyStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("nothing may return to TRIAL, and a suspended company cannot expire")
        void illegalTransitions() {
            assertThat(CompanyStatus.ACTIVE.canTransitionTo(CompanyStatus.TRIAL)).isFalse();
            assertThat(CompanyStatus.EXPIRED.canTransitionTo(CompanyStatus.TRIAL)).isFalse();
            // A suspended company is already blocked; expiring it would only muddy why.
            assertThat(CompanyStatus.SUSPENDED.canTransitionTo(CompanyStatus.EXPIRED)).isFalse();
            assertThat(CompanyStatus.EXPIRED.canTransitionTo(CompanyStatus.SUSPENDED)).isFalse();
        }

        @Test
        @DisplayName("an illegal transition throws INVALID_STATE_TRANSITION")
        void illegalTransitionThrows() {
            Company subject = company(CompanyStatus.SUSPENDED).build();

            assertThatThrownBy(() -> subject.transitionTo(CompanyStatus.EXPIRED))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cannot move from SUSPENDED to EXPIRED")
                    .extracting(e -> ((BusinessRuleException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("transitioning to the current status is a no-op, not an error")
        void selfTransitionIsIdempotent() {
            Company subject = company(CompanyStatus.SUSPENDED).build();

            subject.transitionTo(CompanyStatus.SUSPENDED);

            assertThat(subject.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        }

        @Test
        @DisplayName("isActive tracks the status and is never set independently")
        void activeFlagFollowsStatus() {
            Company subject = company(CompanyStatus.TRIAL).build();
            subject.applyInvariants();
            assertThat(subject.isActive()).isTrue();

            subject.transitionTo(CompanyStatus.SUSPENDED);
            assertThat(subject.isActive()).isFalse();

            subject.transitionTo(CompanyStatus.ACTIVE);
            assertThat(subject.isActive()).isTrue();

            subject.transitionTo(CompanyStatus.EXPIRED);
            assertThat(subject.isActive()).isFalse();
        }

        @Test
        @DisplayName("only TRIAL and ACTIVE are operational")
        void operationalStatuses() {
            assertThat(CompanyStatus.TRIAL.isOperational()).isTrue();
            assertThat(CompanyStatus.ACTIVE.isOperational()).isTrue();
            assertThat(CompanyStatus.INACTIVE.isOperational()).isFalse();
            assertThat(CompanyStatus.SUSPENDED.isOperational()).isFalse();
            assertThat(CompanyStatus.EXPIRED.isOperational()).isFalse();
        }
    }

    @Nested
    @DisplayName("windows")
    class Windows {

        @Test
        @DisplayName("a null end date never counts as elapsed")
        void nullDatesNeverElapse() {
            Company subject = company(CompanyStatus.ACTIVE).build();

            assertThat(subject.isTrialElapsed(LocalDate.of(2030, 1, 1))).isFalse();
            assertThat(subject.isSubscriptionElapsed(LocalDate.of(2030, 1, 1))).isFalse();
        }

        @Test
        @DisplayName("the last day of a window is still inside it")
        void endDateIsInclusive() {
            LocalDate end = LocalDate.of(2026, 7, 31);
            Company subject = company(CompanyStatus.TRIAL).trialEndDate(end).build();

            assertThat(subject.isTrialElapsed(end)).isFalse();
            assertThat(subject.isTrialElapsed(end.plusDays(1))).isTrue();
        }

        @Test
        @DisplayName("each generated company id is distinct")
        void companyIdsAreUnique() {
            assertThat(Company.newCompanyId()).isNotEqualTo(Company.newCompanyId());
        }
    }
}
