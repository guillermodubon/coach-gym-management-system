package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "coach-gym.security.request")
public record RequestBodyLimitProperties(
        int maxJsonBodyBytes,
        int maxFormBodyBytes,
        int maxMultipartBodyBytes) {

    public RequestBodyLimitProperties {
        maxJsonBodyBytes = maxJsonBodyBytes == 0 ? 1_048_576 : maxJsonBodyBytes;
        maxFormBodyBytes = maxFormBodyBytes == 0 ? 65_536 : maxFormBodyBytes;
        maxMultipartBodyBytes = maxMultipartBodyBytes == 0 ? 8 * 1_048_576 : maxMultipartBodyBytes;
    }

    @AssertTrue(message = "request body limits must be positive and bounded")
    public boolean isValid() {
        return bounded(maxJsonBodyBytes, 1_024, 8 * 1_048_576)
                && bounded(maxFormBodyBytes, 1_024, 1_048_576)
                && bounded(maxMultipartBodyBytes, 1_024, 16 * 1_048_576)
                && maxJsonBodyBytes <= maxMultipartBodyBytes;
    }

    private static boolean bounded(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }
}
