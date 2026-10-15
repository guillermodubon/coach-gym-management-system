package io.github.guillermodubon.coachgym.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class SessionSecurityPolicyTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-18T12:00:00Z");

    @Test
    void recordsAuthenticationTimeAndExpiresAtTheAbsoluteBoundary() {
        Clock clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        SessionSecurityPolicy policy = new SessionSecurityPolicy(clock, Duration.ofHours(8));
        MockHttpServletRequest request = new MockHttpServletRequest();

        policy.markAuthenticated(request);
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        assertThat(session.getAttribute(SessionSecurityPolicy.ISSUED_AT_ATTRIBUTE))
                .isEqualTo(ISSUED_AT);
        assertThat(policy.isExpired(session)).isFalse();

        SessionSecurityPolicy expiredPolicy = new SessionSecurityPolicy(
                Clock.fixed(ISSUED_AT.plus(Duration.ofHours(8)), ZoneOffset.UTC),
                Duration.ofHours(8));
        assertThat(expiredPolicy.isExpired(session)).isTrue();
    }

    @Test
    void addsAnIssuedAtValueForLegacyAuthenticatedSessionsWithoutOne() {
        Clock clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        SessionSecurityPolicy policy = new SessionSecurityPolicy(clock, Duration.ofHours(8));
        MockHttpSession session = new MockHttpSession();

        policy.ensureIssuedAt(session);

        assertThat(session.getAttribute(SessionSecurityPolicy.ISSUED_AT_ATTRIBUTE))
                .isEqualTo(ISSUED_AT);
    }
}
