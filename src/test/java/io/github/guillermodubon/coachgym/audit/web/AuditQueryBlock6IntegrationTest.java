package io.github.guillermodubon.coachgym.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** End-to-end security, privacy, pagination, and failure regressions. */
class AuditQueryBlock6IntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final Instant MIXED_TIMESTAMP =
            Instant.parse("2026-09-16T12:00:00Z");
    private static final Instant PAGE_TIMESTAMP =
            Instant.parse("2026-09-17T12:00:00Z");

    private UUID paymentEntryId;

    @BeforeEach
    void insertMixedAuditFixtures() {
        jdbcTemplate.update("delete from gym.audit_entries");

        paymentEntryId = UUID.randomUUID();
        insertAuditEntry(
                paymentEntryId,
                "PAYMENT_REGISTERED",
                "PAYMENT",
                "PAY-QUERY-001",
                "Payment registered.",
                "{\"paymentId\":\"payment-001\","
                        + "\"paymentMethod\":\"CASH\","
                        + "\"smtpPassword\":\"do-not-return\","
                        + "\"stripePayload\":\"do-not-return\","
                        + "\"emailBody\":\"do-not-return\"}",
                MIXED_TIMESTAMP);

        insertAuditEntry(
                UUID.randomUUID(), "CLIENT_REGISTERED", "CLIENT", "CLI-QUERY-001",
                "Client registered.", "{}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "MEMBERSHIP_CREATED", "MEMBERSHIP", "MEM-QUERY-001",
                "Membership created.", "{\"listPrice\":\"30.00\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "PAYMENT_ATTEMPT_CREATED", "PAYMENT_ATTEMPT", "ATT-QUERY-001",
                "Payment attempt created.", "{\"amount\":\"30.00\",\"testMode\":true}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "PAYMENT_RECEIPT_GENERATED", "PAYMENT_RECEIPT", "REC-QUERY-001",
                "Payment receipt generated.", "{\"paymentCode\":\"PAY-QUERY-001\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "ACCESS_CREDENTIAL_ISSUED", "ACCESS_CREDENTIAL", "QR-QUERY-001",
                "Access credential issued.", "{\"tokenSchemeVersion\":\"sha256-v1\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "ACCESS_DENIED", "ACCESS_RECORD", "ACCESS-QUERY-001",
                "Access denied.", "{\"reasonCode\":\"PAYMENT_REQUIRED\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "ACCESS_PAYMENT_POLICY_CHANGED", "SETTINGS", "GYM_SETTINGS",
                "Access payment policy changed.", "{\"previousValue\":false,\"newValue\":true}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "EMAIL_DELIVERY_SENT", "EMAIL_DELIVERY", "EMAIL-QUERY-001",
                "Email delivery sent.", "{\"deliveryType\":\"RECEIPT\",\"maskedRecipient\":\"a***@example.test\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "EQUIPMENT_REGISTERED", "EQUIPMENT", "EQ-QUERY-001",
                "Equipment registered.", "{\"equipmentCode\":\"EQ-QUERY-001\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "INCIDENT_REPORTED", "INCIDENT", "INC-QUERY-001",
                "Incident reported.", "{\"priority\":\"HIGH\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "MAINTENANCE_SCHEDULED", "MAINTENANCE", "MNT-QUERY-001",
                "Maintenance scheduled.", "{\"maintenanceType\":\"PREVENTIVE\"}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "PLAN_CREATED", "MEMBERSHIP_PLAN", "PLAN-QUERY-001",
                "Membership plan created.", "{}", MIXED_TIMESTAMP);
        insertAuditEntry(
                UUID.randomUUID(), "PROMOTION_CREATED", "PROMOTION", "PROMO-QUERY-001",
                "Promotion created.", "{}", MIXED_TIMESTAMP);
    }

    @Test
    void adminCanSearchMixedSourcesAndDetailNeverReturnsSensitiveMetadata() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("resourceType", "payment")
                        .param("actionCode", "payment_registered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].actionCode").value("PAYMENT_REGISTERED"))
                .andExpect(jsonPath("$.items[0].resourceType").value("PAYMENT"));

        String detail = mockMvc.perform(get("/api/v1/audit-entries/{id}", paymentEntryId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.paymentId").value("payment-001"))
                .andExpect(jsonPath("$.metadata.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.metadataRedacted").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(detail)
                .doesNotContain("smtpPassword", "stripePayload", "emailBody", "do-not-return");
    }

    @Test
    void boundedPagesRemainStableForTwoHundredFiveEqualTimestampRows() throws Exception {
        jdbcTemplate.update("delete from gym.audit_entries");
        Set<String> expectedIds = new LinkedHashSet<>();
        for (int index = 0; index < 205; index++) {
            UUID id = pageId(index);
            expectedIds.add(id.toString());
            insertAuditEntry(
                    id,
                    "CLIENT_REGISTERED",
                    "CLIENT",
                    "PAGE-%03d".formatted(index),
                    "Client registered.",
                    "{}",
                    PAGE_TIMESTAMP);
        }

        MockHttpSession admin = loginAsAdmin();
        List<String> allPageIds = new ArrayList<>();
        String firstPage = page(admin, 0);
        String secondPage = page(admin, 1);
        String lastPage = page(admin, 2);
        allPageIds.addAll(itemIds(firstPage));
        allPageIds.addAll(itemIds(secondPage));
        allPageIds.addAll(itemIds(lastPage));

        assertThat(itemIds(firstPage)).hasSize(100);
        assertThat(itemIds(secondPage)).hasSize(100);
        assertThat(itemIds(lastPage)).hasSize(5);
        assertThat((Number) JsonPath.read(firstPage, "$.totalElements")).isEqualTo(205);
        assertThat((Number) JsonPath.read(firstPage, "$.totalPages")).isEqualTo(3);
        assertThat(allPageIds).hasSize(205).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(expectedIds);

        String beyondRange = page(admin, 99);
        assertThat(itemIds(beyondRange)).isEmpty();
        assertThat((Number) JsonPath.read(beyondRange, "$.totalElements")).isEqualTo(205);
        assertThat((Number) JsonPath.read(beyondRange, "$.totalPages")).isEqualTo(3);
    }

    @Test
    void rejectsMalformedAndUnboundedQueryInputsWithoutExecutingARead() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("actorUserId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("sort", "metadata"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("resourceType", "UNKNOWN_RESOURCE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("occurredFrom", "2025-01-01T00:00:00Z")
                        .param("occurredUntil", "2026-01-03T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_QUERY_VALIDATION_FAILED"));
    }

    @Test
    void readsNeverWriteOrExposeAMutationRoute() throws Exception {
        long before = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);

        mockMvc.perform(get("/api/v1/audit-entries")
                        .session(loginAsAdmin())
                        .param("size", "100"))
                .andExpect(status().isOk());

        long after = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);
        assertThat(after).isEqualTo(before);
    }

    private String page(MockHttpSession admin, int page) throws Exception {
        return mockMvc.perform(get("/api/v1/audit-entries")
                        .session(admin)
                        .param("page", Integer.toString(page))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static List<String> itemIds(String body) {
        return JsonPath.read(body, "$.items[*].id");
    }

    private void insertAuditEntry(
            UUID id,
            String actionCode,
            String resourceType,
            String resourceCode,
            String summary,
            String metadata,
            Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot, action_code,
                     resource_type, resource_id, resource_code_snapshot, summary,
                     metadata, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """,
                id,
                adminId,
                ADMIN_USERNAME,
                actionCode,
                resourceType,
                UUID.randomUUID(),
                resourceCode,
                summary,
                metadata,
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
    }

    private static UUID pageId(int index) {
        return UUID.fromString("00000000-0000-0000-0000-%012x".formatted(0x1000 + index));
    }
}
