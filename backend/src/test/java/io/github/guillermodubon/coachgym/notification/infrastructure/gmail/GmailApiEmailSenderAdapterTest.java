package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GmailApiEmailSenderAdapterTest {

    private static final String SENDER = "coach-gym@example.test";
    private static final String CLIENT_SECRET = "synthetic-client-secret";
    private static final String REFRESH_TOKEN = "synthetic-refresh-token";
    private static final String ACCESS_TOKEN_PREFIX = "synthetic-access-token-";

    private final ObjectMapper objectMapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());
    private final GmailOperationalMetrics metrics =
            new GmailOperationalMetrics(new SimpleMeterRegistry());
    private HttpServer server;
    private java.util.concurrent.ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void sendsUrlSafeBase64MimeAndReturnsOnlyAValidatedProviderMessageId() throws Exception {
        AtomicInteger sendCount = new AtomicInteger();
        AtomicInteger tokenCount = new AtomicInteger();
        AtomicReference<GmailHttpTestCapture.CapturedRequest> capturedRequest =
                new AtomicReference<>();
        startServer(exchange -> {
            sendCount.incrementAndGet();
            capturedRequest.set(GmailHttpTestCapture.capture(exchange));
            respond(exchange, 200, "{\"id\":\"gmail-message_123\"}");
        }, tokenCount);

        EmailSendResult result = adapter().send(message());
        GmailHttpTestCapture.CapturedRequest request = capturedRequest.get();
        JsonNode payload = objectMapper.readTree(request.body());
        String raw = payload.path("raw").asText();
        byte[] mime = Base64.getUrlDecoder().decode(raw);

        assertThat(result.result()).isEqualTo(EmailAttemptResult.SENT);
        assertThat(result.providerMessageId()).isEqualTo("gmail-message_123");
        assertThat(sendCount).hasValue(1);
        assertThat(tokenCount).hasValue(1);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.authorization()).isEqualTo("Bearer " + ACCESS_TOKEN_PREFIX + "1");
        assertThat(request.path()).isEqualTo("/gmail/v1/users/me/messages/send");
        assertThat(request.accept()).isEqualTo("application/json");
        assertThat(request.contentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(raw).matches("[A-Za-z0-9_-]+").doesNotContain("=");
        jakarta.mail.internet.MimeMessage parsed = parse(mime);
        jakarta.mail.Multipart mixed = (jakarta.mail.Multipart) parsed.getContent();
        assertThat(parsed.getSubject()).isEqualTo("Payment receipt");
        assertThat(parsed.getFrom())
                .extracting(address -> ((jakarta.mail.internet.InternetAddress) address).getAddress())
                .containsExactly(SENDER);
        assertThat(parsed.getRecipients(jakarta.mail.Message.RecipientType.TO))
                .extracting(address -> ((jakarta.mail.internet.InternetAddress) address).getAddress())
                .containsExactly("ana@example.test");
        jakarta.mail.Multipart alternative =
                (jakarta.mail.Multipart) mixed.getBodyPart(0).getContent();
        assertThat(alternative.getBodyPart(0).getContent().toString())
                .contains("Payment confirmed");
        assertThat(alternative.getBodyPart(1).getContent().toString())
                .contains("<p>Payment confirmed</p>");
        assertThat(mixed.getBodyPart(1).getFileName()).isEqualTo("receipt-á.pdf");
        assertThat(mixed.getBodyPart(1).getInputStream().readAllBytes())
                .containsExactly((byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46);
        assertThat(new String(mime, StandardCharsets.ISO_8859_1))
                .doesNotContain(CLIENT_SECRET, REFRESH_TOKEN);
        assertThat(request.bodyUtf8())
                .doesNotContain(CLIENT_SECRET, REFRESH_TOKEN);
        assertThat(request.toString())
                .doesNotContain(ACCESS_TOKEN_PREFIX, CLIENT_SECRET, REFRESH_TOKEN, "raw");
    }

    @Test
    void credentialPngBytesSurviveTheCapturedGmailRequestDecode() throws Exception {
        AtomicReference<GmailHttpTestCapture.CapturedRequest> capturedRequest =
                new AtomicReference<>();
        AtomicInteger tokenCount = new AtomicInteger();
        startServer(exchange -> {
            capturedRequest.set(GmailHttpTestCapture.capture(exchange));
            respond(exchange, 200, "{\"id\":\"png-message\"}");
        }, tokenCount);
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        EmailMessage credentialMessage = message(new EmailAttachment(
                "credencial-ñ.png", "image/png", pngBytes));

        EmailSendResult result = adapter().send(credentialMessage);
        JsonNode payload = objectMapper.readTree(capturedRequest.get().body());
        byte[] mime = Base64.getUrlDecoder().decode(payload.path("raw").asText());
        jakarta.mail.Multipart mixed = (jakarta.mail.Multipart) parse(mime).getContent();

        assertThat(result.result()).isEqualTo(EmailAttemptResult.SENT);
        assertThat(mixed.getBodyPart(1).getFileName()).isEqualTo("credencial-ñ.png");
        assertThat(mixed.getBodyPart(1).getContentType().toLowerCase())
                .startsWith("image/png");
        assertThat(mixed.getBodyPart(1).getInputStream().readAllBytes())
                .containsExactly(pngBytes);
        assertThat(new String(mime, StandardCharsets.ISO_8859_1))
                .doesNotContain("access-credentials/", "storage_key");
    }

    @Test
    void identityTokenLinkAppearsOnlyInTheApprovedMessageBodies() throws Exception {
        AtomicReference<GmailHttpTestCapture.CapturedRequest> capturedRequest =
                new AtomicReference<>();
        AtomicInteger tokenCount = new AtomicInteger();
        startServer(exchange -> {
            capturedRequest.set(GmailHttpTestCapture.capture(exchange));
            respond(exchange, 200, "{\"id\":\"identity-message\"}");
        }, tokenCount);
        String invitationLink =
                "https://app.example.test/accept-invitation?token=synthetic-invitation-token";
        EmailMessage identityMessage = new EmailMessage(
                "invitee@example.test", SENDER, "Coach Gym", null,
                "Invitation to Coach Gym", "Accept the invitation: " + invitationLink,
                "<a href=\"" + invitationLink + "\">Accept invitation</a>", null);

        EmailSendResult result = adapter().send(identityMessage);
        GmailHttpTestCapture.CapturedRequest request = capturedRequest.get();
        JsonNode payload = objectMapper.readTree(request.body());
        String encodedRaw = payload.path("raw").asText();
        byte[] mime = Base64.getUrlDecoder().decode(encodedRaw);
        jakarta.mail.internet.MimeMessage parsed = parse(mime);
        jakarta.mail.Multipart alternative = (jakarta.mail.Multipart) parsed.getContent();
        String headers = String.join("\n", java.util.Collections.list(parsed.getAllHeaderLines()));

        assertThat(result.result()).isEqualTo(EmailAttemptResult.SENT);
        assertThat(payload.size()).isEqualTo(1);
        assertThat(request.bodyUtf8()).doesNotContain(invitationLink, "synthetic-invitation-token");
        assertThat(((jakarta.mail.internet.InternetAddress) parsed.getFrom()[0]).getAddress())
                .isEqualTo(SENDER);
        assertThat(parsed.getRecipients(jakarta.mail.Message.RecipientType.TO))
                .extracting(address -> ((jakarta.mail.internet.InternetAddress) address).getAddress())
                .containsExactly("invitee@example.test");
        assertThat(parsed.getSubject()).isEqualTo("Invitation to Coach Gym");
        assertThat(headers).doesNotContain(invitationLink, "synthetic-invitation-token");
        assertThat(alternative.getBodyPart(0).getContent().toString()).contains(invitationLink);
        assertThat(alternative.getBodyPart(1).getContent().toString()).contains(invitationLink);
        assertThat(new String(mime, StandardCharsets.ISO_8859_1))
                .doesNotContain("access-credentials/", "storage_key");
    }

    @Test
    void retriesOnlyOnceWhenTheRefreshedCredentialIsAlsoRejected() throws Exception {
        AtomicInteger sendCount = new AtomicInteger();
        AtomicInteger tokenCount = new AtomicInteger();
        startServer(exchange -> {
            sendCount.incrementAndGet();
            respond(exchange, 401, "synthetic unauthorized response");
        }, tokenCount);

        EmailSendResult result = adapter().send(message());

        assertThat(result.result()).isEqualTo(EmailAttemptResult.FAILED);
        assertThat(result.failureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED);
        assertThat(sendCount).hasValue(2);
        assertThat(tokenCount).hasValue(2);
    }

    @Test
    void oversizedSuccessfulProviderResponseIsBoundedAndClassifiedAsAmbiguous()
            throws Exception {
        AtomicInteger sendCount = new AtomicInteger();
        AtomicInteger tokenCount = new AtomicInteger();
        String oversizedResponse = "{\"id\":\"bounded-id\",\"padding\":\""
                + "x".repeat(65_536) + "\"}";
        startServer(exchange -> {
            sendCount.incrementAndGet();
            try {
                respond(exchange, 200, oversizedResponse);
            } catch (IOException ignored) {
                // The adapter may cancel the local response as soon as its bound is exceeded.
            }
        }, tokenCount);

        EmailSendResult result = adapter().send(message());

        assertThat(result.result()).isEqualTo(EmailAttemptResult.AMBIGUOUS);
        assertThat(result.failureCode())
                .isEqualTo(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME);
        assertThat(sendCount).hasValue(1);
        assertThat(tokenCount).hasValue(1);
    }

    @Test
    void refreshesAndRetriesOnceOnlyAfterDefinitiveUnauthorizedResponse() throws Exception {
        AtomicInteger sendCount = new AtomicInteger();
        AtomicInteger tokenCount = new AtomicInteger();
        AtomicReference<String> firstAuthorization = new AtomicReference<>();
        AtomicReference<String> secondAuthorization = new AtomicReference<>();
        startServer(exchange -> {
            if (sendCount.incrementAndGet() == 1) {
                firstAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, 401, "{}");
            } else {
                secondAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, 200, "{\"id\":\"accepted-after-refresh\"}");
            }
        }, tokenCount);

        EmailSendResult result = adapter().send(message());

        assertThat(result.result()).isEqualTo(EmailAttemptResult.SENT);
        assertThat(sendCount).hasValue(2);
        assertThat(tokenCount).hasValue(2);
        assertThat(firstAuthorization.get()).isEqualTo("Bearer " + ACCESS_TOKEN_PREFIX + "1");
        assertThat(secondAuthorization.get()).isEqualTo("Bearer " + ACCESS_TOKEN_PREFIX + "2");
    }

    @Test
    void mapsDefinitiveRejectionsAndUncertainProviderStatusesWithoutRetrying() throws Exception {
        AtomicInteger sendCount = new AtomicInteger();
        AtomicInteger tokenCount = new AtomicInteger();
        AtomicInteger status = new AtomicInteger(400);
        startServer(exchange -> {
            sendCount.incrementAndGet();
            respond(exchange, status.get(), "provider response must not escape");
        }, tokenCount);

        assertResultForStatus(status, EmailAttemptResult.FAILED,
                EmailDeliveryFailureCode.TRANSPORT_REJECTED, 400);
        assertResultForStatus(status, EmailAttemptResult.FAILED,
                EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED, 403);
        assertResultForStatus(status, EmailAttemptResult.FAILED,
                EmailDeliveryFailureCode.TRANSPORT_REJECTED, 429);
        assertResultForStatus(status, EmailAttemptResult.AMBIGUOUS,
                EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME, 408);
        assertResultForStatus(status, EmailAttemptResult.AMBIGUOUS,
                EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME, 503);

        assertThat(sendCount).hasValue(5);
        assertThat(tokenCount).hasValue(5);
    }

    @Test
    void treatsMalformedSuccessAndTransportTimeoutAsAmbiguous() throws Exception {
        AtomicInteger tokenCount = new AtomicInteger();
        AtomicInteger sendCount = new AtomicInteger();
        AtomicReference<Boolean> timeout = new AtomicReference<>(false);
        startServer(exchange -> {
            sendCount.incrementAndGet();
            if (timeout.get()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            } else {
                respond(exchange, 200, "{\"id\":\"not safe!\"}");
            }
        }, tokenCount);

        EmailSendResult malformed = adapter().send(message());
        timeout.set(true);
        EmailSendResult timedOut = adapter(Duration.ofMillis(40), 12 * 1024 * 1024)
                .send(message());

        assertThat(malformed.result()).isEqualTo(EmailAttemptResult.AMBIGUOUS);
        assertThat(timedOut.result()).isEqualTo(EmailAttemptResult.AMBIGUOUS);
        assertThat(sendCount).hasValue(2);
        assertThat(tokenCount).hasValue(2);
    }

    @Test
    void rejectsOversizedMimeBeforeAnyGmailSubmission() throws Exception {
        AtomicInteger sendCount = new AtomicInteger();
        AtomicInteger tokenCount = new AtomicInteger();
        startServer(exchange -> {
            sendCount.incrementAndGet();
            respond(exchange, 200, "{\"id\":\"unexpected\"}");
        }, tokenCount);

        EmailMessage largeMessage = new EmailMessage(
                "ana@example.test", SENDER, "Coach Gym", null,
                "Large message", "x".repeat(4_000), "<p>" + "x".repeat(4_000) + "</p>", null);
        EmailSendResult result = adapter(Duration.ofSeconds(2), 1_024).send(largeMessage);

        assertThat(result.result()).isEqualTo(EmailAttemptResult.FAILED);
        assertThat(result.failureCode()).isEqualTo(EmailDeliveryFailureCode.VALIDATION_FAILED);
        assertThat(sendCount).hasValue(0);
        assertThat(tokenCount).hasValue(0);
    }

    @Test
    void providerResponseBodiesAndOAuthSecretsNeverAppearInResultText() throws Exception {
        AtomicInteger tokenCount = new AtomicInteger();
        startServer(exchange -> respond(exchange, 403,
                "provider body with " + CLIENT_SECRET + " and " + REFRESH_TOKEN), tokenCount);

        EmailSendResult result = adapter().send(message());

        assertThat(result.failureMessage())
                .doesNotContain(CLIENT_SECRET, REFRESH_TOKEN, "provider body");
        assertThat(result.toString())
                .doesNotContain(CLIENT_SECRET, REFRESH_TOKEN, "provider body");
    }

    private void assertResultForStatus(
            AtomicInteger status,
            EmailAttemptResult expectedResult,
            EmailDeliveryFailureCode expectedFailure,
            int responseStatus) {
        status.set(responseStatus);
        EmailSendResult result = adapter().send(message());
        assertThat(result.result()).isEqualTo(expectedResult);
        assertThat(result.failureCode()).isEqualTo(expectedFailure);
        assertThat(result.failureMessage()).doesNotContain("provider response");
    }

    private GmailApiEmailSenderAdapter adapter() {
        return adapter(Duration.ofSeconds(2), 12 * 1024 * 1024);
    }

    private GmailApiEmailSenderAdapter adapter(Duration requestTimeout, int maxMessageBytes) {
        int port = server.getAddress().getPort();
        String localBase = "http://127.0.0.1:" + port;
        GmailApiProperties api = new GmailApiProperties(
                localBase, SENDER, Duration.ofSeconds(1), requestTimeout,
                Duration.ofSeconds(1), maxMessageBytes);
        GoogleOAuthProperties oauth = new GoogleOAuthProperties(
                localBase + "/token", "synthetic-client-id", CLIENT_SECRET,
                REFRESH_TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        GoogleOAuthTokenClient tokenClient = new GoogleOAuthTokenClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                objectMapper, oauth,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")),
                true, metrics);
        return new GmailApiEmailSenderAdapter(
                GmailApiEmailSenderAdapter.createHttpClient(api), api, tokenClient, true, metrics);
    }

    private void startServer(
            com.sun.net.httpserver.HttpHandler sendHandler,
            AtomicInteger tokenCount) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "local-gmail-api-test-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
        server.createContext("/token", exchange -> {
            tokenCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "{\"access_token\":\"" + ACCESS_TOKEN_PREFIX
                    + tokenCount.get() + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        });
        server.createContext("/gmail/v1/users/me/messages/send", sendHandler);
        server.start();
    }

    private static EmailMessage message() {
        return message(new EmailAttachment(
                "receipt-á.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}));
    }

    private static EmailMessage message(EmailAttachment attachment) {
        return new EmailMessage(
                "ana@example.test", SENDER, "Coach Gym", null,
                "Payment receipt", "Payment confirmed", "<p>Payment confirmed</p>",
                attachment);
    }

    private static jakarta.mail.internet.MimeMessage parse(byte[] mime) throws Exception {
        return new jakarta.mail.internet.MimeMessage(
                jakarta.mail.Session.getInstance(new java.util.Properties()),
                new java.io.ByteArrayInputStream(mime));
    }

    private static void respond(HttpExchange exchange, int status, String response)
            throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }
}
