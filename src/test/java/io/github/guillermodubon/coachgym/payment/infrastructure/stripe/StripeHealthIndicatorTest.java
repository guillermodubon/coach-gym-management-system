package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StripeHealthIndicatorTest {

    @Test
    void disabledStripeIsReadyWithoutProviderCalls() {
        StripeHealthIndicator indicator = new StripeHealthIndicator(properties(false, false));

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void nonTestConfigurationIsNotReadyWhenStripeIsEnabled() {
        StripeHealthIndicator indicator = new StripeHealthIndicator(properties(true, false));

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    private static StripeProperties properties(boolean enabled, boolean sandbox) {
        return new StripeProperties(
                enabled, sandbox, "sk_test_key", "whsec_key",
                "http://localhost/success", "http://localhost/cancel",
                Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofMinutes(5),
                262_144, 1_024);
    }
}
