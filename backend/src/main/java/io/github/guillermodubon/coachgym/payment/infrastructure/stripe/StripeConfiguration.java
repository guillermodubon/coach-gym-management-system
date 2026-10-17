package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

import io.github.guillermodubon.coachgym.payment.application.CheckoutRedirectPolicy;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookLimits;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StripeProperties.class)
class StripeConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "coach-gym.payment.stripe", name = "enabled", havingValue = "true")
    StripeSdkClient stripeSdkClient(StripeProperties properties) {
        return new OfficialStripeSdkClient(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "coach-gym.payment.stripe", name = "enabled", havingValue = "true")
    StripeCheckoutGateway stripeCheckoutGateway(StripeSdkClient client, StripeProperties properties) {
        return new StripeCheckoutGateway(client, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "coach-gym.payment.stripe", name = "enabled", havingValue = "true")
    CheckoutRedirectPolicy stripeCheckoutRedirectPolicy(StripeProperties properties) {
        return new StripeCheckoutRedirectPolicy(properties);
    }

    @Bean
    PaymentProviderWebhookLimits stripeWebhookRequestLimits(StripeProperties properties) {
        if (properties.maxWebhookPayloadBytes() < 1
                || properties.maxSignatureHeaderLength() < 1) {
            return PaymentProviderWebhookLimits.defaults();
        }
        return new PaymentProviderWebhookLimits(
                properties.maxWebhookPayloadBytes(), properties.maxSignatureHeaderLength());
    }

    @Bean
    @ConditionalOnProperty(prefix = "coach-gym.payment.stripe", name = "enabled", havingValue = "true")
    StripeWebhookVerifier stripeWebhookVerifier(StripeSdkClient client, StripeProperties properties) {
        return new StripeWebhookVerifier(client, properties);
    }
}
