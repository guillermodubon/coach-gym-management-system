package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChangeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromotionAuditEventListenerTest {

    @Mock
    private AuditEntryStore auditEntryStore;

    @Test
    void delegatesPromotionEventToAuditStore() {
        PromotionAuditEventListener listener =
                new PromotionAuditEventListener(
                        auditEntryStore);

        PromotionChanged event =
                new PromotionChanged(
                        UUID.fromString(
                                "32f9291d-2263-4099-aac4-a51958ce82c0"),
                        "PROMO-000001",
                        PromotionChangeType.CREATED,
                        UUID.fromString(
                                "2d903ea1-b5e6-4506-b72e-7ab905ec5fa6"),
                        "coach-admin",
                        Instant.parse(
                                "2026-08-15T04:00:00Z"));

        listener.record(event);

        verify(auditEntryStore)
                .recordPromotionChanged(event);
    }
}
