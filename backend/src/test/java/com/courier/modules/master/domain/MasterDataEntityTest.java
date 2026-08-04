package com.courier.modules.master.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The head every master row shares: normalisation, required fields, status transitions. */
class MasterDataEntityTest {

    @Nested
    @DisplayName("code normalisation")
    class Codes {

        @Test
        @DisplayName("a code is uppercased and spaces become underscores")
        void normalises() {
            // Otherwise "pune main", "Pune Main" and "PUNE_MAIN" become three rows a
            // human reads as one.
            assertThat(MasterDataEntity.normaliseCode(" pune main ")).isEqualTo("PUNE_MAIN");
            assertThat(MasterDataEntity.normaliseCode("TRUCK")).isEqualTo("TRUCK");
            assertThat(MasterDataEntity.normaliseCode(null)).isNull();
        }

        @Test
        @DisplayName("applyInvariants normalises the code and trims the name")
        void appliedOnSave() {
            Country country = country("  in dia ", "  India  ");
            country.applyInvariants();

            assertThat(country.getCode()).isEqualTo("IN_DIA");
            assertThat(country.getName()).isEqualTo("India");
        }

        @Test
        @DisplayName("a blank description becomes null rather than an empty string")
        void blankDescriptionIsNull() {
            // So "no description" has one representation in the database, not two.
            Country country = country("IN", "India");
            country.setDescription("   ");
            country.applyInvariants();

            assertThat(country.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("required fields")
    class Required {

        @Test
        @DisplayName("a missing code is refused")
        void codeRequired() {
            assertThatThrownBy(() -> country(null, "India").applyInvariants())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("code is required");
        }

        @Test
        @DisplayName("a blank name is refused")
        void nameRequired() {
            assertThatThrownBy(() -> country("IN", "   ").applyInvariants())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("a null status defaults to ACTIVE and a null display order to zero")
        void defaults() {
            Country country = country("IN", "India");
            country.setStatus(null);
            country.setDisplayOrder(null);
            country.applyInvariants();

            assertThat(country.getStatus()).isEqualTo(MasterStatus.ACTIVE);
            assertThat(country.getDisplayOrder()).isZero();
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("activate and deactivate flip the status and nothing else")
        void transitions() {
            Country country = country("IN", "India");
            assertThat(country.isActive()).isTrue();

            country.deactivate();
            assertThat(country.isActive()).isFalse();
            assertThat(country.getStatus()).isEqualTo(MasterStatus.INACTIVE);

            country.activate();
            assertThat(country.isActive()).isTrue();
        }
    }

    private static Country country(String code, String name) {
        Country country = new Country();
        country.setCode(code);
        country.setName(name);
        return country;
    }
}
