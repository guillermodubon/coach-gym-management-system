package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembershipCancellationApiIntegrationTest
        extends AbstractMembershipCancellationApiIntegrationTest {

    @Test
    void adminShouldCancelActiveMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long originalVersion =
                membershipVersion(
                        membershipId);

        int originalPeriodCount =
                membershipPeriodCount(
                        membershipId);

        cancelMembership(
                membershipId,
                originalVersion,
                adminSession)
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        membershipId.toString()))
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED"))
                .andExpect(
                        jsonPath("$.version")
                                .value(
                                        originalVersion + 1));

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("CANCELLED");

        assertThat(
                membershipVersion(
                        membershipId))
                .isEqualTo(
                        originalVersion + 1);

        assertThat(
                membershipPeriodCount(
                        membershipId))
                .isEqualTo(
                        originalPeriodCount);

        assertThat(
                membershipHasCancelledAt(
                        membershipId))
                .isTrue();

        assertThat(
                membershipCancelledByUserId(
                        membershipId))
                .isNotNull();

        assertThat(
                membershipCancellationReason(
                        membershipId))
                .isEqualTo(
                        CANCELLATION_REASON);

        assertThat(
                cancellationStatusHistoryCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                cancellationPreviousStatus(
                        membershipId))
                .isEqualTo("ACTIVE");

        assertThat(
                cancellationAuditCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                cancellationAuditActor(
                        membershipId))
                .isEqualTo("coach-admin");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "cancelledOn"))
                .isEqualTo(
                        CANCELLATION_DATE.toString());

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "reason"))
                .isEqualTo(
                        CANCELLATION_REASON);

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "previousStatus"))
                .isEqualTo("ACTIVE");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "resultingStatus"))
                .isEqualTo("CANCELLED");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "statusChanged"))
                .isEqualTo("true");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "closedOpenFreeze"))
                .isEqualTo("false");

        assertThat(
                openFreezeCount(
                        membershipId))
                .isZero();

        assertThat(
                cancellationClosedFreezeCount(
                        membershipId))
                .isZero();
    }

    @Test
    void adminShouldCancelFrozenMembershipAndCloseFreeze()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long activeVersion =
                membershipVersion(
                        membershipId);

        freezeMembership(
                adminSession,
                membershipId,
                CANCELLATION_DATE.toString(),
                "2026-09-20",
                "Medical leave",
                activeVersion)
                .andExpect(
                        status().isOk());

        assertThat(
                membershipStatus(
                        membershipId))
                .isEqualTo("FROZEN");

        assertThat(
                openFreezeCount(
                        membershipId))
                .isEqualTo(1);

        long frozenVersion =
                membershipVersion(
                        membershipId);

        int originalPeriodCount =
                membershipPeriodCount(
                        membershipId);

        cancelMembership(
                membershipId,
                frozenVersion,
                adminSession)
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED"))
                .andExpect(
                        jsonPath("$.version")
                                .value(
                                        frozenVersion + 1));

        assertThat(
                membershipPeriodCount(
                        membershipId))
                .isEqualTo(
                        originalPeriodCount);

        assertThat(
                openFreezeCount(
                        membershipId))
                .isZero();

        assertThat(
                cancellationClosedFreezeCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                freezeCancelledOn(
                        membershipId))
                .isEqualTo(
                        CANCELLATION_DATE);

        assertThat(
                freezeCancelledByUserId(
                        membershipId))
                .isNotNull();

        assertThat(
                membershipCancelledByUserId(
                        membershipId))
                .isEqualTo(
                        freezeCancelledByUserId(
                                membershipId));

        assertThat(
                cancellationStatusHistoryCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                cancellationPreviousStatus(
                        membershipId))
                .isEqualTo("FROZEN");

        assertThat(
                cancellationAuditCount(
                        membershipId))
                .isEqualTo(1);

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "previousStatus"))
                .isEqualTo("FROZEN");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "resultingStatus"))
                .isEqualTo("CANCELLED");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "closedOpenFreeze"))
                .isEqualTo("true");
    }

    @Test
    void cancellationShouldTrimReason()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        String body =
                cancellationBody(
                        CANCELLATION_DATE,
                        "  Client requested cancellation  ",
                        version);

        cancelMembershipWithBody(
                membershipId,
                body,
                adminSession)
                .andExpect(
                        status().isOk());

        assertThat(
                membershipCancellationReason(
                        membershipId))
                .isEqualTo(
                        "Client requested cancellation");

        assertThat(
                cancellationAuditMetadata(
                        membershipId,
                        "reason"))
                .isEqualTo(
                        "Client requested cancellation");
    }
}
