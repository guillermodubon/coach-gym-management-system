package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Bounded Stripe webhook counters without provider identifiers or payload data. */
@Component
final class StripeWebhookMetrics {

    private static final String METRIC = "coachgym.stripe.webhook";

    private final MeterRegistry meterRegistry;

    StripeWebhookMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    void record(PaymentProviderEventType eventType, PaymentProviderEventProcessingResult result) {
        meterRegistry.counter(
                        METRIC,
                        "event_type", eventType == null ? "UNKNOWN" : eventType.name(),
                        "result", result == null ? "UNKNOWN" : result.name(),
                        "failure_code", "NONE")
                .increment();
    }

    void recordRejected(PaymentProviderFailureCode failureCode) {
        meterRegistry.counter(
                        METRIC,
                        "event_type", "UNKNOWN",
                        "result", "REJECTED",
                        "failure_code", failureCode == null ? "UNKNOWN" : failureCode.name())
                .increment();
    }
}
