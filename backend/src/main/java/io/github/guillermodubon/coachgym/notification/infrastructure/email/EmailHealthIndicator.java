package io.github.guillermodubon.coachgym.notification.infrastructure.email;

import io.github.guillermodubon.coachgym.notification.infrastructure.gmail.GmailConfigurationReadiness;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports configuration-only Gmail readiness without contacting the provider. */
@Component("email")
class EmailHealthIndicator implements HealthIndicator {

    private final EmailProperties emailProperties;
    private final GmailConfigurationReadiness gmailReadiness;

    EmailHealthIndicator(
            EmailProperties emailProperties,
            GmailConfigurationReadiness gmailReadiness) {
        this.emailProperties = emailProperties;
        this.gmailReadiness = gmailReadiness;
    }

    @Override
    public Health health() {
        if (!emailProperties.enabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("configuration", "disabled")
                    .build();
        }
        boolean ready = emailProperties.isValidWhenEnabled()
                && gmailReadiness.status(true, emailProperties.fromAddress())
                        == GmailConfigurationReadiness.Status.READY;
        return ready
                ? Health.up().withDetail("enabled", true)
                        .withDetail("configuration", "ready").build()
                : Health.down().withDetail("enabled", true)
                        .withDetail("configuration", "invalid")
                        .withDetail("reason", "configuration_invalid").build();
    }
}
