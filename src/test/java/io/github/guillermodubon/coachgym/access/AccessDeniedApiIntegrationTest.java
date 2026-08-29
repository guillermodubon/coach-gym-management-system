package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AccessDeniedApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void unknownIdentifierIsPersistedAndAudited() throws Exception {
        assertDenial("XYZ-999999", "IDENTIFIER_NOT_FOUND", null, null);
    }

    @Test
    void inactiveClientPrecedesMissingMembership() throws Exception {
        ClientFixture client = createClient("INACTIVE");
        assertDenial(client.code(), "CLIENT_INACTIVE", client.id(), null);
    }

    @Test
    void activeClientWithoutMembershipIsDenied() throws Exception {
        ClientFixture client = createClient("ACTIVE");
        assertDenial(client.code(), "MEMBERSHIP_NOT_FOUND", client.id(), null);
    }

    @Test
    void futurePeriodIsDenied() throws Exception {
        LocalDate today = today();
        MembershipFixture membership = createMembership(createClient("ACTIVE"),
                "ACTIVE", today.plusDays(1), today.plusDays(30));
        assertDenial(membership.code(), "MEMBERSHIP_NOT_STARTED",
                membership.client().id(), membership.id());
    }

    @Test
    void finishedPeriodIsDenied() throws Exception {
        LocalDate today = today();
        MembershipFixture membership = createMembership(createClient("ACTIVE"),
                "ACTIVE", today.minusDays(30), today.minusDays(1));
        assertDenial(membership.code(), "MEMBERSHIP_PERIOD_EXPIRED",
                membership.client().id(), membership.id());
    }

    @Test
    void frozenMembershipIsDeniedOnInclusiveWindow() throws Exception {
        LocalDate today = today();
        MembershipFixture membership = createMembership(createClient("ACTIVE"),
                "ACTIVE", today.minusDays(10), today.plusDays(20));
        createOpenFreeze(membership, today, today.plusDays(5));
        assertDenial(membership.code(), "MEMBERSHIP_FROZEN",
                membership.client().id(), membership.id());
    }

    @Test
    void expiredMembershipStatusPrecedesPeriodDates() throws Exception {
        LocalDate today = today();
        MembershipFixture membership = createMembership(createClient("ACTIVE"),
                "EXPIRED", today.minusDays(2), today.plusDays(20));
        assertDenial(membership.code(), "MEMBERSHIP_EXPIRED",
                membership.client().id(), membership.id());
    }

    @Test
    void cancelledMembershipIsDenied() throws Exception {
        LocalDate today = today();
        MembershipFixture membership = createMembership(createClient("ACTIVE"),
                "CANCELLED", today.minusDays(2), today.plusDays(20));
        assertDenial(membership.code(), "MEMBERSHIP_CANCELLED",
                membership.client().id(), membership.id());
    }

    @Test
    void repeatedDenialsCreateIndependentRecordsAndAudits() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID first = responseId(checkIn(session, "XYZ-999999"));
        UUID second = responseId(checkIn(session, "XYZ-999999"));
        assertThat(first).isNotEqualTo(second);
        assertThat(countAccessRows()).isEqualTo(2);
        assertThat(countAccessAudits()).isEqualTo(2);
    }

    private void assertDenial(String identifier, String reasonCode,
            UUID expectedClientId, UUID expectedMembershipId) throws Exception {
        MockHttpSession session = loginAsAdmin();
        MvcResult result = checkIn(session, identifier);
        UUID id = responseId(result);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"result\":\"DENIED\"")
                .contains("\"reasonCode\":\"" + reasonCode + "\"");
        assertThat(countAccessRows()).isEqualTo(1);
        assertThat(countAccessAudits()).isEqualTo(1);
        Map<String, Object> row = accessRow(id);
        assertThat(row.get("client_id")).isEqualTo(expectedClientId);
        assertThat(row.get("membership_id")).isEqualTo(expectedMembershipId);
        Map<String, Object> audit = accessAudit(id);
        assertThat(audit.get("resource_type")).isEqualTo("ACCESS_RECORD");
        assertThat(audit.get("resource_id")).isEqualTo(id);
        assertThat(audit.get("actor_user_id")).isEqualTo(userId(ADMIN_USERNAME));
        String metadata = audit.get("metadata").toString().toLowerCase();
        assertThat(metadata).contains(reasonCode.toLowerCase());
        assertThat(metadata).doesNotContain("email", "phone", "password", "cookie", "session", "csrf");
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/El_Salvador"));
    }
}
