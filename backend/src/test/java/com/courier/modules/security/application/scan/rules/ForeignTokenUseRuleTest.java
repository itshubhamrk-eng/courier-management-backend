package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.security.application.CountryLookup;
import com.courier.modules.security.application.GeoCountryService;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.domain.IncidentRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForeignTokenUseRuleTest {

    @Mock RefreshTokenRepository refreshTokenRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID session = UUID.randomUUID();

    /** Real GeoCountryService over a stub lookup: 8.8.x is US, 49.36.x is IN, anything else unknown. */
    private static final CountryLookup STUB = address -> {
        String ip = address.getHostAddress();
        if (ip.startsWith("8.8.")) return Optional.of("us");
        if (ip.startsWith("49.36.")) return Optional.of("IN");
        return Optional.empty();
    };

    private ForeignTokenUseRule rule(boolean geoEnabled) {
        SecurityProperties props = new SecurityProperties();
        props.getGeo().setEnabled(geoEnabled);
        return new ForeignTokenUseRule(refreshTokenRepository, new GeoCountryService(props, STUB), props);
    }

    private SessionRefreshEvent ev(UUID u, UUID s, String ip) {
        return new SessionRefreshEvent(company, u, s, "Mozilla/5.0", ip);
    }

    @Test
    void codeIsForeignTokenUseWhichAutoBans() {
        assertThat(rule(true).code()).isEqualTo(IncidentRule.FOREIGN_TOKEN_USE);
        assertThat(IncidentRule.FOREIGN_TOKEN_USE.autoBan()).isTrue();
    }

    @Test
    void foreignRefreshIpRaisesOneRequestForTheUser() {
        UUID otherSession = UUID.randomUUID();
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(
                ev(user, session, "49.36.10.1"),
                ev(user, session, "8.8.8.8"),
                ev(user, otherSession, "8.8.4.4")));

        List<IncidentRequest> out = rule(true).scan(from, to);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.rule()).isEqualTo(IncidentRule.FOREIGN_TOKEN_USE);
            assertThat(r.companyId()).isEqualTo(company);
            assertThat(r.userId()).isEqualTo(user);
            assertThat(r.evidence())
                    .containsEntry("foreignIpCount", 2)
                    .containsEntry("foreignIps", List.of("8.8.8.8", "8.8.4.4"))
                    .containsEntry("countries", List.of("US"))
                    .containsEntry("sessionIds", List.of(session.toString(), otherSession.toString()))
                    .containsEntry("windowFrom", from.toString())
                    .containsEntry("windowTo", to.toString());
        });
    }

    @Test
    void allowedAndUnknownIpsRaiseNothing() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of(
                ev(user, session, "49.36.10.1"),   // ALLOWED (IN)
                ev(user, session, "1.1.1.1"),      // UNKNOWN (lookup empty)
                ev(user, session, "10.0.0.5"),     // UNKNOWN (private)
                ev(user, session, "not-an-ip"),    // UNKNOWN (garbage, never throws)
                ev(user, session, null)));

        assertThat(rule(true).scan(from, to)).isEmpty();
    }

    @Test
    void geoDisabledRaisesNothingAndReadsNothing() {
        assertThat(rule(false).scan(from, to)).isEmpty();
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void noRowsRaiseNothing() {
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(List.of());

        assertThat(rule(true).scan(from, to)).isEmpty();
    }

    @Test
    void evidenceIsBoundedAndUsersAreSeparate() {
        UUID other = UUID.randomUUID();
        List<SessionRefreshEvent> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            rows.add(ev(user, UUID.randomUUID(), "8.8.8." + i));
        }
        rows.add(ev(other, UUID.randomUUID(), "49.36.1.1"));
        when(refreshTokenRepository.findSessionRefreshEvents(from, to)).thenReturn(rows);

        List<IncidentRequest> out = rule(true).scan(from, to);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.userId()).isEqualTo(user);
            Map<String, Object> e = r.evidence();
            assertThat(e).containsEntry("foreignIpCount", 7);
            assertThat((List<?>) e.get("foreignIps")).hasSize(5);
            assertThat((List<?>) e.get("sessionIds")).hasSize(5);
        });
    }
}
