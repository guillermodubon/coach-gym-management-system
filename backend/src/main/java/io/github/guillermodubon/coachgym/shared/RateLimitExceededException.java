package io.github.guillermodubon.coachgym.shared;

import org.springframework.security.core.AuthenticationException;

/** Safe authentication failure used when a local failed-request budget is exhausted. */
public final class RateLimitExceededException extends AuthenticationException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Authentication rate limit exceeded.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
