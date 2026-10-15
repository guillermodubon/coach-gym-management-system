package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GoogleOAuthTokenClientTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService callers;
    private String tokenUrl;

    @AfterEach
    void stopServerAndExecutors() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (callers != null) {
            callers.shutdownNow();
        }
    }

    @Test
    void refreshesWithFormEncodedSecretsAndCachesOnlyTheAccessToken() throws Exception {
        AtomicReference<GmailHttpTestCapture.CapturedRequest> capturedRequest =
                new AtomicReference<>();
        startServer(exchange -> {
            capturedRequest.set(GmailHttpTestCapture.capture(exchange));
            respond(exchange, 200, tokenResponse("access-token-synthetic", 3_600));
        });
        GoogleOAuthTokenClient client = client(properties(
                "client id", "client secret/+", "refresh token&?", Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(2)), fixedClock());

        OAuthAccessToken first = client.accessToken();
        OAuthAccessToken cached = client.accessToken();

        assertThat(first.value()).isEqualTo("access-token-synthetic");
        assertThat(cached).isSameAs(first);
        assertThat(capturedRequest.get().method()).isEqualTo("POST");
        assertThat(capturedRequest.get().path()).isEqualTo("/token");
        assertThat(capturedRequest.get().contentType())
                .startsWith("application/x-www-form-urlencoded");
        assertThat(capturedRequest.get().bodyUtf8())
                .contains("grant_type=refresh_token")
                .contains("client_id=client+id")
                .contains("client_secret=client+secret%2F%2B")
                .contains("refresh_token=refresh+token%26%3F");
        assertThat(capturedRequest.get().toString())
                .doesNotContain("client+secret", "refresh+token", "client-secret");
        assertThat(first.toString()).doesNotContain("access-token-synthetic");
    }

    @Test
    void refreshesWhenTokenEntersTheConfiguredExpirySafetyWindow() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            int call = requests.incrementAndGet();
            respond(exchange, 200, tokenResponse("access-token-" + call, 60));
        });
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GoogleOAuthTokenClient client = client(properties(
                "client-id", "client-secret", "refresh-token", Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(2)), clock);

        OAuthAccessToken first = client.accessToken();
        assertThat(client.accessToken()).isSameAs(first);
        clock.advance(Duration.ofSeconds(31));
        OAuthAccessToken refreshed = client.accessToken();

        assertThat(refreshed.value()).isEqualTo("access-token-2");
        assertThat(requests).hasValue(2);
    }

    @Test
    void forceRefreshBypassesTheCacheAndReplacesTheCurrentToken() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> respond(
                exchange, 200, tokenResponse("access-token-" + requests.incrementAndGet(), 3_600)));
        GoogleOAuthTokenClient client = client(validProperties(), fixedClock());

        OAuthAccessToken first = client.accessToken();
        OAuthAccessToken forced = client.forceRefresh();

        assertThat(first.value()).isEqualTo("access-token-1");
        assertThat(forced.value()).isEqualTo("access-token-2");
        assertThat(client.accessToken()).isSameAs(forced);
        assertThat(requests).hasValue(2);
    }

    @Test
    void concurrentCallersShareOneRefreshRequest() throws Exception {
        int callerCount = 10;
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            requestReceived.countDown();
            await(releaseResponse);
            respond(exchange, 200, tokenResponse("access-token-shared", 3_600));
        });
        GoogleOAuthTokenClient client = client(properties(
                "client-id", "client-secret", "refresh-token", Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(4)), Clock.systemUTC());
        callers = Executors.newFixedThreadPool(callerCount);
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch startCallers = new CountDownLatch(1);
        List<AtomicReference<Thread>> callerThreads = new ArrayList<>();
        List<Future<OAuthAccessToken>> futures = new ArrayList<>();
        for (int index = 0; index < callerCount; index++) {
            AtomicReference<Thread> callerThread = new AtomicReference<>();
            callerThreads.add(callerThread);
            futures.add(callers.submit(() -> {
                callerThread.set(Thread.currentThread());
                callersReady.countDown();
                await(startCallers);
                return client.accessToken();
            }));
        }

        assertThat(callersReady.await(2, TimeUnit.SECONDS)).isTrue();
        startCallers.countDown();
        assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
        awaitAtLeastParkedWaiters(callerThreads, callerCount - 1);
        releaseResponse.countDown();

        List<OAuthAccessToken> tokens = new ArrayList<>();
        for (Future<OAuthAccessToken> future : futures) {
            tokens.add(future.get(3, TimeUnit.SECONDS));
        }
        assertThat(tokens).allSatisfy(token -> assertThat(token.value()).isEqualTo("access-token-shared"));
        assertThat(requests).hasValue(1);
    }

    @Test
    void concurrentRefreshFailureIsSharedAndTranslatedWithoutProviderText() throws Exception {
        int callerCount = 6;
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            requestReceived.countDown();
            await(releaseResponse);
            respond(exchange, 400, "invalid_grant refresh-token-synthetic client-secret-synthetic");
        });
        GoogleOAuthTokenClient client = client(properties(
                "client-id", "client-secret-synthetic", "refresh-token-synthetic",
                Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofSeconds(60), Duration.ofSeconds(4)), fixedClock());
        callers = Executors.newFixedThreadPool(callerCount);
        CountDownLatch callersReady = new CountDownLatch(callerCount);
        CountDownLatch startCallers = new CountDownLatch(1);
        List<AtomicReference<Thread>> callerThreads = new ArrayList<>();
        List<Future<GoogleOAuthFailureCode>> futures = new ArrayList<>();
        for (int index = 0; index < callerCount; index++) {
            AtomicReference<Thread> callerThread = new AtomicReference<>();
            callerThreads.add(callerThread);
            futures.add(callers.submit(() -> {
                callerThread.set(Thread.currentThread());
                callersReady.countDown();
                await(startCallers);
                try {
                    client.accessToken();
                    return null;
                } catch (GoogleOAuthException exception) {
                    return exception.failureCode();
                }
            }));
        }

        assertThat(callersReady.await(2, TimeUnit.SECONDS)).isTrue();
        startCallers.countDown();
        assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();
        awaitAtLeastParkedWaiters(callerThreads, callerCount - 1);
        releaseResponse.countDown();

        for (Future<GoogleOAuthFailureCode> future : futures) {
            assertThat(future.get(3, TimeUnit.SECONDS))
                    .isEqualTo(GoogleOAuthFailureCode.AUTHENTICATION_FAILED);
        }
        assertThat(requests).hasValue(1);
    }

    @Test
    void waitersHaveABoundedWaitWhileTheRefreshOwnerIsBlocked() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        startServer(exchange -> {
            requestReceived.countDown();
            await(releaseResponse);
            respond(exchange, 200, tokenResponse("access-token-after-wait", 3_600));
        });
        GoogleOAuthTokenClient client = client(properties(
                "client-id", "client-secret", "refresh-token", Duration.ofSeconds(5),
                Duration.ofSeconds(3), Duration.ofSeconds(60), Duration.ofMillis(75)), fixedClock());
        callers = Executors.newSingleThreadExecutor();
        Future<OAuthAccessToken> owner = callers.submit(client::accessToken);
        assertThat(requestReceived.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(client::accessToken)
                .isInstanceOf(GoogleOAuthException.class)
                .satisfies(exception -> assertThat(((GoogleOAuthException) exception).failureCode())
                        .isEqualTo(GoogleOAuthFailureCode.REFRESH_WAIT_TIMEOUT));

        releaseResponse.countDown();
        assertThat(owner.get(3, TimeUnit.SECONDS).value()).isEqualTo("access-token-after-wait");
    }

    @Test
    void mapsOAuthStatusesWithoutIncludingProviderBodyOrSecrets() throws Exception {
        AtomicInteger status = new AtomicInteger(400);
        startServer(exchange -> respond(exchange, status.get(),
                "provider-body-with-client-secret-and-refresh-token-synthetic"));
        GoogleOAuthTokenClient client = client(validProperties(), fixedClock());

        assertFailure(client, GoogleOAuthFailureCode.AUTHENTICATION_FAILED);
        status.set(401);
        assertFailure(client, GoogleOAuthFailureCode.AUTHENTICATION_FAILED);
        status.set(403);
        assertFailure(client, GoogleOAuthFailureCode.AUTHENTICATION_FAILED);
        status.set(408);
        assertFailure(client, GoogleOAuthFailureCode.TIMEOUT);
        status.set(429);
        assertFailure(client, GoogleOAuthFailureCode.RATE_LIMITED);
        status.set(503);
        assertFailure(client, GoogleOAuthFailureCode.UNAVAILABLE);
    }

    @Test
    void rejectsMalformedDuplicateAndOversizedSuccessResponses() throws Exception {
        AtomicReference<byte[]> body = new AtomicReference<>("not-json".getBytes(StandardCharsets.UTF_8));
        startServer(exchange -> respond(exchange, 200, body.get()));
        GoogleOAuthTokenClient client = client(validProperties(), fixedClock());

        assertFailure(client, GoogleOAuthFailureCode.INVALID_RESPONSE);
        body.set(("{\"access_token\":\"token-one\",\"access_token\":\"token-two\","
                + "\"token_type\":\"Bearer\",\"expires_in\":3600}").getBytes(StandardCharsets.UTF_8));
        assertFailure(client, GoogleOAuthFailureCode.INVALID_RESPONSE);
        body.set(("{\"access_token\":\"token\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"padding\":\"" + "x".repeat(17_000) + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        assertFailure(client, GoogleOAuthFailureCode.INVALID_RESPONSE);
    }

    @Test
    void tokenRequestTimeoutIsMappedToASafeFailure() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, tokenResponse("access-token-late", 3_600));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client is expected to close the timed-out local request.
            }
        });
        GoogleOAuthTokenClient client = client(properties(
                "client-id", "client-secret", "refresh-token", Duration.ofSeconds(5),
                Duration.ofMillis(60), Duration.ofSeconds(60), Duration.ofSeconds(1)), fixedClock());

        assertFailure(client, GoogleOAuthFailureCode.TIMEOUT);
    }

    @Test
    void invalidConfigurationFailsBeforeAnyNetworkRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, tokenResponse("unused-token", 3_600));
        });
        GoogleOAuthProperties missingSecret = properties(
                "client-id", null, "refresh-token", Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofSeconds(60), Duration.ofSeconds(1));
        GoogleOAuthTokenClient client = client(missingSecret, fixedClock());

        assertThatThrownBy(client::accessToken)
                .isInstanceOf(GoogleOAuthException.class)
                .satisfies(exception -> assertThat(((GoogleOAuthException) exception).failureCode())
                        .isEqualTo(GoogleOAuthFailureCode.CONFIGURATION_INVALID));
        assertThat(requests).hasValue(0);
    }

    private void assertFailure(GoogleOAuthTokenClient client, GoogleOAuthFailureCode code) {
        assertThatThrownBy(client::accessToken)
                .isInstanceOf(GoogleOAuthException.class)
                .satisfies(exception -> {
                    GoogleOAuthException oauth = (GoogleOAuthException) exception;
                    assertThat(oauth.failureCode()).isEqualTo(code);
                    assertThat(oauth.getMessage() + oauth).doesNotContain(
                            "client-secret", "refresh-token", "provider-body", "access-token");
                    assertThat(oauth.getCause()).isNull();
                });
    }

    private GoogleOAuthTokenClient client(GoogleOAuthProperties properties, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        return new GoogleOAuthTokenClient(
                httpClient, mapper, properties, clock, true,
                new GmailOperationalMetrics(new SimpleMeterRegistry()));
    }

    private GoogleOAuthProperties validProperties() {
        return properties("client-id", "client-secret", "refresh-token",
                Duration.ofSeconds(5), Duration.ofSeconds(2),
                Duration.ofSeconds(60), Duration.ofSeconds(2));
    }

    private GoogleOAuthProperties properties(
            String clientId,
            String clientSecret,
            String refreshToken,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration expirySafetyMargin,
            Duration refreshWaitTimeout) {
        if (tokenUrl == null) {
            throw new IllegalStateException("Local OAuth test URL was not initialized.");
        }
        return new GoogleOAuthProperties(this.tokenUrl, clientId, clientSecret, refreshToken,
                connectTimeout, requestTimeout, expirySafetyMargin, refreshWaitTimeout);
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "local-oauth-test-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
        server.createContext("/token", handler);
        server.start();
        tokenUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    private static void respond(HttpExchange exchange, int status, String response) throws IOException {
        respond(exchange, status, response.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] response) throws IOException {
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
        } finally {
            exchange.close();
        }
    }

    private static String tokenResponse(String token, long lifetimeSeconds) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\","
                + "\"expires_in\":" + lifetimeSeconds + ",\"scope\":\"ignored\"}";
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Local OAuth test latch expired.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Local OAuth test was interrupted.");
        }
    }

    private static void awaitAtLeastParkedWaiters(
            List<AtomicReference<Thread>> callerThreads, int expectedWaiters)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            long parked = callerThreads.stream()
                    .map(AtomicReference::get)
                    .filter(java.util.Objects::nonNull)
                    .filter(thread -> thread.getState() == Thread.State.WAITING
                            || thread.getState() == Thread.State.TIMED_WAITING)
                    .count();
            if (parked >= expectedWaiters) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("Concurrent OAuth callers did not reach the shared refresh wait.");
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
