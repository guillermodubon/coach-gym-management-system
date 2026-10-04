package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.application.CardCheckoutGateway;
import io.github.guillermodubon.coachgym.payment.application.CheckoutRedirectPolicy;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookLimits;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookVerifier;
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
                    assertThat(context).doesNotHaveBean(CardCheckoutGateway.class);
                    assertThat(context).doesNotHaveBean(CheckoutRedirectPolicy.class);
                    assertThat(context).doesNotHaveBean(StripeWebhookVerifier.class);
                    assertThat(context).doesNotHaveBean(PaymentProviderWebhookVerifier.class);
                    assertThat(context).hasSingleBean(PaymentProviderWebhookLimits.class);
                });
    }

    @Test
    void enabledValidStripeRegistersEachRequiredProviderBoundaryExactlyOnce() {
        contextRunner
                .withPropertyValues(
                        "coach-gym.payment.stripe.enabled=true",
                        "coach-gym.payment.stripe.sandbox=true",
                        "coach-gym.payment.stripe.secret-key=sk_test_configuration-only",
                        "coach-gym.payment.stripe.webhook-signing-secret=whsec_configuration-only",
                        "coach-gym.payment.stripe.success-url=http://localhost:8080/stripe/success",
                        "coach-gym.payment.stripe.cancel-url=http://localhost:8080/stripe/cancel",
                        "coach-gym.payment.stripe.connect-timeout=PT5S",
                        "coach-gym.payment.stripe.read-timeout=PT15S",
                        "coach-gym.payment.stripe.webhook-tolerance=PT5M",
                        "coach-gym.payment.stripe.max-webhook-payload-bytes=262144",
                        "coach-gym.payment.stripe.max-signature-header-length=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StripeProperties.class);
                    assertThat(context).hasSingleBean(StripeSdkClient.class);
                    assertThat(context).hasSingleBean(StripeCheckoutGateway.class);
                    assertThat(context).hasSingleBean(CardCheckoutGateway.class);
                    assertThat(context).hasSingleBean(CheckoutRedirectPolicy.class);
                    assertThat(context).hasSingleBean(StripeWebhookVerifier.class);
                    assertThat(context).hasSingleBean(PaymentProviderWebhookVerifier.class);
                    assertThat(context).hasSingleBean(PaymentProviderWebhookLimits.class);
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
