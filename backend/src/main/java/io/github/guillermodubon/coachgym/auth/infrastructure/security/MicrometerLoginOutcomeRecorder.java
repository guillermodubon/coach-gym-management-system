package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.application.LoginOutcomeRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Emits only a fixed login outcome tag; identifiers and credentials are never recorded. */
@Component
final class MicrometerLoginOutcomeRecorder implements LoginOutcomeRecorder {

    private static final String METRIC = "coachgym.authentication.login.attempts";

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    MicrometerLoginOutcomeRecorder(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void record(Outcome outcome) {
        Outcome safeOutcome = Objects.requireNonNull(outcome);
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(METRIC, "outcome", safeOutcome.name()).increment();
        }
    }
}
