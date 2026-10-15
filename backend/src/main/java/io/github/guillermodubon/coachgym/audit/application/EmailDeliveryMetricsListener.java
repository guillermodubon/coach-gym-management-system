package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records bounded email-delivery attempt counters for operational visibility.
 *
 * <p>Dimensions are finite enums and a retry boolean. No recipient, client,
 * delivery, source, subject, provider, or attachment identifier is used as a
 * metric tag.</p>
 */
@Component
public class EmailDeliveryMetricsListener {

    private static final String EMAIL_ATTEMPTS_METRIC =
            "coachgym.email.delivery.attempts";

    private final MeterRegistry meterRegistry;

    public EmailDeliveryMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void record(EmailDeliveryLifecycleEvent event) {
        meterRegistry.counter(
                        EMAIL_ATTEMPTS_METRIC,
                        "type", event.deliveryType().name(),
                        "result", event.attemptResult().name(),
                        "retry", Boolean.toString(event.retry()),
                        "failure_code", boundedFailureCode(event))
                .increment();
    }

    private static String boundedFailureCode(EmailDeliveryLifecycleEvent event) {
        return event.failureCode() == null ? "NONE" : event.failureCode().name();
    }
}
