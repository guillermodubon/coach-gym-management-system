package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StripeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StripeConfiguration.class);

    @Test
    void disabledStripeStartsWithoutSecretsAndWithoutProviderBeans() {
        contextRunner
                .withPropertyValues("coach-gym.payment.stripe.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StripeProperties.class);
                    assertThat(context).doesNotHaveBean(StripeSdkClient.class);
                    assertThat(context).doesNotHaveBean(StripeCheckoutGateway.class);
                    assertThat(context).doesNotHaveBean(StripeWebhookVerifier.class);
                });
    }

    @Test
    void enabledStripeFailsFastWhenRequiredConfigurationIsMissing() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.payment.stripe.enabled=true",
                        "coach-gym.payment.stripe.sandbox=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
