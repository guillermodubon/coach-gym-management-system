package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembershipFreezeReactivationApiIntegrationTest
        extends AbstractMembershipFreezeApiIntegrationTest {

    @Test
    void adminFreezesAndReactivatesMembership()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        UUID originalPeriodId =
                currentPeriodId(membershipId);

        long initialVersion =
                membershipVersion(membershipId);

        freezeMembership(
                session,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                initialVersion)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        membershipId.toString()))
                .andExpect(
                        jsonPath("$.status")
                                .value("FROZEN"))
                .andExpect(
                        jsonPath("$.currentPeriod.id")
                                .value(
                                        originalPeriodId.toString()))
                .andExpect(
                        jsonPath("$.currentPeriod.periodNumber")
                                .value(1))
                .andExpect(
                        jsonPath("$.currentPeriod.source")
                                .value("INITIAL"))
                .andExpect(
                        jsonPath("$.version")
                                .value(initialVersion + 1));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(membershipVersion(membershipId))
                .isEqualTo(initialVersion + 1);

        assertThat(membershipPeriodCount(membershipId))
                .isEqualTo(1);

        assertThat(currentPeriodId(membershipId))
                .isEqualTo(originalPeriodId);

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);

        Map<String, Object> freeze =
                freezeRow(membershipId);

        assertThat(freeze.get("membership_id"))
                .isEqualTo(membershipId);

        assertThat(freeze.get("membership_period_id"))
                .isEqualTo(originalPeriodId);

        assertThat(
                asLocalDate(
                        freeze.get("starts_on")))
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                9,
                                10));

        assertThat(
                asLocalDate(
                        freeze.get("planned_ends_on")))
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                9,
                                20));

        assertThat(freeze.get("reason"))
                .isEqualTo("Medical leave");

        assertThat(freeze.get("reactivated_on"))
                .isNull();

        assertThat(
                statusHistoryCount(
                        membershipId,
                        "ACTIVE",
                        "FROZEN"))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_FROZEN"))
                .isEqualTo(1);

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_FROZEN",
                        "startsOn"))
                .isEqualTo("2026-09-10");

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_FROZEN",
                        "plannedEndsOn"))
                .isEqualTo("2026-09-20");

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_FROZEN",
                        "previousStatus"))
                .isEqualTo("ACTIVE");

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_FROZEN",
                        "resultingStatus"))
                .isEqualTo("FROZEN");

        reactivateMembership(
                session,
                membershipId,
                "2026-09-15",
                initialVersion + 1)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE"))
                .andExpect(
                        jsonPath("$.currentPeriod.id")
                                .value(
                                        originalPeriodId.toString()))
                .andExpect(
                        jsonPath("$.currentPeriod.periodNumber")
                                .value(1))
                .andExpect(
                        jsonPath("$.currentPeriod.source")
                                .value("INITIAL"))
                .andExpect(
                        jsonPath("$.version")
                                .value(initialVersion + 2));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(membershipVersion(membershipId))
                .isEqualTo(initialVersion + 2);

        assertThat(membershipPeriodCount(membershipId))
                .isEqualTo(1);

        assertThat(currentPeriodId(membershipId))
                .isEqualTo(originalPeriodId);

        assertThat(openFreezeCount(membershipId))
                .isZero();

        assertThat(closedFreezeCount(membershipId))
                .isEqualTo(1);

        Map<String, Object> closedFreeze =
                freezeRow(membershipId);

        assertThat(
                asLocalDate(
                        closedFreeze.get("reactivated_on")))
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                9,
                                15));

        assertThat(closedFreeze.get(
                "reactivated_by_user_id"))
                .isNotNull();

        assertThat(
                statusHistoryCount(
                        membershipId,
                        "FROZEN",
                        "ACTIVE"))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED"))
                .isEqualTo(1);

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED",
                        "reactivatedOn"))
                .isEqualTo("2026-09-15");

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED",
                        "previousStatus"))
                .isEqualTo("FROZEN");

        assertThat(
                auditMetadataValue(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED",
                        "resultingStatus"))
                .isEqualTo("ACTIVE");
    }

    @Test
    void receptionistCanFreezeAndReactivateMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(adminSession);

        long initialVersion =
                membershipVersion(membershipId);

        MockHttpSession receptionistSession =
                loginAsReceptionist();

        freezeMembership(
                receptionistSession,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Travel",
                initialVersion)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("FROZEN"));

        assertThat(
                auditActorIdentifier(
                        membershipId,
                        "MEMBERSHIP_FROZEN"))
                .isNotBlank();

        reactivateMembership(
                receptionistSession,
                membershipId,
                "2026-09-15",
                initialVersion + 1)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE"));

        assertThat(
                auditActorIdentifier(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED"))
                .isNotBlank();
    }

    @Test
    void membershipCanBeFrozenAgainAfterReactivation()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        long version =
                membershipVersion(membershipId);

        freezeMembership(
                session,
                membershipId,
                "2026-09-05",
                "2026-09-10",
                "First freeze",
                version)
                .andExpect(status().isOk());

        reactivateMembership(
                session,
                membershipId,
                "2026-09-08",
                version + 1)
                .andExpect(status().isOk());

        freezeMembership(
                session,
                membershipId,
                "2026-09-15",
                "2026-09-20",
                "Second freeze",
                version + 2)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("FROZEN"));

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);

        assertThat(closedFreezeCount(membershipId))
                .isEqualTo(1);

        assertThat(membershipPeriodCount(membershipId))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_FROZEN"))
                .isEqualTo(2);
    }

    private static LocalDate asLocalDate(
            Object value) {

        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        throw new AssertionError(
                "Expected a database date but received "
                        + (value == null
                        ? "null"
                        : value.getClass().getName())
                        + ": "
                        + value);
    }
}
