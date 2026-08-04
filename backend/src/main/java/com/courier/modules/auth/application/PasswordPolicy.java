package com.courier.modules.auth.application;

import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Enforces the password rules described in {@code MEMORY/modules/auth.md}.
 *
 * <p>Every rejection throws with a message the user can act on. Vague errors such as
 * "password not strong enough" push people towards `Password1!` — telling them
 * exactly which rule failed produces better passwords, and reveals nothing an
 * attacker does not already know from published policy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    private static final String COMMON_PASSWORD_RESOURCE = "security/common-passwords.txt";

    private final AuthProperties properties;
    private Set<String> commonPasswords = Set.of();

    @PostConstruct
    void loadCommonPasswords() {
        if (!properties.getPassword().isRejectCommonPasswords()) {
            return;
        }
        Set<String> loaded = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(COMMON_PASSWORD_RESOURCE);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    loaded.add(trimmed);
                }
            }
        } catch (IOException e) {
            // Fail fast: silently running without the list would weaken every
            // password set from this point on, invisibly.
            throw new IllegalStateException(
                    "Cannot read " + COMMON_PASSWORD_RESOURCE + "; password policy would be weakened", e);
        }
        this.commonPasswords = Set.copyOf(loaded);
        log.info("Password policy loaded {} common passwords to reject", commonPasswords.size());
    }

    /**
     * @param rawPassword     the candidate
     * @param email           used to reject passwords derived from the address
     * @param currentHash     current BCrypt hash, or null when there is none yet
     * @param encoderMatches  callback into the encoder, so this class stays free of
     *                        Spring Security types and trivially unit-testable
     * @throws BusinessRuleException with {@link ErrorCode#WEAK_PASSWORD} on any failure
     */
    public void validate(String rawPassword,
                         String email,
                         String currentHash,
                         java.util.function.BiPredicate<String, String> encoderMatches) {

        AuthProperties.PasswordPolicyProperties policy = properties.getPassword();

        if (rawPassword == null || rawPassword.isBlank()) {
            throw weak("Password must not be blank");
        }
        if (rawPassword.length() < policy.getMinLength()) {
            throw weak("Password must be at least %d characters".formatted(policy.getMinLength()));
        }
        if (rawPassword.length() > policy.getMaxLength()) {
            throw weak("Password must be at most %d characters".formatted(policy.getMaxLength()));
        }
        if (policy.isRequireLetter() && rawPassword.chars().noneMatch(Character::isLetter)) {
            throw weak("Password must contain at least one letter");
        }
        if (policy.isRequireDigit() && rawPassword.chars().noneMatch(Character::isDigit)) {
            throw weak("Password must contain at least one digit");
        }
        if (policy.isRequireMixedCase()
                && (rawPassword.chars().noneMatch(Character::isUpperCase)
                || rawPassword.chars().noneMatch(Character::isLowerCase))) {
            throw weak("Password must contain both upper and lower case letters");
        }
        if (policy.isRequireSpecial()
                && rawPassword.chars().allMatch(Character::isLetterOrDigit)) {
            throw weak("Password must contain at least one special character");
        }
        if (policy.isRejectCommonPasswords() && commonPasswords.contains(rawPassword.toLowerCase())) {
            throw weak("This password is too common; choose something less predictable");
        }
        if (containsEmailLocalPart(rawPassword, email)) {
            throw weak("Password must not contain your email address");
        }
        if (currentHash != null && encoderMatches != null
                && encoderMatches.test(rawPassword, currentHash)) {
            throw weak("New password must be different from the current one");
        }
    }

    /**
     * Rejects `johnsmith2024` for `john.smith@acme.test`. Compares against the local
     * part with separators stripped, since that is how people actually build these.
     */
    private boolean containsEmailLocalPart(String rawPassword, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String normalised = localPart.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (normalised.length() < 4) {
            return false;
        }
        return rawPassword.toLowerCase().contains(normalised);
    }

    private BusinessRuleException weak(String message) {
        return new BusinessRuleException(ErrorCode.WEAK_PASSWORD, message);
    }
}
