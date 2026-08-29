package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessAttemptRecordedTest {

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

    private static final Instant NOW =
            Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void createsAllowedEvent() {
        AccessAttemptRecorded event =
                allowedEvent();

        assertThat(event.denied()).isFalse();

        assertThat(event.reasonCode())
                .isEqualTo(
                        AccessReasonCode.ACCESS_ALLOWED);
    }

    @Test
    void createsDeniedEventWithoutResolvedReferences() {
        AccessAttemptRecorded event =
                deniedEvent();

        assertThat(event.denied()).isTrue();
        assertThat(event.clientId()).isNull();
        assertThat(event.clientCode()).isNull();
        assertThat(event.membershipId()).isNull();
        assertThat(event.membershipCode()).isNull();
    }

    @Test
    void rejectsAllowedEventWithDenialReason() {
        assertThatThrownBy(() ->
                new AccessAttemptRecorded(
                        RECORD_ID,
                        "MEM-000001",
                        "MEMBERSHIP_CODE",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        AccessResult.ALLOWED,
                        AccessReasonCode.MEMBERSHIP_FROZEN,
                        NOW,
                        ACTOR_ID,
                        "receptionist",
                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCESS_ALLOWED");
    }

    @Test
    void rejectsDeniedEventWithAllowedReason() {
        assertThatThrownBy(() ->
                new AccessAttemptRecorded(
                        RECORD_ID,
                        "MEM-000001",
                        "MEMBERSHIP_CODE",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        AccessResult.DENIED,
                        AccessReasonCode.ACCESS_ALLOWED,
                        NOW,
                        ACTOR_ID,
                        "receptionist",
                        NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("denial reason");
    }

    @Test
    void exposesNoSensitiveRecordComponents() {
        assertThat(Arrays.stream(
                        AccessAttemptRecorded.class
                                .getRecordComponents())
                .map(component ->
                        component.getName()
                                .toLowerCase())
                .toList())
                .noneMatch(name ->
                        name.contains("email")
                                || name.contains("phone")
                                || name.contains("password")
                                || name.contains("cookie")
                                || name.contains("token")
                                || name.contains("session")
                                || name.contains("birth"));
    }

    private static AccessAttemptRecorded allowedEvent() {
        return new AccessAttemptRecorded(
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
    }

    private static AccessAttemptRecorded deniedEvent() {
        return new AccessAttemptRecorded(
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
    }
}
