package io.github.guillermodubon.coachgym.audit.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Bounded operational counters for audit export outcomes. */
@Component
public class AuditExportMetrics {

    private final MeterRegistry meterRegistry;

    public AuditExportMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    public void recordSuccess() {
        record("SUCCESS");
    }

    public void recordFailure() {
        record("FAILURE");
    }

    private void record(String outcome) {
        meterRegistry.counter("coachgym.audit.export", "outcome", outcome).increment();
    }
}
