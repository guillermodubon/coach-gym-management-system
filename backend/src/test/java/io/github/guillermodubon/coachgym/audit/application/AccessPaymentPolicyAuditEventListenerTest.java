package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicyAuditEventListenerTest {

    @Test
    void forwardsPolicyChangesToTheExistingAuditPort() {
        AuditEntryStore store = mock(AuditEntryStore.class);
        AccessPaymentPolicyAuditEventListener listener =
                new AccessPaymentPolicyAuditEventListener(store);
        AccessPaymentPolicyChanged event = new AccessPaymentPolicyChanged(
                false,
                true,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "policy-admin",
                Instant.parse("2026-09-15T20:00:00Z"));

        listener.record(event);

        verify(store).recordAccessPaymentPolicyChanged(event);
    }
}
