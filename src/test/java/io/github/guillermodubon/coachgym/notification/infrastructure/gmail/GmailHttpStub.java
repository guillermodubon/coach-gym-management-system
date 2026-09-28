package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Local HTTP-only Gmail/OAuth substitute for complete message-flow tests. */
final class GmailHttpStub implements AutoCloseable {

    static final String SENDER = "coach-gym@example.test";
    private static final String CLIENT_SECRET = "synthetic-client-secret";
    private static final String REFRESH_TOKEN = "synthetic-refresh-token";
    private static final String ACCESS_TOKEN = "synthetic-access-token";
    private static final String MESSAGE_ID_PREFIX = "stub-message-";

    private final ObjectMapper objectMapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());
    private final ConcurrentLinkedQueue<StubResponse> queuedResponses =
            new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<GmailHttpTestCapture.CapturedRequest> requests =
            new CopyOnWriteArrayList<>();
    private final AtomicInteger tokenCount = new AtomicInteger();
    private final AtomicInteger sendCount = new AtomicInteger();
    private final HttpServer server;
    private final ExecutorService executor;

    private GmailHttpStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "local-gmail-http-stub");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/token", this::issueAccessToken);
        server.createContext("/gmail/v1/users/me/messages/send", this::acceptEmail);
        server.start();
    }

    static GmailHttpStub start() throws IOException {
        return new GmailHttpStub();
    }

    EmailSender sender() {
        GmailOperationalMetrics metrics = new GmailOperationalMetrics(new SimpleMeterRegistry());
        String localBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        GmailApiProperties apiProperties = new GmailApiProperties(
                localBaseUrl, SENDER, Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(2), 12 * 1024 * 1024);
        GoogleOAuthProperties oauthProperties = new GoogleOAuthProperties(
                localBaseUrl + "/token", "synthetic-client-id", CLIENT_SECRET,
                REFRESH_TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        GoogleOAuthTokenClient tokenClient = new GoogleOAuthTokenClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                objectMapper,
                oauthProperties,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")),
                true,
                metrics);
        return new GmailApiEmailSenderAdapter(
                GmailApiEmailSenderAdapter.createHttpClient(apiProperties),
                apiProperties,
                tokenClient,
                true,
                metrics);
    }

    void respondToNextSend(int status, String safeTestBody) {
        queuedResponses.add(new StubResponse(status, safeTestBody));
    }

    int sendCount() {
        return sendCount.get();
    }

    int tokenCount() {
        return tokenCount.get();
    }

    List<GmailHttpTestCapture.CapturedRequest> requests() {
        return List.copyOf(requests);
    }

    byte[] decodeRawMessage(int requestIndex) throws IOException {
        var body = objectMapper.readTree(requests.get(requestIndex).body());
        return java.util.Base64.getUrlDecoder().decode(body.path("raw").asText());
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void issueAccessToken(HttpExchange exchange) throws IOException {
        tokenCount.incrementAndGet();
        exchange.getRequestBody().readNBytes(16 * 1024);
        respond(exchange, 200,
                "{\"access_token\":\"" + ACCESS_TOKEN
                        + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    }

    private void acceptEmail(HttpExchange exchange) throws IOException {
        GmailHttpTestCapture.CapturedRequest captured = GmailHttpTestCapture.capture(exchange);
        requests.add(captured);
        int number = sendCount.incrementAndGet();
        StubResponse response = queuedResponses.poll();
        if (response == null) {
            response = new StubResponse(200,
                    "{\"id\":\"" + MESSAGE_ID_PREFIX + number + "\"}");
        }
        respond(exchange, response.status(), response.body());
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

    private record StubResponse(int status, String body) {}
}
