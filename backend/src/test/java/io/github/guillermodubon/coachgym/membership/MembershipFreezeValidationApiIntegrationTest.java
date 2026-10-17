package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembershipFreezeValidationApiIntegrationTest
        extends AbstractMembershipFreezeApiIntegrationTest {

    @Test
    void rejectsFreezeWithStaleVersion()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        long currentVersion =
                membershipVersion(membershipId);

        freezeMembership(
                session,
                membershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                currentVersion + 1)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VERSION_CONFLICT"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_FROZEN"))
                .isZero();
    }

    @Test
    void rejectsRepeatedFreeze()
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
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        freezeMembership(
                session,
                membershipId,
                "2026-09-12",
                "2026-09-22",
                "Second freeze",
                version + 1)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_ALREADY_FROZEN"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_FROZEN"))
                .isEqualTo(1);
    }

    @Test
    void rejectsReactivationOfActiveMembership()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        long version =
                membershipVersion(membershipId);

        reactivateMembership(
                session,
                membershipId,
                "2026-09-15",
                version)
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_FREEZE_NOT_FOUND"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED"))
                .isZero();
    }

    @Test
    void rejectsReactivationWithStaleVersion()
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
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        reactivateMembership(
                session,
                membershipId,
                "2026-09-15",
                version)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VERSION_CONFLICT"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED"))
                .isZero();
    }

    @Test
    void rejectsReactivationBeforeFreezeStart()
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
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        reactivateMembership(
                session,
                membershipId,
                "2026-09-09",
                version + 1)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VALIDATION_FAILED"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED"))
                .isZero();
    }

    @Test
    void rejectsInvalidFreezeDateRange()
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
                "2026-09-20",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_VALIDATION_FAILED"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_FROZEN"))
                .isZero();
    }

    @Test
    void rejectsStructurallyInvalidFreezeRequest()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/freeze",
                                membershipId)
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        "application/json")
                                .content(
                                        """
                                        {
                                          "startsOn": null,
                                          "plannedEndsOn": null,
                                          "reason": " ",
                                          "version": null
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.fieldErrors.startsOn")
                                .exists())
                .andExpect(
                        jsonPath("$.fieldErrors.plannedEndsOn")
                                .exists())
                .andExpect(
                        jsonPath("$.fieldErrors.reason")
                                .exists())
                .andExpect(
                        jsonPath("$.fieldErrors.version")
                                .exists());

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");

        assertThat(openFreezeCount(membershipId))
                .isZero();
    }

    @Test
    void rejectsStructurallyInvalidReactivationRequest()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID membershipId =
                createActiveMembership(session);

        mockMvc.perform(
                        post(
                                "/api/v1/memberships/{id}/reactivate",
                                membershipId)
                                .session(session)
                                .with(csrf())
                                .contentType(
                                        "application/json")
                                .content(
                                        """
                                        {
                                          "reactivatedOn": null,
                                          "version": null
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.fieldErrors.reactivatedOn")
                                .exists())
                .andExpect(
                        jsonPath("$.fieldErrors.version")
                                .exists());

        assertThat(membershipStatus(membershipId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void rejectsUnknownMembership()
            throws Exception {

        MockHttpSession session =
                loginAsAdmin();

        UUID unknownMembershipId =
                UUID.fromString(
                        "90000000-0000-0000-0000-000000000001");

        freezeMembership(
                session,
                unknownMembershipId,
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                0L)
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_NOT_FOUND"));
    }


    @Test
    void rejectsReactivationWhenClientIsInactive()
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
                "2026-09-10",
                "2026-09-20",
                "Medical leave",
                version)
                .andExpect(status().isOk());

        jdbcTemplate.update(
                """
                update gym.clients
                set status = 'INACTIVE',
                    deactivated_at = current_timestamp,
                    deactivation_reason = 'Integration test'
                where id = (
                    select client_id
                    from gym.memberships
                    where id = ?
                )
                """,
                membershipId);

        reactivateMembership(
                session,
                membershipId,
                "2026-09-15",
                version + 1)
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "MEMBERSHIP_CLIENT_INACTIVE"));

        assertThat(membershipStatus(membershipId))
                .isEqualTo("FROZEN");

        assertThat(openFreezeCount(membershipId))
                .isEqualTo(1);

        assertThat(
                membershipAuditCount(
                        membershipId,
                        "MEMBERSHIP_REACTIVATED"))
                .isZero();
    }
}
