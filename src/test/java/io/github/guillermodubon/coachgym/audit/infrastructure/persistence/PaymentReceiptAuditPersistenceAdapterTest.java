package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptAuditPersistenceAdapterTest {

    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000a01");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000a02");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000a03");
    private static final Instant GENERATED_AT =
            Instant.parse("2026-09-12T17:00:00Z");

    @Mock
    private AuditEntryJpaRepository repository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void delegatesReceiptAuditToExistingJpaRepository() {
        AuditEntryPersistenceAdapter adapter = new AuditEntryPersistenceAdapter(
                repository, jdbcTemplate);
        PaymentReceiptGenerated event = new PaymentReceiptGenerated(
                RECEIPT_ID,
                "REC-000A01",
                PAYMENT_ID,
                "PAY-000A02",
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                "USD",
                ACTOR_ID,
                "coach-admin",
                false,
                GENERATED_AT);

        adapter.recordPaymentReceiptGenerated(event);

        ArgumentCaptor<AuditEntryJpaEntity> captor =
                ArgumentCaptor.forClass(AuditEntryJpaEntity.class);
        verify(repository).save(captor.capture());
        AuditEntryJpaEntity entry = captor.getValue();
        assertThat(field(entry, "actionCode"))
                .isEqualTo("PAYMENT_RECEIPT_GENERATED");
        assertThat(field(entry, "resourceType"))
                .isEqualTo("PAYMENT_RECEIPT");
        assertThat(field(entry, "resourceId"))
                .isEqualTo(RECEIPT_ID);
        assertThat(field(entry, "resourceCodeSnapshot"))
                .isEqualTo("REC-000A01");
        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);
        assertThat(field(entry, "actorIdentifierSnapshot"))
                .isEqualTo("coach-admin");
        assertThat(metadata(entry)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "paymentId", PAYMENT_ID.toString(),
                "paymentCode", "PAY-000A02",
                "amount", "25.00",
                "currency", "USD",
                "testMode", false));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String name) {
        return ReflectionTestUtils.getField(entry, name);
    }
}
