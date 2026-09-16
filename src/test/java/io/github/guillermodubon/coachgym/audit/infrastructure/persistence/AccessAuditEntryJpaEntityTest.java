package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessAuditEntryJpaEntityTest {

    private static final UUID RECORD_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final UUID CREDENTIAL_ID =
            UUID.fromString(
                    "60000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void mapsDeniedAccessEvent() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        deniedEvent());

        assertThat(entry.id()).isNotNull();

        assertThat(entry.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(entry.actorIdentifierSnapshot())
                .isEqualTo("receptionist");

        assertThat(entry.actionCode())
                .isEqualTo("ACCESS_DENIED");

        assertThat(entry.resourceType())
                .isEqualTo("ACCESS_RECORD");

        assertThat(entry.resourceId())
                .isEqualTo(RECORD_ID);

        assertThat(entry.resourceCodeSnapshot())
                .isEqualTo("MEM-000001");

        assertThat(entry.summary())
                .isEqualTo("Gym access denied.");

        assertThat(entry.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void storesApprovedMinimalMetadata() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        deniedEvent());

        assertThat(entry.metadata())
                .containsEntry(
                        "presentedIdentifierType",
                        "MEMBERSHIP_CODE")
                .containsEntry(
                        "result",
                        "DENIED")
                .containsEntry(
                        "reasonCode",
                        "MEMBERSHIP_FROZEN")
                .containsEntry(
                        "checkedInAt",
                        NOW.toString())
                .containsEntry(
                        "clientId",
                        CLIENT_ID.toString())
                .containsEntry(
                        "membershipId",
                        MEMBERSHIP_ID.toString());

        assertThat(entry.metadata().keySet())
                .noneMatch(key -> {
                    String normalized =
                            key.toLowerCase();

                    return normalized.contains("email")
                            || normalized.contains("phone")
                            || normalized.contains("password")
                            || normalized.contains("cookie")
                            || normalized.contains("token")
                            || normalized.contains("session")
                            || normalized.contains("birth");
                });
    }

    @Test
    void mapsQrDuplicateWithSafeAllowlistedMetadata() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(qrDuplicateEvent());

        assertThat(entry.resourceCodeSnapshot())
                .isEqualTo("QR_CREDENTIAL");
        assertThat(entry.metadata())
                .containsEntry("identificationSource", "QR_CREDENTIAL")
                .containsEntry("result", "DENIED")
                .containsEntry("reasonCode", "DUPLICATE_CHECK_IN")
                .containsEntry("duplicate", true)
                .containsEntry("accessCredentialId", CREDENTIAL_ID.toString());
        assertThat(entry.metadata().toString())
                .doesNotContain("payload", "token", "fingerprint", "email", "phone");
    }

    @Test
    void metadataIsImmutable() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        deniedEvent());

        assertThatThrownBy(() ->
                entry.metadata().put(
                        "unexpected",
                        "value"))
                .isInstanceOf(
                        UnsupportedOperationException.class);
    }

    @Test
    void supportsUnresolvedIdentifier() {
        AccessAttemptRecorded event =
                new AccessAttemptRecorded(
                        RECORD_ID,
                        "XYZ-999999",
                        "UNKNOWN",
                        null,
                        null,
                        null,
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.IDENTIFIER_NOT_FOUND,
                        NOW,
                        ACTOR_ID,
                        "receptionist",
                        NOW);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        assertThat(entry.metadata())
                .doesNotContainKeys(
                        "clientId",
                        "membershipId");

        assertThat(entry.resourceCodeSnapshot())
                .isEqualTo("XYZ-999999");
    }

    @Test
    void supportsPaymentRequiredWithoutPersistingFinancialDetails() {
        AccessAttemptRecorded event = new AccessAttemptRecorded(
                RECORD_ID,
                "MEM-000001",
                "MEMBERSHIP_CODE",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                AccessResult.DENIED,
                AccessReasonCode.PAYMENT_REQUIRED,
                NOW,
                ACTOR_ID,
                "receptionist",
                NOW);

        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(event);

        assertThat(entry.actionCode()).isEqualTo("ACCESS_DENIED");
        assertThat(entry.metadata())
                .containsEntry("reasonCode", "PAYMENT_REQUIRED")
                .containsEntry("result", "DENIED");
        assertThat(entry.metadata().toString())
                .doesNotContain("amount", "currency", "method", "provider",
                        "externalReference", "stripe", "card");
    }

    @Test
    void rejectsAllowedAttempt() {
        AccessAttemptRecorded event =
                new AccessAttemptRecorded(
                        RECORD_ID,
                        "MEM-000001",
                        "MEMBERSHIP_CODE",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        AccessResult.ALLOWED,
                        AccessReasonCode.ACCESS_ALLOWED,
                        NOW,
                        ACTOR_ID,
                        "receptionist",
                        NOW);

        assertThatThrownBy(() ->
                AuditEntryJpaEntity.from(event))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessageContaining(
                        "Only denied access attempts");
    }

    private static AccessAttemptRecorded deniedEvent() {
        return new AccessAttemptRecorded(
                RECORD_ID,
                "MEM-000001",
                "MEMBERSHIP_CODE",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                AccessResult.DENIED,
                AccessReasonCode.MEMBERSHIP_FROZEN,
                NOW,
                ACTOR_ID,
                "receptionist",
                NOW);
    }

    private static AccessAttemptRecorded qrDuplicateEvent() {
        return new AccessAttemptRecorded(
                RECORD_ID,
                "QR_CREDENTIAL",
                "QR_CREDENTIAL",
                CREDENTIAL_ID,
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                AccessResult.DENIED,
                AccessReasonCode.DUPLICATE_CHECK_IN,
                NOW,
                ACTOR_ID,
                "receptionist",
                NOW);
    }
}
