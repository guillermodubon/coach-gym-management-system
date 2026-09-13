package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

class PaymentReceiptEndToEndIntegrationTest extends AbstractPaymentCorrectionApiIntegrationTest {

    private static final Path RECEIPT_DIRECTORY = Path.of(
            System.getProperty("java.io.tmpdir"),
            "coach-gym-payment-receipts-" + UUID.randomUUID());

    @DynamicPropertySource
    static void configureReceiptStorage(DynamicPropertyRegistry registry) {
        registry.add("gym.storage.payment-receipts.directory",
                RECEIPT_DIRECTORY::toString);
    }

    @BeforeEach
    void clearTemporaryReceiptStorage() throws IOException {
        if (!Files.exists(RECEIPT_DIRECTORY)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(RECEIPT_DIRECTORY)) {
            paths.filter(path -> !path.equals(RECEIPT_DIRECTORY))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    });
        }
    }

    @AfterAll
    static void removeTemporaryReceiptStorage() throws IOException {
        if (!Files.exists(RECEIPT_DIRECTORY)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(RECEIPT_DIRECTORY)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    });
        }
    }

    @Test
    void generatesPersistsAuditsReadsAndDownloadsOneCanonicalReceipt() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID clientId = createClient(admin, uniqueEmail());
        String planName = uniqueName("Receipt-E2E-Plan");
        UUID planId = createPlan(admin, planName, "25.00", "USD");
        UUID membershipId = createMembership(admin, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);

        MvcResult paymentResult = mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID paymentId = responseId(paymentResult);
        var paymentBefore = paymentRow(paymentId);
        BigDecimal revenueBeforeReceipt = dashboardRevenue(admin);

        MvcResult receiptResult = mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/receipt", paymentId)
                                .with(csrf())
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/payments/" + paymentId + "/receipt"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn();
        UUID receiptId = responseId(receiptResult);
        String receiptNumber = JsonPath.read(
                receiptResult.getResponse().getContentAsString(), "$.receiptNumber");
        assertThat(dashboardRevenue(admin)).isEqualByComparingTo(revenueBeforeReceipt);

        var receiptRow = jdbcTemplate.queryForMap("""
                select payment_id, payment_code_snapshot, payment_status_snapshot,
                       amount, currency, payment_method, storage_key,
                       content_type, size_bytes, checksum_sha256
                from gym.payment_receipts
                where id = ?
                """, receiptId);
        assertThat(receiptRow.get("payment_id")).isEqualTo(paymentId);
        assertThat(receiptRow.get("payment_status_snapshot")).isEqualTo("PAID");
        assertThat((BigDecimal) receiptRow.get("amount"))
                .isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(receiptRow.get("currency")).isEqualTo("USD");
        assertThat(receiptRow.get("content_type")).isEqualTo("application/pdf");

        Path storedPdf = RECEIPT_DIRECTORY.resolve("receipts")
                .resolve(receiptId + ".pdf");
        assertThat(Files.exists(storedPdf)).isTrue();
        byte[] storedBytes = Files.readAllBytes(storedPdf);
        assertThat(storedBytes).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(receiptRow.get("size_bytes")).isEqualTo((long) storedBytes.length);
        assertThat(receiptRow.get("checksum_sha256"))
                .isEqualTo(sha256(storedBytes));
        try (var pdf = Loader.loadPDF(storedBytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text)
                    .contains("PAYMENT RECEIPT")
                    .contains(receiptNumber)
                    .contains(planName)
                    .contains("USD 25.00")
                    .doesNotContain("secret")
                    .doesNotContain("4111111111111111");
        }

        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt", paymentId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receiptId.toString()))
                .andExpect(jsonPath("$.receiptNumber").value(receiptNumber))
                .andExpect(jsonPath("$.downloadUrl")
                        .value("/api/v1/payments/" + paymentId + "/receipt.pdf"));

        mockMvc.perform(get("/api/v1/payments/{paymentId}/receipt.pdf", paymentId)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(storedBytes))
                .andExpect(header().string("Content-Length", String.valueOf(storedBytes.length)))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/receipt", paymentId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(receiptId.toString()))
                .andExpect(jsonPath("$.receiptNumber").value(receiptNumber));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_receipts where payment_id = ?",
                Integer.class, paymentId)).isEqualTo(1);
        awaitReceiptAudit(receiptId);

        var paymentAfter = paymentRow(paymentId);
        assertThat(paymentAfter.get("status")).isEqualTo(paymentBefore.get("status"));
        assertThat(paymentAfter.get("amount")).isEqualTo(paymentBefore.get("amount"));
        assertThat(paymentAfter.get("currency")).isEqualTo(paymentBefore.get("currency"));
        assertThat(paymentAfter.get("payment_method"))
                .isEqualTo(paymentBefore.get("payment_method"));
        assertThat(paymentAfter.get("paid_at")).isEqualTo(paymentBefore.get("paid_at"));
        assertThat(paymentAfter.get("version")).isEqualTo(paymentBefore.get("version"));
    }

    @Test
    void concurrentGenerationCreatesOneReceiptAndOneArtifact() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID clientId = createClient(admin, uniqueEmail());
        UUID planId = createPlan(admin, uniqueName("Receipt-Concurrent-Plan"), "25.00", "USD");
        UUID membershipId = createMembership(admin, clientId, planId, null, "2026-09-01");
        UUID periodId = getMembershipPeriodId(membershipId);
        MvcResult paymentResult = mockMvc.perform(
                        post("/api/v1/payments")
                                .with(csrf())
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        clientId, membershipId, periodId,
                                        "25.00", "USD", "CASH", null,
                                        "2026-08-25T12:00:00Z")))
                .andExpect(status().isCreated())
                .andReturn();
        UUID paymentId = responseId(paymentResult);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent receipt test did not start.");
                }
                return mockMvc.perform(post(
                                "/api/v1/payments/{paymentId}/receipt", paymentId)
                                .with(csrf())
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn();
            };
            Future<MvcResult> first = executor.submit(request);
            Future<MvcResult> second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = awaitResult(first);
            MvcResult secondResult = awaitResult(second);
            assertThat(firstResult.getResponse().getStatus())
                    .withFailMessage("First receipt response: %s",
                            firstResult.getResponse().getContentAsString())
                    .isEqualTo(201);
            assertThat(secondResult.getResponse().getStatus())
                    .withFailMessage("Second receipt response: %s",
                            secondResult.getResponse().getContentAsString())
                    .isEqualTo(201);
            UUID firstReceiptId = responseId(firstResult);
            UUID secondReceiptId = responseId(secondResult);
            assertThat(secondReceiptId).isEqualTo(firstReceiptId);
            String firstReceiptNumber = JsonPath.read(
                    firstResult.getResponse().getContentAsString(), "$.receiptNumber");
            String secondReceiptNumber = JsonPath.read(
                    secondResult.getResponse().getContentAsString(), "$.receiptNumber");
            assertThat(firstReceiptNumber).isEqualTo(secondReceiptNumber);

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.payment_receipts where payment_id = ?",
                    Integer.class, paymentId)).isEqualTo(1);
            Path storedPdf = RECEIPT_DIRECTORY.resolve("receipts")
                    .resolve(firstReceiptId + ".pdf");
            assertThat(Files.exists(storedPdf)).isTrue();
            try (Stream<Path> files = Files.walk(RECEIPT_DIRECTORY)) {
                assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
            }
            awaitReceiptAudit(firstReceiptId);
        } finally {
            executor.shutdownNow();
        }
    }

    private static MvcResult awaitResult(Future<MvcResult> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(30, TimeUnit.SECONDS);
    }

    private void awaitReceiptAudit(UUID receiptId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int count;
        do {
            count = jdbcTemplate.queryForObject("""
                    select count(*) from gym.audit_entries
                    where action_code = 'PAYMENT_RECEIPT_GENERATED'
                      and resource_id = ?
                    """, Integer.class, receiptId);
            if (count == 1) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        } while (System.nanoTime() < deadline);
        assertThat(count).isEqualTo(1);
    }

    private BigDecimal dashboardRevenue(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .param("from", "2026-08-01")
                        .param("until", "2026-08-31")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        Object amount = JsonPath.read(
                result.getResponse().getContentAsString(), "$.payments.registeredAmount");
        return new BigDecimal(String.valueOf(amount));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
