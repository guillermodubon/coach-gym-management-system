package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end lifecycle, concurrency, compensation and privacy regression coverage. */
class AccessCredentialLifecycleIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final Path STORAGE = createStorage();

    @DynamicPropertySource
    static void configureCredentialStorage(DynamicPropertyRegistry registry) {
        registry.add("gym.storage.access-credentials.directory", STORAGE::toString);
    }

    @BeforeEach
    void clearCredentialFixtures() throws IOException {
        jdbcTemplate.execute(
                "truncate table gym.access_records, "
                        + "gym.access_credential_history, gym.access_credentials");
        jdbcTemplate.update(
                "delete from gym.audit_entries where resource_type = 'ACCESS_CREDENTIAL'");
        clearStorage();
    }

    @AfterAll
    static void removeStorage() throws IOException {
        clearStorage();
        Files.deleteIfExists(STORAGE);
    }

    @Test
    void issuesCanonicalCredentialAndPreservesClientAndMembershipData() throws Exception {
        UUID clientId = createActiveClient();
        Map<String, Object> clientBefore = clientSnapshot(clientId);
        long membershipsBefore = membershipCount(clientId);

        MvcResult issue = mockMvc.perform(post(path(clientId))
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String response = issue.getResponse().getContentAsString();
        UUID credentialId = UUID.fromString(JsonPath.read(response, "$.id"));
        assertThat(response).doesNotContain(
                "rawToken", "tokenFingerprint", "checksumSha256", "storageKey", "qrPayload");

        MvcResult download = mockMvc.perform(get(path(clientId) + "/content")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andReturn();
        byte[] png = download.getResponse().getContentAsByteArray();
        assertThat(png).isNotEmpty();

        String payload = decodeQr(png);
        assertThat(payload).startsWith(AccessCredentialQrPayload.PAYLOAD_PREFIX);
        assertThat(payload).doesNotContain(
                clientId.toString(), "e2e.", "+50370003009");

        Map<String, Object> credential = jdbcTemplate.queryForMap("""
                select client_id, status, token_fingerprint, payload_version,
                       storage_key, content_type, size_bytes, checksum_sha256
                from gym.access_credentials where id = ?
                """, credentialId);
        assertThat(credential).containsEntry("client_id", clientId)
                .containsEntry("status", "ACTIVE")
                .containsEntry("payload_version", "v1")
                .containsEntry("content_type", "image/png");
        assertThat(credential.get("token_fingerprint").toString())
                .isEqualTo(sha256(payload));
        assertThat(credential.get("token_fingerprint").toString())
                .isNotEqualTo(payload);
        assertThat(((Number) credential.get("size_bytes")).longValue())
                .isEqualTo(png.length);
        assertThat(credential.get("checksum_sha256").toString())
                .isEqualTo(sha256(png));
        assertThat(Files.exists(STORAGE.resolve(credential.get("storage_key").toString())))
                .isTrue();

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.access_credential_history where credential_id = ?",
                Integer.class, credentialId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'ACCESS_CREDENTIAL_ISSUED' and resource_id = ?
                """, Integer.class, credentialId)).isEqualTo(1);
        assertThat(clientSnapshot(clientId)).isEqualTo(clientBefore);
        assertThat(membershipCount(clientId)).isEqualTo(membershipsBefore);
    }

    @Test
    void replacementIsAtomicAndLeavesExactlyOneActiveCredentialAndTwoArtifacts() throws Exception {
        UUID clientId = createActiveClient();
        UUID originalId = issueCredential(clientId, loginAsAdmin());

        String replacementResponse = mockMvc.perform(post(path(clientId) + "/replace")
                        .session(loginAsReceptionist())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID replacementId = UUID.fromString(JsonPath.read(replacementResponse, "$.id"));

        Map<String, Object> original = jdbcTemplate.queryForMap("""
                select status, replaced_by_credential_id, revocation_reason
                from gym.access_credentials where id = ?
                """, originalId);
        assertThat(original).containsEntry("status", "REVOKED")
                .containsEntry("replaced_by_credential_id", replacementId)
                .containsEntry("revocation_reason", "lost card");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credentials
                where client_id = ? and status = 'ACTIVE'
                """, Integer.class, clientId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credential_history
                where client_id = ?
                """, Integer.class, clientId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type = 'ACCESS_CREDENTIAL' and action_code in
                    ('ACCESS_CREDENTIAL_ISSUED', 'ACCESS_CREDENTIAL_REPLACED')
                """, Integer.class)).isEqualTo(2);

        List<Map<String, Object>> artifacts = jdbcTemplate.queryForList("""
                select storage_key, checksum_sha256 from gym.access_credentials
                where client_id = ? order by issued_at
                """, clientId);
        assertThat(artifacts).hasSize(2);
        assertThat(artifacts.get(0).get("checksum_sha256"))
                .isNotEqualTo(artifacts.get(1).get("checksum_sha256"));
        assertThat(artifacts).allSatisfy(artifact ->
                assertThat(Files.exists(STORAGE.resolve(artifact.get("storage_key").toString())))
                        .isTrue());
        assertThat(replacementResponse).doesNotContain("token", "tokenFingerprint", "storageKey");
    }

    @Test
    void concurrentIssueProducesOneCredentialOneHistoryAndOneArtifact() throws Exception {
        UUID clientId = createActiveClient();
        List<HttpResult> results = concurrently(
                () -> issueRequest(clientId, loginAsAdmin()),
                () -> issueRequest(clientId, loginAsReceptionist()));

        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(201));
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credentials
                where client_id = ? and status = 'ACTIVE'
                """, Integer.class, clientId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credential_history where client_id = ?
                """, Integer.class, clientId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type = 'ACCESS_CREDENTIAL'
                  and action_code = 'ACCESS_CREDENTIAL_ISSUED'
                """, Integer.class)).isEqualTo(1);
        assertThat(storedArtifacts()).hasSize(1);
    }

    @Test
    void concurrentRevocationAllowsOneTransitionAndReturnsStableConflict() throws Exception {
        UUID clientId = createActiveClient();
        UUID credentialId = issueCredential(clientId, loginAsAdmin());
        List<HttpResult> results = concurrently(
                () -> revokeRequest(clientId, loginAsAdmin()),
                () -> revokeRequest(clientId, loginAsReceptionist()));

        assertThat(results).as("responses: %s", results).extracting(HttpResult::status)
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credential_history where credential_id = ?
                """, Integer.class, credentialId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'ACCESS_CREDENTIAL_REVOKED' and resource_id = ?
                """, Integer.class, credentialId)).isEqualTo(1);
    }

    @Test
    void concurrentReplacementAllowsOneReplacementChainAndNoOrphanArtifacts() throws Exception {
        UUID clientId = createActiveClient();
        UUID originalId = issueCredential(clientId, loginAsAdmin());
        List<HttpResult> results = concurrently(
                () -> replaceRequest(clientId, loginAsAdmin()),
                () -> replaceRequest(clientId, loginAsReceptionist()));

        assertThat(results).as("responses: %s", results).extracting(HttpResult::status)
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credentials where client_id = ?
                """, Integer.class, clientId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credentials
                where client_id = ? and status = 'ACTIVE'
                """, Integer.class, clientId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credential_history where client_id = ?
                """, Integer.class, clientId)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type = 'ACCESS_CREDENTIAL'
                  and action_code = 'ACCESS_CREDENTIAL_REPLACED'
                """, Integer.class)).isEqualTo(1);
        assertThat(storedArtifacts()).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("""
                select status from gym.access_credentials where id = ?
                """, String.class, originalId)).isEqualTo("REVOKED");
    }

    @Test
    void revokeVersusReplaceProducesOneValidCanonicalResult() throws Exception {
        UUID clientId = createActiveClient();
        UUID originalId = issueCredential(clientId, loginAsAdmin());
        List<HttpResult> results = concurrently(
                () -> revokeRequest(clientId, loginAsAdmin()),
                () -> replaceRequest(clientId, loginAsReceptionist()));

        assertThat(results).extracting(HttpResult::status)
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.access_credentials
                where client_id = ? and status = 'ACTIVE'
                """, Integer.class, clientId)).isLessThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select status from gym.access_credentials where id = ?
                """, String.class, originalId)).isEqualTo("REVOKED");
    }

    @Test
    void unknownClientReturnsClientNotFoundWithoutCreatingCredentialOrArtifact() throws Exception {
        UUID unknownClient = UUID.randomUUID();
        String response = mockMvc.perform(post(path(unknownClient))
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(response, "$.code")).isEqualTo("CLIENT_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.access_credentials", Integer.class)).isZero();
        assertThat(storedArtifacts()).isEmpty();
    }

    private UUID issueCredential(UUID clientId, MockHttpSession session) throws Exception {
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post(path(clientId)).session(session).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isCreated()).andReturn()
                        .getResponse().getContentAsString(), "$.id"));
    }

    private HttpResult issueRequest(UUID clientId, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post(path(clientId)).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        return new HttpResult(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private HttpResult revokeRequest(UUID clientId, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post(path(clientId) + "/revoke").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andReturn();
        return new HttpResult(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private HttpResult replaceRequest(UUID clientId, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post(path(clientId) + "/replace").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"lost card\",\"version\":0}"))
                .andReturn();
        return new HttpResult(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private List<HttpResult> concurrently(Callable<HttpResult>... operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.length);
        CountDownLatch ready = new CountDownLatch(operations.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpResult>> futures = new ArrayList<>();
            for (Callable<HttpResult> operation : operations) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return operation.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpResult> results = new ArrayList<>();
            for (Future<HttpResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID createActiveClient() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-13T10:00:00Z");
        java.sql.Timestamp timestamp = java.sql.Timestamp.from(now);
        String email = "e2e." + id + "@example.test";
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, email, phone, date_of_birth, status,
                     created_by_user_id, updated_by_user_id, created_at, updated_at, version)
                values (?, 'End-to-end', 'Credential', ?, '+50370003009',
                        '1990-01-01', 'ACTIVE', ?, ?, ?, ?, 0)
                """, id, email, adminId, adminId, timestamp, timestamp);
        return id;
    }

    private Map<String, Object> clientSnapshot(UUID clientId) {
        return jdbcTemplate.queryForMap("""
                select first_name, last_name, email, phone, date_of_birth, status,
                       version, updated_at
                from gym.clients where id = ?
                """, clientId);
    }

    private long membershipCount(UUID clientId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from gym.memberships where client_id = ?", Long.class, clientId);
    }

    private List<Path> storedArtifacts() throws IOException {
        if (!Files.exists(STORAGE)) {
            return List.of();
        }
        try (var paths = Files.walk(STORAGE)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static String decodeQr(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    private static String sha256(String value) throws Exception {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String path(UUID clientId) {
        return "/api/v1/clients/" + clientId + "/access-credential";
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("coach-gym-access-credential-e2e-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void clearStorage() throws IOException {
        if (!Files.exists(STORAGE)) {
            return;
        }
        try (var paths = Files.walk(STORAGE)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(STORAGE))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new StorageCleanupException(exception);
                        }
                    });
        } catch (StorageCleanupException exception) {
            throw exception.cause;
        }
    }

    private record HttpResult(int status, String body) {
    }

    private static final class StorageCleanupException extends RuntimeException {
        private final IOException cause;

        private StorageCleanupException(IOException cause) {
            this.cause = cause;
        }
    }
}
