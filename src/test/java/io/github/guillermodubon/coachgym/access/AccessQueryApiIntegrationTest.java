package io.github.guillermodubon.coachgym.access;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AccessQueryApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    @Test
    void reportsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/access/records/{id}", UUID.randomUUID())
                        .session(loginAsAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCESS_RECORD_NOT_FOUND"));
    }

    @Test
    void listsWithDefaultsPaginationAndNoOptionalFilters() throws Exception {
        UUID actor = userId(ADMIN_USERNAME);
        insertAccessRow("XYZ-1", null, null, null, null, null,
                "DENIED", "IDENTIFIER_NOT_FOUND", Instant.parse("2026-09-15T10:00:00Z"), actor);
        insertAccessRow("XYZ-2", null, null, null, null, null,
                "DENIED", "IDENTIFIER_NOT_FOUND", Instant.parse("2026-09-15T11:00:00Z"), actor);

        mockMvc.perform(get("/api/v1/access/records").session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].presentedIdentifier").value("XYZ-2"));
    }

    @Test
    void filtersByClientMembershipResultReasonActorAndInclusiveRange() throws Exception {
        MockHttpSession session = loginAsAdmin();
        UUID actor = userId(ADMIN_USERNAME);
        ClientFixture client = createClient("ACTIVE");
        java.time.LocalDate today = java.time.LocalDate.now(
                java.time.ZoneId.of("America/El_Salvador"));
        MembershipFixture membership = createMembership(
                client, "ACTIVE", today.minusDays(1), today.plusDays(30));
        Instant target = Instant.parse("2026-09-15T12:00:00Z");
        insertAccessRow(membership.code(), client.id(), client.code(), membership.id(),
                membership.code(), membership.periodId(), "ALLOWED", "ACCESS_ALLOWED", target, actor);
        insertAccessRow("XYZ-OTHER", null, null, null, null, null,
                "DENIED", "IDENTIFIER_NOT_FOUND", target.plusSeconds(1), actor);

        mockMvc.perform(get("/api/v1/access/records")
                        .session(session)
                        .param("clientId", client.id().toString())
                        .param("membershipId", membership.id().toString())
                        .param("result", "ALLOWED")
                        .param("reasonCode", "ACCESS_ALLOWED")
                        .param("checkedInFrom", target.toString())
                        .param("checkedInUntil", target.toString())
                        .param("processedByUserId", actor.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[*].result", everyItem(is("ALLOWED"))))
                .andExpect(jsonPath("$.items[*].clientId", everyItem(is(client.id().toString()))));
    }

    @Test
    void supportsAscendingOrderAndEmptyPage() throws Exception {
        UUID actor = userId(ADMIN_USERNAME);
        insertAccessRow("XYZ-EARLY", null, null, null, null, null,
                "DENIED", "IDENTIFIER_NOT_FOUND", Instant.parse("2026-09-15T10:00:00Z"), actor);
        insertAccessRow("XYZ-LATE", null, null, null, null, null,
                "DENIED", "IDENTIFIER_NOT_FOUND", Instant.parse("2026-09-15T11:00:00Z"), actor);
        MockHttpSession session = loginAsAdmin();
        mockMvc.perform(get("/api/v1/access/records")
                        .session(session).param("sort", "CHECKED_IN_AT").param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].presentedIdentifier").value("XYZ-EARLY"));
        mockMvc.perform(get("/api/v1/access/records")
                        .session(session).param("page", "9").param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
