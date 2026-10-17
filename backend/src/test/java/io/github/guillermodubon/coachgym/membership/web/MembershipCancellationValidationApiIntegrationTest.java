package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembershipCancellationValidationApiIntegrationTest
        extends AbstractMembershipCancellationApiIntegrationTest {

    @Test
    void shouldRejectStaleMembershipVersion()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long currentVersion =
                membershipVersion(
                        membershipId);

        cancelMembership(
                membershipId,
                currentVersion + 1,
                adminSession)
                .andExpect(
                        status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VERSION_CONFLICT"));

        assertActiveAndUnchanged(
                membershipId,
                currentVersion);
    }

    @Test
    void shouldRejectAlreadyCancelledMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        assertThat(
                currentPeriodStartsOn(
                        membershipId))
                .isEqualTo(
                        CANCELLATION_PERIOD_START);

        long activeVersion =
                membershipVersion(
                        membershipId);

        cancelMembership(
                membershipId,
                activeVersion,
                adminSession)
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED"));

        long cancelledVersion =
                membershipVersion(
                        membershipId);

        cancelMembership(
                membershipId,
                cancelledVersion,
                adminSession)
                .andExpect(
                        status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_ALREADY_CANCELLED"));

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("CANCELLED");

        assertThat(
                membershipVersion(
                        membershipId))
                .isEqualTo(
                        cancelledVersion);

        assertThat(
                cancellationStatusHistoryCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                cancellationAuditCount(
                        membershipId))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectExpiredMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        assertThat(
                currentPeriodStartsOn(
                        membershipId))
                .isEqualTo(
                        CANCELLATION_PERIOD_START);

        markMembershipExpired(
                membershipId);

        long expiredVersion =
                membershipVersion(
                        membershipId);

        cancelMembership(
                membershipId,
                expiredVersion,
                adminSession)
                .andExpect(
                        status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_CANCELLATION_STATE_CONFLICT"));

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("EXPIRED");

        assertThat(
                membershipVersion(
                        membershipId))
                .isEqualTo(
                        expiredVersion);

        assertThat(
                membershipHasCancelledAt(
                        membershipId))
                .isFalse();

        assertThat(
                membershipCancellationReason(
                        membershipId))
                .isNull();

        assertThat(
                cancellationStatusHistoryCount(
                        membershipId))
                .isZero();

        assertThat(
                cancellationAuditCount(
                        membershipId))
                .isZero();
    }

    @Test
    void shouldRejectUnknownMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID unknownMembershipId =
                UUID.fromString(
                        "90000000-0000-0000-0000-000000000001");

        cancelMembership(
                unknownMembershipId,
                0L,
                adminSession)
                .andExpect(
                        status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_NOT_FOUND"));

        assertThat(
                cancellationAuditCount(
                        unknownMembershipId))
                .isZero();
    }

    @Test
    void shouldRejectMissingCancellationDate()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                """
                {
                  "cancelledOn": null,
                  "reason": "Client requested cancellation",
                  "version": %d
                }
                """
                        .formatted(version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectMissingReason()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                """
                {
                  "cancelledOn": "2026-09-15",
                  "reason": null,
                  "version": %d
                }
                """
                        .formatted(version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectBlankReason()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                cancellationBody(
                        CANCELLATION_DATE,
                        "   ",
                        version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectReasonLongerThanMaximum()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                cancellationBody(
                        CANCELLATION_DATE,
                        "a".repeat(2_001),
                        version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectMissingVersion()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                """
                {
                  "cancelledOn": "2026-09-15",
                  "reason": "Client requested cancellation",
                  "version": null
                }
                """;

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectNegativeVersion()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                cancellationBody(
                        CANCELLATION_DATE,
                        CANCELLATION_REASON,
                        -1L);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectFutureCancellationDate()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                cancellationBody(
                        CANCELLATION_DATE.plusDays(1),
                        CANCELLATION_REASON,
                        version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    @Test
    void shouldRejectCancellationBeforePeriodStart()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                cancellationBody(
                        CANCELLATION_PERIOD_START.minusDays(1),
                        CANCELLATION_REASON,
                        version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VALIDATION_FAILED"));

        assertActiveAndUnchanged(
                membershipId,
                version);
    }

    private void assertActiveAndUnchanged(
            UUID membershipId,
            long originalVersion) {

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("ACTIVE");

        assertThat(
                membershipVersion(
                        membershipId))
                .isEqualTo(
                        originalVersion);

        assertThat(
                membershipHasCancelledAt(
                        membershipId))
                .isFalse();

        assertThat(
                membershipCancellationReason(
                        membershipId))
                .isNull();

        assertThat(
                cancellationStatusHistoryCount(
                        membershipId))
                .isZero();

        assertThat(
                cancellationAuditCount(
                        membershipId))
                .isZero();
    }
}
