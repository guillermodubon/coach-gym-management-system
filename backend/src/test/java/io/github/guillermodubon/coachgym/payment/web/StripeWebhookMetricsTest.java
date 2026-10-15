package io.github.guillermodubon.coachgym.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class StripeWebhookMetricsTest {

    @Test
    void recordsOnlyFiniteWebhookDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StripeWebhookMetrics metrics = new StripeWebhookMetrics(registry);

        metrics.record(
                PaymentProviderEventType.CHECKOUT_COMPLETED,
                PaymentProviderEventProcessingResult.PROCESSED);
        metrics.recordRejected(PaymentProviderFailureCode.INVALID_SIGNATURE);

        assertThat(registry.get("coachgym.stripe.webhook")
                .tag("event_type", "CHECKOUT_COMPLETED")
                .tag("result", "PROCESSED")
                .tag("failure_code", "NONE")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("coachgym.stripe.webhook")
                .tag("event_type", "UNKNOWN")
                .tag("result", "REJECTED")
                .tag("failure_code", "INVALID_SIGNATURE")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue()).doesNotContain("stripe_", "evt_", "pi_")));
    }
}
