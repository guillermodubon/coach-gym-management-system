package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end coverage for the shared payment requirement in manual and QR
 * access workflows. Payment rows are inserted as isolated fixtures; the
 * access service never mutates them while evaluating a check-in.
 */
@TestPropertySource(properties = "gym.access.duplicate-scan-window=PT30S")
class AccessPaymentPolicyIntegrationTest extends AbstractAccessApiIntegrationTest {

    private static final String QR_PAYLOAD =
            "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private AccessCredentialTokenProtector tokenProtector;

    @BeforeEach
    void resetPaymentPolicy() {
        jdbcTemplate.update(
                "update gym.gym_settings "
                        + "set require_confirmed_payment_for_access=false");
        jdbcTemplate.execute("truncate table gym.access_records, "
                + "gym.access_credential_history, gym.access_credentials");
    }

    @AfterEach
    void restoreDefaultPaymentPolicy() {
        jdbcTemplate.update(
                "update gym.gym_settings "
                        + "set require_confirmed_payment_for_access=false");
    }

    @Test
    void disabledPolicyPreservesManualAccessWithoutPayment() throws Exception {
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembershipForToday(client);

        MvcAccessResult result = manualCheckIn(
                loginAsReceptionist(), membership.code());

        assertThat(result.result()).isEqualTo("ALLOWED");
        assertThat(result.reasonCode()).isEqualTo("ACCESS_ALLOWED");
        assertThat(countAccessRows()).isEqualTo(1);
        assertThat(countPayments(client.id())).isZero();
    }

    @Test
    void enabledPolicyDeniesManualAccessWithoutPaymentAndPersistsReason() throws Exception {
        enablePaymentRequirement();
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembershipForToday(client);

        MvcAccessResult result = manualCheckIn(
                loginAsReceptionist(), membership.code());

        assertThat(result.result()).isEqualTo("DENIED");
        assertThat(result.reasonCode()).isEqualTo("PAYMENT_REQUIRED");
        assertThat(accessRow(result.id()).get("reason_code"))
                .isEqualTo("PAYMENT_REQUIRED");
        assertThat(countAccessRows()).isEqualTo(1);
        assertThat(countPayments(client.id())).isZero();
    }

    @Test
    void enabledPolicyAllowsManualAccessWithExactPaidPeriod() throws Exception {
        enablePaymentRequirement();
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembershipForToday(client);
        insertPaidPayment(membership);

        MvcAccessResult result = manualCheckIn(
                loginAsReceptionist(), membership.code());

        assertThat(result.result()).isEqualTo("ALLOWED");
        assertThat(result.reasonCode()).isEqualTo("ACCESS_ALLOWED");
        assertThat(countPayments(client.id())).isEqualTo(1);
    }

    @Test
    void earlierInactiveClientDenialIsPreservedWhenPaymentRequirementIsEnabled()
            throws Exception {
        enablePaymentRequirement();
        ClientFixture client = createClient("INACTIVE");
        MembershipFixture membership = createMembershipForToday(client);

        MvcAccessResult result = manualCheckIn(
                loginAsReceptionist(), membership.code());

        assertThat(result.result()).isEqualTo("DENIED");
        assertThat(result.reasonCode()).isEqualTo("CLIENT_INACTIVE");
    }

    @Test
    void disabledPolicyPreservesQrAccessWithoutPayment() throws Exception {
        ClientFixture client = createClient("ACTIVE");
        createMembershipForToday(client);
        insertActiveCredential(client.id());

        MvcAccessResult result = qrCheckIn(loginAsReceptionist());

        assertThat(result.result()).isEqualTo("ALLOWED");
        assertThat(result.reasonCode()).isEqualTo("ACCESS_ALLOWED");
    }

    @Test
    void enabledPolicyDeniesQrAccessWithoutPayment() throws Exception {
        enablePaymentRequirement();
        ClientFixture client = createClient("ACTIVE");
        createMembershipForToday(client);
        insertActiveCredential(client.id());

        MvcAccessResult result = qrCheckIn(loginAsReceptionist());

        assertThat(result.result()).isEqualTo("DENIED");
        assertThat(result.reasonCode()).isEqualTo("PAYMENT_REQUIRED");
    }

