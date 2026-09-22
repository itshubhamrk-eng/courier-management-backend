package com.courier.shared.useragent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @Test
    void parsesChromeOnWindows() {
        var parsed = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36");
        assertThat(parsed.device()).isEqualTo("DESKTOP");
        assertThat(parsed.browser()).isEqualTo("Chrome");
        assertThat(parsed.os()).isEqualTo("Windows");
    }

    @Test
    void parsesSafariOnMac() {
        var parsed = UserAgentParser.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15");
        assertThat(parsed.browser()).isEqualTo("Safari");
        assertThat(parsed.os()).isEqualTo("macOS");
    }

    @Test
    void parsesMobileAndroid() {
        var parsed = UserAgentParser.parse(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36");
        assertThat(parsed.device()).isEqualTo("MOBILE");
        assertThat(parsed.os()).isEqualTo("Android");
    }

    @Test
    void edgeIsNotMisreadAsChrome() {
        var parsed = UserAgentParser.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0 Safari/537.36 Edg/128.0");
        assertThat(parsed.browser()).isEqualTo("Edge");
    }

    @Test
    void blankUserAgentYieldsUnknown() {
        var parsed = UserAgentParser.parse(null);
        assertThat(parsed.device()).isEqualTo("UNKNOWN");
        assertThat(parsed.browser()).isEqualTo("Unknown");
        assertThat(parsed.os()).isEqualTo("Unknown");
    }

    @Test
    void apiClientsAreClassified() {
        var parsed = UserAgentParser.parse("okhttp/4.9.0");
        assertThat(parsed.device()).isEqualTo("API_CLIENT");
        assertThat(parsed.browser()).isEqualTo("API Client");
    }
}
