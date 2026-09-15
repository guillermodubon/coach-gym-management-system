package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records bounded operational counters for access attempts.
 *
 * <p>Only the finite source, result, and duplicate dimensions are used. No
 * access record, client, credential, actor, payload, or token value is ever
 * used as a metric tag.</p>
 */
@Component
public class AccessAttemptMetricsListener {

    private static final String ACCESS_ATTEMPTS_METRIC =
            "coachgym.access.attempts";

    private final MeterRegistry meterRegistry;

    public AccessAttemptMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void record(AccessAttemptRecorded event) {
        meterRegistry.counter(
                        ACCESS_ATTEMPTS_METRIC,
                        "source", boundedSource(event.presentedIdentifierType()),
                        "result", event.result().name(),
                        "duplicate", Boolean.toString(event.duplicate()))
                .increment();
    }

    private static String boundedSource(String source) {
        return switch (source) {
            case "CLIENT_CODE", "MEMBERSHIP_CODE", "QR_CREDENTIAL", "UNKNOWN" ->
                    source;
            default -> "OTHER";
        };
    }
}
