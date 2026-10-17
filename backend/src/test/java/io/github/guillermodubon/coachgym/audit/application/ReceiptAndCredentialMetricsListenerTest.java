package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceiptAndCredentialMetricsListenerTest {

    @Test
    void recordsFiniteReceiptAndCredentialDimensionsOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReceiptAndCredentialMetricsListener listener =
                new ReceiptAndCredentialMetricsListener(registry);
        UUID receiptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-18T12:00:00Z");

        listener.recordReceipt(new PaymentReceiptGenerated(
                receiptId, "REC-0001", paymentId, "PAY-0001", PaymentStatus.PAID,
                BigDecimal.TEN, "USD", actorId, "admin", true, now));
        listener.recordIssued(new AccessCredentialIssued(
                credentialId, clientId, "AC-0001", "v1", "sha256-v1", actorId,
                "admin", now));

        assertThat(registry.get("coachgym.receipt.generation")
                .tag("outcome", "SUCCESS")
                .tag("test_mode", "true")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("coachgym.access.credential.lifecycle")
                .tag("event", "ISSUED")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue())
                                .doesNotContain(receiptId.toString(), paymentId.toString(),
                                        actorId.toString(), credentialId.toString(), clientId.toString())
                                .doesNotContain("admin")));
    }
}
