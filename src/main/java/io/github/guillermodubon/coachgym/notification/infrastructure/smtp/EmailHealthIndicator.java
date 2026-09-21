package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import io.github.guillermodubon.coachgym.notification.infrastructure.resend.ResendProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports bounded email configuration readiness without contacting a provider. */
@Component("email")
class EmailHealthIndicator implements HealthIndicator {

    private final EmailProperties emailProperties;
    private final ResendProperties resendProperties;

    EmailHealthIndicator(
            EmailProperties emailProperties,
            ResendProperties resendProperties) {
        this.emailProperties = emailProperties;
        this.resendProperties = resendProperties;
    }

    @Override
    public Health health() {
        if (!emailProperties.enabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("provider", emailProperties.provider())
                    .build();
        }
        boolean valid = emailProperties.isValidWhenEnabled()
                && (!"resend".equals(emailProperties.provider())
                    || resendProperties.isValidWhenEnabled());
        return valid
                ? Health.up().withDetail("enabled", true)
                        .withDetail("provider", emailProperties.provider()).build()
                : Health.down().withDetail("enabled", true)
                        .withDetail("reason", "configuration_invalid").build();
    }
}
