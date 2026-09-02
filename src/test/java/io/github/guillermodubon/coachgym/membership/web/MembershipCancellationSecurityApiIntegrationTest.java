package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MembershipCancellationSecurityApiIntegrationTest
        extends AbstractMembershipCancellationApiIntegrationTest {

    @Test
    void unauthenticatedUserShouldNotCancelMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        cancelMembershipUnauthenticated(
                membershipId,
                version)
                .andExpect(
                        status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "AUTHENTICATION_REQUIRED"));

        assertCancellationDidNotOccur(
                membershipId,
                version);
    }

    @Test
    void cancellationShouldRequireCsrf()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        cancelMembershipWithoutCsrf(
                membershipId,
                version,
                adminSession)
                .andExpect(
                        status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "CSRF_TOKEN_INVALID"));

        assertCancellationDidNotOccur(
                membershipId,
                version);
    }

    @Test
    void receptionistShouldNotCancelMembership()
            throws Exception {

        MockHttpSession adminSession =
                loginAsAdmin();

        UUID membershipId =
                createCancellableActiveMembership(
                        adminSession);

        long version =
                membershipVersion(
                        membershipId);

        MockHttpSession receptionistSession =
                loginAsReceptionist();

        cancelMembership(
                membershipId,
                version,
                receptionistSession)
                .andExpect(
                        status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED"));

        assertCancellationDidNotOccur(
                membershipId,
                version);
    }

    private void assertCancellationDidNotOccur(
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
