package io.github.guillermodubon.coachgym.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessRecordResponseTest {

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
            Instant.parse(
                    "2026-09-15T20:00:00Z");

    @Test
    void mapsAllowedRecord() {
        AccessRecordResponse response =
                AccessRecordResponse.from(
                        allowedDetails());

        assertThat(response.id())
                .isEqualTo(RECORD_ID);

        assertThat(response.presentedIdentifier())
                .isEqualTo("MEM-000001");

        assertThat(response.clientId())
                .isEqualTo(CLIENT_ID);

        assertThat(response.clientCode())
                .isEqualTo("CLI-000001");

        assertThat(response.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(response.membershipCode())
                .isEqualTo("MEM-000001");

        assertThat(response.result())
                .isEqualTo(AccessResult.ALLOWED);

        assertThat(response.reasonCode())
                .isEqualTo(
                        AccessReasonCode.ACCESS_ALLOWED);

        assertThat(response.reason())
                .isEqualTo(
                        "Membership is active and its current period is valid.");

        assertThat(response.checkedInAt())
                .isEqualTo(NOW);

        assertThat(response.processedByUserId())
                .isEqualTo(ACTOR_ID);
    }

    @Test
    void mapsResolvedDeniedRecord() {
        AccessRecordDetails details =
                new AccessRecordDetails(
                        RECORD_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        AccessResult.DENIED,
                        AccessReasonCode.MEMBERSHIP_FROZEN,
                        "The membership is currently frozen.",
                        NOW,
                        ACTOR_ID);

        AccessRecordResponse response =
                AccessRecordResponse.from(details);

        assertThat(response.result())
                .isEqualTo(AccessResult.DENIED);

        assertThat(response.reasonCode())
                .isEqualTo(
                        AccessReasonCode.MEMBERSHIP_FROZEN);

        assertThat(response.clientId())
                .isEqualTo(CLIENT_ID);

        assertThat(response.membershipId())
                .isEqualTo(MEMBERSHIP_ID);
    }

    @Test
    void mapsUnresolvedDeniedRecord() {
        AccessRecordDetails details =
                new AccessRecordDetails(
                        RECORD_ID,
                        "XYZ-999999",
                        null,
                        null,
                        null,
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.IDENTIFIER_NOT_FOUND,
                        "The presented identifier could not be resolved.",
                        NOW,
                        ACTOR_ID);

        AccessRecordResponse response =
                AccessRecordResponse.from(details);

        assertThat(response.result())
                .isEqualTo(AccessResult.DENIED);

        assertThat(response.reasonCode())
                .isEqualTo(
                        AccessReasonCode.IDENTIFIER_NOT_FOUND);

        assertThat(response.clientId()).isNull();
        assertThat(response.clientCode()).isNull();
        assertThat(response.membershipId()).isNull();
        assertThat(response.membershipCode()).isNull();

        assertThat(response.processedByUserId())
                .isEqualTo(ACTOR_ID);
    }

    @Test
    void supportsNullProcessingUserAfterUserDeletion() {
        AccessRecordDetails details =
                new AccessRecordDetails(
                        RECORD_ID,
                        "XYZ-999999",
                        null,
                        null,
                        null,
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.IDENTIFIER_NOT_FOUND,
                        "The presented identifier could not be resolved.",
                        NOW,
                        null);

        AccessRecordResponse response =
                AccessRecordResponse.from(details);

        assertThat(response.processedByUserId())
                .isNull();
    }

    @Test
    void rejectsNullDetails() {
        assertThatThrownBy(() ->
                AccessRecordResponse.from(null))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Access record details must be provided.");
    }

    private static AccessRecordDetails allowedDetails() {
        return new AccessRecordDetails(
                RECORD_ID,
                "MEM-000001",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active and its current period is valid.",
                NOW,
                ACTOR_ID);
    }
}
