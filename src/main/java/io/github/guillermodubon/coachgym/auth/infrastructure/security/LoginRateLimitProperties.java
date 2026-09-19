package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.security.login-rate-limit")
public record LoginRateLimitProperties(int maxAttempts, Duration window) {

    public LoginRateLimitProperties {
        maxAttempts = maxAttempts == 0 ? 10 : maxAttempts;
        window = window == null ? Duration.ofMinutes(1) : window;
    }

    @AssertTrue(message = "the login rate limit must allow between 1 and 100 attempts per positive bounded window")
    public boolean isValid() {
        return maxAttempts >= 1
                && maxAttempts <= 100
                && !window.isZero()
                && !window.isNegative()
                && window.compareTo(Duration.ofHours(1)) <= 0;
    }
}
