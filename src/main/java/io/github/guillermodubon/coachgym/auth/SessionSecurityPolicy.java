package io.github.guillermodubon.coachgym.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Applies the absolute lifetime policy to authenticated server-side sessions. */
public class SessionSecurityPolicy {

    public static final String ISSUED_AT_ATTRIBUTE =
            SessionSecurityPolicy.class.getName() + ".issuedAt";

    private final Clock clock;
    private final Duration absoluteTimeout;

    public SessionSecurityPolicy(Clock clock, Duration absoluteTimeout) {
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.absoluteTimeout = Objects.requireNonNull(absoluteTimeout, "Absolute timeout is required.");
    }

    public void markAuthenticated(HttpServletRequest request) {
        request.getSession(true).setAttribute(ISSUED_AT_ATTRIBUTE, clock.instant());
    }

    public void ensureIssuedAt(HttpSession session) {
        if (session.getAttribute(ISSUED_AT_ATTRIBUTE) == null) {
            session.setAttribute(ISSUED_AT_ATTRIBUTE, clock.instant());
        }
    }

    public boolean isExpired(HttpSession session) {
        Object issuedAt = session.getAttribute(ISSUED_AT_ATTRIBUTE);
        if (!(issuedAt instanceof Instant instant)) {
            return false;
        }
        return !clock.instant().isBefore(instant.plus(absoluteTimeout));
    }
}
