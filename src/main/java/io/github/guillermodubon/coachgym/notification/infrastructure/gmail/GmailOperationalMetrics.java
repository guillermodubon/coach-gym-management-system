package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Records bounded Gmail/OAuth outcomes without message or identity data. */
final class GmailOperationalMetrics {

    static final String GMAIL_SENDS = "coachgym.email.gmail.sends";
    static final String GMAIL_SEND_DURATION = "coachgym.email.gmail.send.duration";
    static final String OAUTH_REFRESHES = "coachgym.email.gmail.oauth.refreshes";
    static final String OAUTH_REFRESH_DURATION = "coachgym.email.gmail.oauth.refresh.duration";

    private final MeterRegistry registry;

    GmailOperationalMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Meter registry is required.");
    }

    void recordGmailSend(EmailSendResult result, long startedAtNanos) {
        String outcome = result == null ? "UNEXPECTED" : result.result().name();
        String failureCode = result == null
                ? EmailDeliveryFailureCode.UNEXPECTED_FAILURE.name()
                : result.failureCode() == null ? "NONE" : result.failureCode().name();
        registry.counter(GMAIL_SENDS,
                        "result", outcome,
                        "failure_code", failureCode)
                .increment();
        recordDuration(GMAIL_SEND_DURATION, startedAtNanos,
                "result", outcome, "failure_code", failureCode);
    }

    void recordOAuthRefresh(
            boolean succeeded, GoogleOAuthFailureCode failureCode, long startedAtNanos) {
        String outcome = succeeded ? "SUCCESS" : "FAILED";
        String boundedFailure = succeeded || failureCode == null
                ? "NONE" : failureCode.name();
        registry.counter(OAUTH_REFRESHES,
                        "result", outcome,
                        "failure_code", boundedFailure)
                .increment();
        recordDuration(OAUTH_REFRESH_DURATION, startedAtNanos,
                "result", outcome, "failure_code", boundedFailure);
    }

    private void recordDuration(
            String name, long startedAtNanos, String... tags) {
        long elapsed = Math.max(0L, System.nanoTime() - startedAtNanos);
        Timer.builder(name).tags(tags).register(registry)
                .record(elapsed, TimeUnit.NANOSECONDS);
    }
}
