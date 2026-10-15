package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.security.headers")
public record SecurityHeadersProperties(boolean hstsEnabled, Duration hstsMaxAge) {

    public SecurityHeadersProperties {
        hstsMaxAge = hstsMaxAge == null
                ? Duration.ofDays(365)
                : hstsMaxAge;
    }

    @AssertTrue(message = "the HSTS max age must be positive and at most two years")
    public boolean isValid() {
        return !hstsMaxAge.isZero()
                && !hstsMaxAge.isNegative()
                && hstsMaxAge.compareTo(Duration.ofDays(730)) <= 0;
    }
}
