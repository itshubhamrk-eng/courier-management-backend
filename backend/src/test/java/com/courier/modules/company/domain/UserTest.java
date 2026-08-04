package com.courier.modules.company.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static User.UserBuilder user() {
        return User.builder()
                .email("  ASHA@Legacy.test ")
                .username("  Asha.Nair ")
                .employeeCode("  emp001 ")
                .firstName("  Asha ")
                .lastName(" Nair ")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE);
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("email and username are lowercased, employee code uppercased, names trimmed")
        void normalises() {
            User u = user().build();
            u.applyInvariants();

            assertThat(u.getEmail()).isEqualTo("asha@legacy.test");
            assertThat(u.getUsername()).isEqualTo("asha.nair");
            assertThat(u.getEmployeeCode()).isEqualTo("EMP001");
            assertThat(u.getFirstName()).isEqualTo("Asha");
            assertThat(u.getLastName()).isEqualTo("Nair");
        }

        @Test
        @DisplayName("blank username and employee code normalise to null, so unique keys ignore them")
        void blankIdentifiersBecomeNull() {
            User u = user().username("   ").employeeCode("").build();
            u.applyInvariants();

            assertThat(u.getUsername()).isNull();
            assertThat(u.getEmployeeCode()).isNull();
        }

        @Test
        @DisplayName("a user with no email is rejected")
        void requiresEmail() {
            User u = user().email("  ").build();
            assertThatThrownBy(u::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("gender defaults to UNSPECIFIED")
        void genderDefault() {
            User u = user().gender(null).build();
            u.applyInvariants();
            assertThat(u.getGender()).isEqualTo(Gender.UNSPECIFIED);
        }
    }

    @Nested
    @DisplayName("names")
    class Names {

        @Test
        @DisplayName("full name joins the parts, display name falls back to it")
        void names() {
            User u = user().middleName("K").build();
            u.applyInvariants();

            assertThat(u.fullName()).isEqualTo("Asha K Nair");
            assertThat(u.effectiveDisplayName()).isEqualTo("Asha K Nair");
        }

        @Test
        @DisplayName("display name is used when set")
        void displayNameWins() {
            User u = user().displayName("Ashu").build();
            u.applyInvariants();
            assertThat(u.effectiveDisplayName()).isEqualTo("Ashu");
        }

        @Test
        @DisplayName("with no name parts, full name falls back to the email")
        void nameFallsBackToEmail() {
            User u = user().firstName(null).lastName(null).build();
            u.applyInvariants();
            assertThat(u.fullName()).isEqualTo("asha@legacy.test");
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("activate clears any lock and sets ACTIVE")
        void activate() {
            User u = user().status(UserStatus.DISABLED).build();
            u.activate();
            assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(u.isLocked()).isFalse();
            assertThat(u.isOperational()).isTrue();
        }

        @Test
        @DisplayName("deactivate disables without touching the lock flag")
        void deactivate() {
            User u = user().build();
            u.deactivate();
            assertThat(u.getStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(u.isOperational()).isFalse();
        }

        @Test
        @DisplayName("lock sets LOCKED and the flag; a locked user is not operational")
        void lock() {
            User u = user().build();
            u.lock();
            assertThat(u.getStatus()).isEqualTo(UserStatus.LOCKED);
            assertThat(u.isLocked()).isTrue();
            assertThat(u.isOperational()).isFalse();
        }

        @Test
        @DisplayName("unlock clears the lock, resets the failed counter, and reactivates")
        void unlock() {
            User u = user().build();
            u.setFailedLoginCount(4);
            u.lock();

            u.unlock();

            assertThat(u.isLocked()).isFalse();
            assertThat(u.getFailedLoginCount()).isZero();
            assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("only ACTIVE and unlocked counts as operational, mirroring auth's login gate")
        void operationalMirrorsAuth() {
            assertThat(UserStatus.ACTIVE.operational()).isTrue();
            assertThat(UserStatus.PENDING.operational()).isFalse();
            assertThat(UserStatus.LOCKED.operational()).isFalse();
            assertThat(UserStatus.DISABLED.operational()).isFalse();

            User active = user().build();
            active.setLocked(true);
            // ACTIVE but hard-locked is still not operational.
            assertThat(active.isOperational()).isFalse();
        }
    }

    @Nested
    @DisplayName("invariants")
    class Invariants {

        @Test
        @DisplayName("a user cannot report to themselves")
        void noSelfManager() {
            User u = user().build();
            u.setReportingManagerId(u.getId());
            assertThatThrownBy(u::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("report to themselves");
        }

        @Test
        @DisplayName("branch and hub assignment set the single columns")
        void placement() {
            User u = user().build();
            UUID branch = UUID.randomUUID();
            UUID hub = UUID.randomUUID();

            u.assignBranch(branch);
            u.assignHub(hub);

            assertThat(u.getBranchId()).isEqualTo(branch);
            assertThat(u.getHubId()).isEqualTo(hub);
        }

        @Test
        @DisplayName("soft delete flags the row without discarding it")
        void softDelete() {
            User u = user().build();
            UUID actor = UUID.randomUUID();
            u.softDelete(actor);
            assertThat(u.isDeleted()).isTrue();
            assertThat(u.getDeletedBy()).isEqualTo(actor);
        }
    }
}
