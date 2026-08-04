package com.courier.modules.company.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanySettingsTest {

    @Test
    @DisplayName("defaultsFor seeds identity from the company and field defaults for the rest")
    void defaults() {
        UUID company = UUID.randomUUID();
        CompanySettings s = CompanySettings.defaultsFor(new CompanySettings.UUIDHolder(
                company, "Legacy Co", "Legacy", "ops@legacy.test", "+91 900", "https://legacy.test",
                null, null, null, "India", "MH", "Pune", "LEGACY"));

        // Seeded from the company.
        assertThat(s.getCompanyId()).isEqualTo(company);
        assertThat(s.getCompanyName()).isEqualTo("Legacy Co");
        assertThat(s.getSupportEmail()).isEqualTo("ops@legacy.test");
        assertThat(s.getCity()).isEqualTo("Pune");
        assertThat(s.getAwbPrefix()).isEqualTo("LEGACY");
        assertThat(s.getInvoicePrefix()).isEqualTo("LEGACY");

        // Blank localisation falls back to platform defaults.
        assertThat(s.getLanguage()).isEqualTo("en");
        assertThat(s.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(s.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("field defaults are sensible and typed")
    void fieldDefaults() {
        CompanySettings s = CompanySettings.builder().build();

        assertThat(s.getGstPercentage()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(s.getWeightUnit()).isEqualTo(WeightUnit.KG);
        assertThat(s.getDimensionUnit()).isEqualTo(DimensionUnit.CM);
        assertThat(s.getTheme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(s.getAwbRunningNumber()).isEqualTo(1L);
        assertThat(s.isCodEnabled()).isTrue();
        assertThat(s.isEmailEnabled()).isTrue();
        assertThat(s.isWalletEnabled()).isFalse();
        assertThat(s.getSessionTimeoutMinutes()).isEqualTo(30);
        assertThat(s.getPrimaryColor()).isEqualTo("#1976D2");
    }
}