    @Test
    void enabledPolicyAllowsQrAccessWithExactPaidPeriod() throws Exception {
        enablePaymentRequirement();
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembershipForToday(client);
        insertPaidPayment(membership);
        insertActiveCredential(client.id());

        MvcAccessResult result = qrCheckIn(loginAsReceptionist());

        assertThat(result.result()).isEqualTo("ALLOWED");
        assertThat(result.reasonCode()).isEqualTo("ACCESS_ALLOWED");
    }

    @Test
    void duplicateQrAttemptRetainsDuplicatePrecedence() throws Exception {
        enablePaymentRequirement();
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembershipForToday(client);
        insertPaidPayment(membership);
        insertActiveCredential(client.id());
        MockHttpSession session = loginAsReceptionist();

        MvcAccessResult first = qrCheckIn(session);
        MvcAccessResult duplicate = qrCheckIn(session);

        assertThat(first.reasonCode()).isEqualTo("ACCESS_ALLOWED");
        assertThat(duplicate.result()).isEqualTo("DENIED");
        assertThat(duplicate.reasonCode()).isEqualTo("DUPLICATE_CHECK_IN");
        assertThat(countAccessRows()).isEqualTo(2);
    }

    private MvcAccessResult manualCheckIn(
            MockHttpSession session,
            String identifier) throws Exception {
        String body = checkIn(session, identifier)
                .getResponse().getContentAsString();
        return MvcAccessResult.from(body);
    }

    private MvcAccessResult qrCheckIn(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(QR_PAYLOAD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return MvcAccessResult.from(body);
    }

    private MembershipFixture createMembershipForToday(ClientFixture client) {
        LocalDate today = LocalDate.now(ZoneId.of("America/El_Salvador"));
        return createMembership(client, "ACTIVE", today.minusDays(2), today.plusDays(28));
    }

    private void enablePaymentRequirement() {
        jdbcTemplate.update(
                "update gym.gym_settings "
                        + "set require_confirmed_payment_for_access=true");
    }

    private int countPayments(UUID clientId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.payments where client_id=?",
                Integer.class,
                clientId);
        return count == null ? 0 : count;
    }

    private void insertPaidPayment(MembershipFixture membership) {
        jdbcTemplate.update("""
                insert into gym.payments
                    (id,client_id,membership_id,membership_period_id,amount,currency,
                     payment_method,status,paid_at,registered_by_user_id)
                values (?, ?, ?, ?, 25.00, 'USD', 'CASH', 'PAID', current_timestamp, ?)
                """, UUID.randomUUID(), membership.client().id(), membership.id(),
                membership.periodId(), userId(ADMIN_USERNAME));
    }

    private UUID insertActiveCredential(UUID clientId) {
        UUID credentialId = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-09-14T10:00:00Z");
        jdbcTemplate.update("""
                insert into gym.access_credentials
                    (id, client_id, credential_code, token_fingerprint,
                     token_scheme_version, payload_version, status, issued_at,
                     issued_by_user_id, storage_key, content_type, size_bytes,
                     checksum_sha256, renderer_version, created_at, updated_at, version)
                values (?, ?, ?, ?, 'sha256-v1', 'v1', 'ACTIVE', ?, ?, ?,
                        'image/png', 128, ?, 'qr-v1', ?, ?, 0)
                """, credentialId, clientId,
                "CRED-" + credentialId.toString().substring(0, 8),
                tokenProtector.fingerprint(QR_PAYLOAD),
                java.sql.Timestamp.from(issuedAt), userId(ADMIN_USERNAME),
                "access-credentials/" + credentialId + ".png", "f".repeat(64),
                java.sql.Timestamp.from(issuedAt), java.sql.Timestamp.from(issuedAt));
        return credentialId;
    }

    private record MvcAccessResult(UUID id, String result, String reasonCode) {

        private static MvcAccessResult from(String body) {
            return new MvcAccessResult(
                    UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id")),
                    com.jayway.jsonpath.JsonPath.read(body, "$.result"),
                    com.jayway.jsonpath.JsonPath.read(body, "$.reasonCode"));
        }
    }
}
