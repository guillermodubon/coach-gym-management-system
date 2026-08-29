package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessRecordJpaEntityTest {

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void createsAllowedAccessRecord() {
        AccessRecordJpaEntity entity = allowedEntity();

        AccessRecordDetails details = entity.toDetails();

        assertThat(details.id()).isNotNull();
        assertThat(details.presentedIdentifier())
                .isEqualTo("MEM-000001");
        assertThat(details.clientId()).isEqualTo(CLIENT_ID);
        assertThat(details.clientCode()).isEqualTo("CLI-000001");
        assertThat(details.membershipId())
                .isEqualTo(MEMBERSHIP_ID);
        assertThat(details.membershipCode())
                .isEqualTo("MEM-000001");
        assertThat(details.result())
                .isEqualTo(AccessResult.ALLOWED);
        assertThat(details.reasonCode())
                .isEqualTo(AccessReasonCode.ACCESS_ALLOWED);
        assertThat(details.checkedInAt()).isEqualTo(NOW);
        assertThat(details.processedByUserId())
                .isEqualTo(ACTOR_ID);
        assertThat(entity.membershipPeriodId())
                .isEqualTo(PERIOD_ID);
    }

    @Test
    void createsDeniedUnresolvedAccessRecord() {
        AccessRecordJpaEntity entity =
                AccessRecordJpaEntity.create(
                        "XYZ-999999",
                        null,
                        null,
                        null,
                        null,
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.IDENTIFIER_NOT_FOUND,
                        "The identifier could not be resolved.",
                        NOW,
                        ACTOR_ID);

        AccessRecordDetails details = entity.toDetails();

        assertThat(details.result())
                .isEqualTo(AccessResult.DENIED);
        assertThat(details.reasonCode())
                .isEqualTo(
                        AccessReasonCode.IDENTIFIER_NOT_FOUND);
        assertThat(details.clientId()).isNull();
        assertThat(details.clientCode()).isNull();
        assertThat(details.membershipId()).isNull();
        assertThat(details.membershipCode()).isNull();
    }

    @Test
    void rejectsAllowedResultWithDenialReason() {
        assertThatThrownBy(() ->
                AccessRecordJpaEntity.create(
                        "MEM-000001",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        PERIOD_ID,
                        AccessResult.ALLOWED,
                        AccessReasonCode.MEMBERSHIP_FROZEN,
                        "Invalid result.",
                        NOW,
                        ACTOR_ID))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("ACCESS_ALLOWED");
    }

    @Test
    void rejectsDeniedResultWithAccessAllowedReason() {
        assertThatThrownBy(() ->
                AccessRecordJpaEntity.create(
                        "MEM-000001",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        PERIOD_ID,
                        AccessResult.DENIED,
                        AccessReasonCode.ACCESS_ALLOWED,
                        "Invalid result.",
                        NOW,
                        ACTOR_ID))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("denial reason");
    }

    @Test
    void rejectsPartialClientSnapshot() {
        assertThatThrownBy(() ->
                AccessRecordJpaEntity.create(
                        "CLI-000001",
                        CLIENT_ID,
                        null,
                        null,
                        null,
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.MEMBERSHIP_NOT_FOUND,
                        "No membership was found.",
                        NOW,
                        ACTOR_ID))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("Client identifier");
    }

    @Test
    void rejectsPartialMembershipSnapshot() {
        assertThatThrownBy(() ->
                AccessRecordJpaEntity.create(
                        "MEM-000001",
                        CLIENT_ID,
                        "CLI-000001",
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.MEMBERSHIP_NOT_STARTED,
                        "Membership has not started.",
                        NOW,
                        ACTOR_ID))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("Membership identifier");
    }

    @Test
    void rejectsMissingActor() {
        assertThatThrownBy(() ->
                AccessRecordJpaEntity.create(
                        "XYZ-999999",
                        null,
                        null,
                        null,
                        null,
                        null,
                        AccessResult.DENIED,
                        AccessReasonCode.IDENTIFIER_NOT_FOUND,
                        "Identifier was not found.",
                        NOW,
                        null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("processing user");
    }

    private static AccessRecordJpaEntity allowedEntity() {
        return AccessRecordJpaEntity.create(
                "MEM-000001",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                PERIOD_ID,
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active and its current period is valid.",
                NOW,
                ACTOR_ID);
    }
}
