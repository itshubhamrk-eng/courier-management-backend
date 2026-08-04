package com.courier.modules.auth.application;

import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private PasswordPolicy policy;
    private AuthProperties properties;

    /** Stands in for the BCrypt encoder: "matches" iff the hash is "hash:" + raw. */
    private final BiPredicate<String, String> encoder =
            (raw, hash) -> hash != null && hash.equals("hash:" + raw);

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        policy = new PasswordPolicy(properties);
        policy.loadCommonPasswords();
    }

    @Test
    @DisplayName("accepts a password that satisfies every rule")
    void acceptsGoodPassword() {
        assertThatCode(() -> policy.validate("Tr0ubador-Horse-42", "ops@acme.test", null, encoder))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a password shorter than the minimum")
    void rejectsShort() {
        assertThatThrownBy(() -> policy.validate("Ab3xyz", "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 10 characters");
    }

    @Test
    @DisplayName("rejects a password longer than BCrypt's effective limit")
    void rejectsOverlyLong() {
        String tooLong = "a1" + "x".repeat(100);

        // Silently truncating at 72 bytes would leave the user believing a long
        // passphrase was fully protecting them.
        assertThatThrownBy(() -> policy.validate(tooLong, "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 72 characters");
    }

    @Test
    @DisplayName("requires a digit")
    void requiresDigit() {
        assertThatThrownBy(() -> policy.validate("abcdefghijkl", "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one digit");
    }

    @Test
    @DisplayName("requires a letter")
    void requiresLetter() {
        assertThatThrownBy(() -> policy.validate("1234567890123", "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one letter");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123", "welcome123", "letmein123"})
    @DisplayName("rejects the passwords credential-stuffing actually uses")
    void rejectsCommonPasswords(String common) {
        // These satisfy length, letter and digit, so the common-list check is the
        // only thing that can reject them — which is what this test is asserting.
        assertThatThrownBy(() -> policy.validate(common, "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too common");
    }

    /**
     * Rules are evaluated cheapest-and-most-actionable first: length, then
     * composition, then the common-password list. A password can break several at
     * once, and the user is told about the first — telling someone their 8-character
     * password is "too common" when it is also too short is unhelpful.
     */
    @ParameterizedTest
    @ValueSource(strings = {"qwerty123", "admin123", "password"})
    @DisplayName("a short common password is reported as short, not as common")
    void shortCommonPasswordsFailOnLength(String common) {
        assertThatThrownBy(() -> policy.validate(common, "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least 10 characters");
    }

    @Test
    @DisplayName("a long common password missing a digit is reported as missing a digit")
    void commonPasswordFailingCompositionReportsComposition() {
        // "qwertyuiop" is on the common list and is long enough, but has no digit.
        assertThatThrownBy(() -> policy.validate("qwertyuiop", "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one digit");

        // "1234567890" is on the list and long enough, but has no letter.
        assertThatThrownBy(() -> policy.validate("1234567890", "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one letter");
    }

    @Test
    @DisplayName("rejects a password built from the email local part")
    void rejectsEmailDerived() {
        // johnsmith@... -> "johnsmith2024" is exactly what people pick.
        assertThatThrownBy(() -> policy.validate("johnsmith2024", "john.smith@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not contain your email");
    }

    @Test
    @DisplayName("a short email local part does not trigger the email rule")
    void shortLocalPartIgnored() {
        // "ab" would otherwise match almost any password.
        assertThatCode(() -> policy.validate("Tr0ubador-Horse-42", "ab@acme.test", null, encoder))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects reusing the current password")
    void rejectsReuse() {
        assertThatThrownBy(() -> policy.validate(
                "Tr0ubador-Horse-42", "ops@acme.test", "hash:Tr0ubador-Horse-42", encoder))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different from the current one");
    }

    @Test
    @DisplayName("accepts a new password that differs from the current one")
    void acceptsDifferentPassword() {
        assertThatCode(() -> policy.validate(
                "Brand-New-Passw0rd", "ops@acme.test", "hash:Tr0ubador-Horse-42", encoder))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects blank and null")
    void rejectsBlank() {
        assertThatThrownBy(() -> policy.validate("   ", "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> policy.validate(null, "ops@acme.test", null, encoder))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("every rejection carries WEAK_PASSWORD so clients can branch on it")
    void usesWeakPasswordCode() {
        BusinessRuleException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessRuleException.class,
                () -> policy.validate("short", "ops@acme.test", null, encoder));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.WEAK_PASSWORD);
        assertThat(thrown.getErrorCode().getStatus().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("optional rules apply only when switched on")
    void optionalRules() {
        properties.getPassword().setRequireMixedCase(true);
        properties.getPassword().setRequireSpecial(true);

        assertThatThrownBy(() -> policy.validate("alllowercase1", "ops@acme.test", null, encoder))
                .hasMessageContaining("upper and lower case");

        assertThatThrownBy(() -> policy.validate("MixedCase123", "ops@acme.test", null, encoder))
                .hasMessageContaining("special character");

        assertThatCode(() -> policy.validate("MixedCase123!", "ops@acme.test", null, encoder))
                .doesNotThrowAnyException();
    }
}
