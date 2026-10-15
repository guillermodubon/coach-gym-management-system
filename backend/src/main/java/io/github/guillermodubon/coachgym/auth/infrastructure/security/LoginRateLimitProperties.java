package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.security.login-rate-limit")
public record LoginRateLimitProperties(
        int maxAttempts,
        Duration window,
        int maxTrackedIdentifiers) {

    public static final int DEFAULT_MAX_TRACKED_IDENTIFIERS = 10_000;
    private static final int MAX_TRACKED_IDENTIFIERS = 100_000;

    public LoginRateLimitProperties {
        window = window == null ? Duration.ofMinutes(1) : window;
    }

    @AssertTrue(message = "the login rate limit must have bounded attempts, window, and tracked identifiers")
    public boolean isValid() {
        return maxAttempts >= 1
                && maxAttempts <= 100
                && !window.isZero()
                && !window.isNegative()
                && window.compareTo(Duration.ofHours(1)) <= 0
                && maxTrackedIdentifiers >= 1
                && maxTrackedIdentifiers <= MAX_TRACKED_IDENTIFIERS;
    }
}
