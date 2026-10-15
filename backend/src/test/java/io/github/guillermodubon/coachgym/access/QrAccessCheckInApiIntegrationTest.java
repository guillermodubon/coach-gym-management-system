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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "gym.access.duplicate-scan-window=PT30S")
class QrAccessCheckInApiIntegrationTest extends AbstractAccessApiIntegrationTest {

    private static final String PAYLOAD =
            "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private AccessCredentialTokenProtector tokenProtector;

    @BeforeEach
    void clearQrCredentials() {
        jdbcTemplate.execute("truncate table gym.access_records, "
                + "gym.access_credential_history, gym.access_credentials");
    }

    @Test
    void adminCanProcessAllowedQrCheckInWithoutSecretInResponseOrStorage() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/El_Salvador"));
        ClientFixture client = createClient("ACTIVE");
        MembershipFixture membership = createMembership(
                client, "ACTIVE", today.minusDays(2), today.plusDays(28));
        UUID credentialId = insertActiveCredential(client.id());

        MockHttpSession session = loginAsAdminWithActiveBranch();
        String response = mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ALLOWED"))
                .andExpect(jsonPath("$.reasonCode").value("ACCESS_ALLOWED"))
                .andExpect(jsonPath("$.clientId").value(client.id().toString()))
                .andExpect(jsonPath("$.membershipId").value(membership.id().toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(PAYLOAD, tokenProtector.fingerprint(PAYLOAD));
        assertThat(accessRow(responseIdFrom(response)).get("access_credential_id"))
                .isEqualTo(credentialId);
        assertThat(accessRow(responseIdFrom(response)).get("identification_source"))
                .isEqualTo("QR_CREDENTIAL");
    }

    @Test
    void receptionistCanProcessDeniedQrCheckInUsingTheExistingPolicy() throws Exception {
        ClientFixture client = createClient("INACTIVE");
        insertActiveCredential(client.id());

        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(loginAsReceptionistWithActiveBranch())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DENIED"))
                .andExpect(jsonPath("$.reasonCode").value("CLIENT_INACTIVE"));

        assertThat(countAccessRows()).isEqualTo(1);
    }

    @Test
    void missingCsrfDoesNotEnterQrBusinessWorkflow() throws Exception {
        ClientFixture client = createClient("ACTIVE");
        insertActiveCredential(client.id());

        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        assertThat(countAccessRows()).isZero();
    }

    @Test
    void anonymousQrCheckInIsRejectedBeforeBusinessProcessing() throws Exception {
        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        assertThat(countAccessRows()).isZero();
    }

    @Test
    void secondScanWithinTheConfiguredWindowIsRecordedAsDeniedDuplicate() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/El_Salvador"));
        ClientFixture client = createClient("ACTIVE");
        createMembership(client, "ACTIVE", today.minusDays(1), today.plusDays(30));
        insertActiveCredential(client.id());
        MockHttpSession session = loginAsAdminWithActiveBranch();

        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ALLOWED"));

        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DENIED"))
                .andExpect(jsonPath("$.reasonCode").value("DUPLICATE_CHECK_IN"));

        assertThat(countAccessRows()).isEqualTo(2);
    }

    @Test
    void unknownCredentialIsRejectedWithSafeProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(loginAsAdminWithActiveBranch())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(
                        "The QR access credential was not found or is inactive."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PAYLOAD))));

        assertThat(countAccessRows()).isZero();
    }

    @Test
    void revokedCredentialIsIndistinguishableFromAnUnknownCredential() throws Exception {
        ClientFixture client = createClient("ACTIVE");
        UUID credentialId = insertActiveCredential(client.id());
        Instant revokedAt = Instant.parse("2026-09-14T10:05:00Z");
        jdbcTemplate.update("""
                update gym.access_credentials
                   set status='REVOKED', revoked_at=?, revoked_by_user_id=?,
                       revocation_reason='Integration test revocation'
                 where id=?
                """, java.sql.Timestamp.from(revokedAt), userId(ADMIN_USERNAME), credentialId);

        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(loginAsAdminWithActiveBranch())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"%s\"}".formatted(PAYLOAD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCESS_CREDENTIAL_NOT_FOUND"));

        assertThat(countAccessRows()).isZero();
    }

    @Test
    void malformedOrUnsupportedPayloadIsRejectedBeforeCredentialLookup() throws Exception {
        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"cgac:v2:unsupported\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_VALIDATION_FAILED"));

        assertThat(countAccessRows()).isZero();
    }

    @Test
    void requestCannotInjectServerControlledFields() throws Exception {
        mockMvc.perform(post("/api/v1/access/qr-check-in")
                        .with(csrf())
                        .session(loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":"%s","clientId":"%s","result":"ALLOWED",
                                 "actor":"attacker","occurredAt":"2026-01-01T00:00:00Z",
                                 "paymentStatus":"PAID"}
                                """.formatted(PAYLOAD, UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        assertThat(countAccessRows()).isZero();
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
                """, credentialId, clientId, "CRED-" + credentialId.toString().substring(0, 8),
                tokenProtector.fingerprint(PAYLOAD), java.sql.Timestamp.from(issuedAt),
                userId(ADMIN_USERNAME), "access-credentials/" + credentialId + ".png",
                "f".repeat(64), java.sql.Timestamp.from(issuedAt),
                java.sql.Timestamp.from(issuedAt));
        return credentialId;
    }

    private UUID responseIdFrom(String response) {
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id"));
    }
}
