package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports Stripe Test Mode configuration readiness without provider calls. */
@Component("stripe")
class StripeHealthIndicator implements HealthIndicator {

    private final StripeProperties properties;

    StripeHealthIndicator(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        return properties.isValidWhenEnabled()
                ? Health.up().withDetail("enabled", true).withDetail("mode", "test").build()
                : Health.down().withDetail("enabled", true)
                        .withDetail("reason", "configuration_invalid").build();
    }
}
