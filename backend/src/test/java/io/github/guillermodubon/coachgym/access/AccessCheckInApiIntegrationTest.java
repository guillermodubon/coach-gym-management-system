package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AccessCheckInApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void adminChecksInWithMembershipCodeAndCanRetrieveRecord() throws Exception {
        MockHttpSession session = loginAsAdmin();
        LocalDate today = LocalDate.now(ZoneId.of("America/El_Salvador"));
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembership(
                client, "ACTIVE", today.minusDays(5), today.plusDays(25));

        MvcResult result = checkIn(session, "  " + membership.code().toLowerCase() + "  ");
        UUID id = responseId(result);

        assertThat(result.getResponse().getContentAsString())
                .contains("\"result\":\"ALLOWED\"")
                .contains("\"reasonCode\":\"ACCESS_ALLOWED\"")
                .contains("\"presentedIdentifier\":\"" + membership.code() + "\"");
        assertThat(countAccessRows()).isEqualTo(1);
        assertThat(countAccessAudits()).isZero();
        Map<String, Object> row = accessRow(id);
        assertThat(row.get("decision")).isEqualTo("ALLOWED");
        assertThat(row.get("client_id")).isEqualTo(client.id());
        assertThat(row.get("membership_id")).isEqualTo(membership.id());
        assertThat(row.get("recorded_by_user_id")).isEqualTo(userId(ADMIN_USERNAME));

        mockMvc.perform(get("/api/v1/access/records/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.result").value("ALLOWED"));
    }

    @Test
    void adminChecksInWithClientCodeAndResolvesCurrentMembership() throws Exception {
        MockHttpSession session = loginAsAdmin();
        LocalDate today = LocalDate.now(ZoneId.of("America/El_Salvador"));
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembership(
                client, "ACTIVE", today.minusDays(2), today.plusDays(28));

        MvcResult result = checkIn(session, client.code());

        assertThat(result.getResponse().getContentAsString())
                .contains("\"result\":\"ALLOWED\"")
                .contains(membership.id().toString());
        assertThat(countAccessRows()).isEqualTo(1);
        assertThat(countAccessAudits()).isZero();
    }

    @Test
    void receptionistCanCheckInAndRepeatedAttemptsRemainIndependent() throws Exception {
        MockHttpSession session = loginAsReceptionist();
        LocalDate today = LocalDate.now(ZoneId.of("America/El_Salvador"));
        MembershipFixture membership = createMembership(
                createClient("ACTIVE"), "ACTIVE", today.minusDays(1), today.plusDays(30));

        UUID first = responseId(checkIn(session, membership.code()));
        UUID second = responseId(checkIn(session, membership.code()));

        assertThat(first).isNotEqualTo(second);
        assertThat(countAccessRows()).isEqualTo(2);
        assertThat(countAccessAudits()).isZero();
        assertThat(accessRow(first).get("recorded_by_user_id"))
                .isEqualTo(userId(RECEPTIONIST_USERNAME));
    }
}
