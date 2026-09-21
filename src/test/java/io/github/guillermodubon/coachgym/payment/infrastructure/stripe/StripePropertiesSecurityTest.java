package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StripePropertiesSecurityTest {

    @Test
    void doesNotExposeProviderSecretsInDiagnosticText() {
        StripeProperties properties = new StripeProperties(
                true,
                true,
                "sk_test_very-secret",
                "whsec_very-secret",
                "https://frontend.example/success",
                "https://frontend.example/cancel",
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofMinutes(5),
                262_144,
                1_024);

        assertThat(properties.toString())
                .doesNotContain("sk_test_very-secret", "whsec_very-secret")
                .contains("secretKeyPresent=true", "webhookSigningSecretPresent=true");
    }
}
