package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records finite-dimension outcomes for canonical receipts and credentials. */
@Component
public class ReceiptAndCredentialMetricsListener {

    private final MeterRegistry meterRegistry;

    public ReceiptAndCredentialMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void recordReceipt(PaymentReceiptGenerated event) {
        meterRegistry.counter(
                        "coachgym.receipt.generation",
                        "outcome", "SUCCESS",
                        "test_mode", Boolean.toString(event.testMode()))
                .increment();
    }

    @EventListener
    public void recordIssued(AccessCredentialIssued event) {
        recordCredential("ISSUED");
    }

    @EventListener
    public void recordRevoked(AccessCredentialRevoked event) {
        recordCredential("REVOKED");
    }

    @EventListener
    public void recordReplaced(AccessCredentialReplaced event) {
        recordCredential("REPLACED");
    }

    private void recordCredential(String lifecycle) {
        meterRegistry.counter(
                        "coachgym.access.credential.lifecycle",
                        "event", lifecycle)
                .increment();
    }
}
