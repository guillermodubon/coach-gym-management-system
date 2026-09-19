package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.security.session")
public record SessionProperties(Duration absoluteTimeout) {

    public SessionProperties {
        absoluteTimeout = absoluteTimeout == null
                ? Duration.ofHours(8)
                : absoluteTimeout;
    }

    @AssertTrue(message = "the absolute session timeout must be positive and at most 24 hours")
    public boolean isValid() {
        return !absoluteTimeout.isZero()
                && !absoluteTimeout.isNegative()
                && absoluteTimeout.compareTo(Duration.ofHours(24)) <= 0;
    }
}
