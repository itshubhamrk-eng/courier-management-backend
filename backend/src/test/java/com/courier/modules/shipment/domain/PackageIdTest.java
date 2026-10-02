package com.courier.modules.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackageIdTest {

    @Test
    void parsesAwbAndPackageNumber() {
        assertThat(PackageId.parse("AWB10001-002")).contains(new PackageId("AWB10001", 2));
        assertThat(PackageId.parse(" 26100000036-012 ")).contains(new PackageId("26100000036", 12));
    }

    @Test
    void plainAwbOrMalformedIsNotAPackageId() {
        assertThat(PackageId.parse("AWB10001")).isEmpty();
        assertThat(PackageId.parse("AWB10001-2")).isEmpty();
        assertThat(PackageId.parse("AWB10001-000")).isEmpty();
        assertThat(PackageId.parse(null)).isEmpty();
    }
}
