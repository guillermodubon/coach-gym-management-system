package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryAuditEventListenerTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T12:00:00Z");

    @Mock
    private AuditEntryStore auditEntryStore;

    private EmailDeliveryAuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailDeliveryAuditEventListener(auditEntryStore);
    }

    @Test
    void forwardsFinalizedLifecycleEventToAuditStore() {
        EmailDeliveryLifecycleEvent event = new EmailDeliveryLifecycleEvent(
                DELIVERY_ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "a***@example.com",
                EmailDeliveryStatus.SENT,
                1,
                null,
                ACTOR_ID,
                "coach-admin",
                OCCURRED_AT);

        listener.record(event);

        verify(auditEntryStore).recordEmailDeliveryLifecycle(event);
    }

    @Test
    void preservesRetryAndFailureSemanticsInThePublicEvent() {
        EmailDeliveryLifecycleEvent event = new EmailDeliveryLifecycleEvent(
                DELIVERY_ID,
                EmailDeliveryType.ACCESS_CREDENTIAL,
                SOURCE_ID,
                CLIENT_ID,
                "a***@example.com",
                EmailDeliveryStatus.FAILED,
                2,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                ACTOR_ID,
                "coach-admin",
                OCCURRED_AT);

        listener.record(event);

        verify(auditEntryStore).recordEmailDeliveryLifecycle(event);
    }
}
