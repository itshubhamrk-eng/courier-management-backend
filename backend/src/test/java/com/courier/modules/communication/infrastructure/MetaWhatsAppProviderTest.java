package com.courier.modules.communication.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaWhatsAppProviderTest {

    @Test
    void bareTenDigitNumberGetsDefaultCountryCode() {
        assertThat(MetaWhatsAppProvider.normalizeRecipient("9123456780", "91")).isEqualTo("919123456780");
    }

    @Test
    void leadingTrunkZeroIsDroppedBeforePrefixing() {
        assertThat(MetaWhatsAppProvider.normalizeRecipient("09123456780", "91")).isEqualTo("919123456780");
    }

    @Test
    void numberAlreadyCarryingCountryCodeIsLeftAlone() {
        assertThat(MetaWhatsAppProvider.normalizeRecipient("+91 91234 56780", "91")).isEqualTo("919123456780");
        assertThat(MetaWhatsAppProvider.normalizeRecipient("00919123456780", "91")).isEqualTo("919123456780");
    }

    @Test
    void nullOrBlankYieldsJustTheCountryCode() {
        assertThat(MetaWhatsAppProvider.normalizeRecipient(null, "91")).isEqualTo("91");
    }
}
