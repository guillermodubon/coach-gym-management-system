package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimitPropertiesTest {

    @Test
    void suppliesBoundedDefaultsForOmittedValues() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties(
                10,
                null,
                LoginRateLimitProperties.DEFAULT_MAX_TRACKED_IDENTIFIERS);

        assertThat(properties.maxAttempts()).isEqualTo(10);
        assertThat(properties.window()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.maxTrackedIdentifiers())
                .isEqualTo(LoginRateLimitProperties.DEFAULT_MAX_TRACKED_IDENTIFIERS);
        assertThat(properties.isValid()).isTrue();
    }

    @Test
    void acceptsConfiguredIdentifierCapacityOnlyWithinSafeBounds() {
        assertThat(new LoginRateLimitProperties(10, Duration.ofMinutes(1), 1).isValid())
                .isTrue();
        assertThat(new LoginRateLimitProperties(10, Duration.ofMinutes(1), 100_000).isValid())
                .isTrue();
        assertThat(new LoginRateLimitProperties(10, Duration.ofMinutes(1), -1).isValid())
                .isFalse();
        assertThat(new LoginRateLimitProperties(10, Duration.ofMinutes(1), 0).isValid())
                .isFalse();
        assertThat(new LoginRateLimitProperties(10, Duration.ofMinutes(1), 100_001).isValid())
                .isFalse();
    }

    @Test
    void rejectsInvalidAttemptBoundsAndDurations() {
        assertThat(new LoginRateLimitProperties(0, Duration.ofMinutes(1), 10_000).isValid())
                .isFalse();
        assertThat(new LoginRateLimitProperties(-1, Duration.ofMinutes(1), 10_000).isValid())
                .isFalse();
        assertThat(new LoginRateLimitProperties(10, Duration.ZERO, 10_000).isValid())
                .isFalse();
        assertThat(new LoginRateLimitProperties(
                10, Duration.ofSeconds(-1), 10_000).isValid())
                .isFalse();
        assertThat(new LoginRateLimitProperties(
                10, Duration.ofHours(1).plusNanos(1), 10_000).isValid())
                .isFalse();
    }
}
