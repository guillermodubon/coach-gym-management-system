package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Ephemeral OAuth bearer token; never persist or include it in diagnostics. */
record OAuthAccessToken(String value, Instant expiresAt) {

    OAuthAccessToken {
        Objects.requireNonNull(value, "Access token is required.");
        Objects.requireNonNull(expiresAt, "Access-token expiration is required.");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Access token is required.");
        }
    }

    boolean usableAt(Instant now, Duration safetyMargin) {
        return expiresAt.isAfter(now.plus(safetyMargin));
    }

    @Override
    public String toString() {
        return "OAuthAccessToken[valuePresent=" + !value.isBlank()
                + ", expiresAt=" + expiresAt + ']';
    }
}
