package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class AccessDatabaseConstraintIntegrationTest
        extends AbstractAccessApiIntegrationTest {

    @Test
    void acceptsUnresolvedDeniedAttempt() {
        UUID id =
                insertAccessRow(
                        "XYZ-UNKNOWN",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "DENIED",
                        "IDENTIFIER_NOT_FOUND",
                        Instant.now(),
                        userId(ADMIN_USERNAME));

        assertThat(accessRow(id).get("client_id"))
                .isNull();

        assertThat(accessRow(id).get("membership_id"))
                .isNull();

        assertThat(accessRow(id).get("membership_period_id"))
                .isNull();
    }

    @Test
    void rejectsUnsupportedDecision() {
        assertInvalid(
                "MAYBE",
                "IDENTIFIER_NOT_FOUND",
                "XYZ-1",
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void rejectsUnsupportedReasonCode() {
        assertInvalid(
                "DENIED",
                "WRONG_CODE",
                "XYZ-1",
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void rejectsAllowedWithDenialReasonAndDeniedWithAllowedReason() {
        assertInvalid(
                "ALLOWED",
                "MEMBERSHIP_FROZEN",
                "XYZ-1",
                null,
                null,
                null,
                null,
                null);

        assertInvalid(
                "DENIED",
                "ACCESS_ALLOWED",
                "XYZ-1",
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void rejectsNullAndBlankCode() {
        assertInvalid(
                "DENIED",
                "IDENTIFIER_NOT_FOUND",
                null,
                null,
                null,
                null,
                null,
                null);

        assertInvalid(
                "DENIED",
                "IDENTIFIER_NOT_FOUND",
                "   ",
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void rejectsPartialSnapshotsAndForeignKeys() {
        UUID missingClientId =
                UUID.randomUUID();

        assertInvalid(
                "DENIED",
                "MEMBERSHIP_NOT_FOUND",
                "CLI-1",
                missingClientId,
                "CLI-999999",
                null,
                null,
                null);

        ClientFixture client =
                createClient("ACTIVE");

        assertInvalid(
                "DENIED",
                "MEMBERSHIP_NOT_STARTED",
                "MEM-1",
                client.id(),
                client.code(),
                UUID.randomUUID(),
                "MEM-999999",
                null);
    }

    private void assertInvalid(
            String decision,
            String reasonCode,
            String enteredCode,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID periodId) {

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        INSERT INTO gym.access_records (
                            id,
                            entered_code,
                            client_id,
                            client_code_snapshot,
                            membership_id,
                            membership_code_snapshot,
                            membership_period_id,
                            decision,
                            reason_code,
                            details,
                            occurred_at,
                            recorded_by_user_id
                        )
                        VALUES (
                            CAST(? AS UUID),
                            ?,
                            CAST(? AS UUID),
                            ?,
                            CAST(? AS UUID),
                            ?,
                            CAST(? AS UUID),
                            ?,
                            ?,
                            'Invalid integration row',
                            CAST(? AS TIMESTAMPTZ),
                            CAST(? AS UUID)
                        )
                        """,
                        UUID.randomUUID(),
                        enteredCode,
                        clientId,
                        clientCode,
                        membershipId,
                        membershipCode,
                        periodId,
                        decision,
                        reasonCode,
                        Instant.now(),
                        userId(ADMIN_USERNAME)))
                .isInstanceOf(DataAccessException.class);
    }
}