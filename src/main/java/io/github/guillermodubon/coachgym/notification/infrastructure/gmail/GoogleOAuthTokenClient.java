package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Refreshes and caches short-lived OAuth tokens only in process memory. */
final class GoogleOAuthTokenClient {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final long MAX_TOKEN_LIFETIME_SECONDS = 86_400;
    private static final Pattern BEARER_TOKEN = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GoogleOAuthProperties properties;
    private final GoogleOAuthErrorMapper errorMapper;
    private final Clock clock;
    private final boolean allowLoopbackHttp;
    private final GmailOperationalMetrics metrics;
    private final AtomicReference<CompletableFuture<OAuthAccessToken>> refreshInFlight =
            new AtomicReference<>();
    private volatile OAuthAccessToken cachedToken;

    GoogleOAuthTokenClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GoogleOAuthProperties properties,
            Clock clock,
            GmailOperationalMetrics metrics) {
        this(httpClient, objectMapper, properties, clock, false, metrics);
    }

    GoogleOAuthTokenClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GoogleOAuthProperties properties,
            Clock clock,
            boolean allowLoopbackHttp,
            GmailOperationalMetrics metrics) {
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper is required.");
        this.properties = Objects.requireNonNull(properties, "OAuth properties are required.");
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.allowLoopbackHttp = allowLoopbackHttp;
        this.metrics = Objects.requireNonNull(metrics, "Gmail metrics are required.");
        this.errorMapper = new GoogleOAuthErrorMapper();
    }

    OAuthAccessToken accessToken() {
        return refresh(false);
    }

    /** Invalidates the local token and forces a coordinated refresh after a definitive 401. */
    OAuthAccessToken forceRefresh() {
        cachedToken = null;
        return refresh(true);
    }

    private OAuthAccessToken refresh(boolean forced) {
        if (!properties.isValidWhenEnabled(allowLoopbackHttp)) {
            metrics.recordOAuthRefresh(
                    false, GoogleOAuthFailureCode.CONFIGURATION_INVALID, System.nanoTime());
            throw new GoogleOAuthException(GoogleOAuthFailureCode.CONFIGURATION_INVALID);
        }
        CompletableFuture<OAuthAccessToken> refresh;
        boolean refreshOwner;
        while (true) {
            if (!forced) {
                OAuthAccessToken current = cachedToken;
                if (current != null
                        && current.usableAt(clock.instant(), properties.expirySafetyMargin())) {
                    return current;
                }
            }

            refresh = refreshInFlight.get();
            if (refresh != null) {
                refreshOwner = false;
                break;
            }

            CompletableFuture<OAuthAccessToken> candidate = new CompletableFuture<>();
            if (refreshInFlight.compareAndSet(null, candidate)) {
                refresh = candidate;
                refreshOwner = true;
                break;
            }
        }

        if (refreshOwner) {
            try {
                OAuthAccessToken token = requestToken();
                cachedToken = token;
                refresh.complete(token);
            } catch (GoogleOAuthException exception) {
                refresh.completeExceptionally(exception);
            } catch (RuntimeException exception) {
                refresh.completeExceptionally(
                        new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE));
            } finally {
                refreshInFlight.compareAndSet(refresh, null);
            }
        }
        return await(refresh);
    }

    private OAuthAccessToken await(CompletableFuture<OAuthAccessToken> refresh) {
        try {
            return refresh.get(properties.refreshWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GoogleOAuthException(GoogleOAuthFailureCode.INTERRUPTED);
        } catch (TimeoutException exception) {
            throw new GoogleOAuthException(GoogleOAuthFailureCode.REFRESH_WAIT_TIMEOUT);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof GoogleOAuthException oauthException) {
                throw oauthException;
            }
            throw new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
        }
    }

    private OAuthAccessToken requestToken() {
        long startedAtNanos = System.nanoTime();
        try {
            OAuthAccessToken token = requestTokenWithoutMetrics();
            metrics.recordOAuthRefresh(true, null, startedAtNanos);
            return token;
        } catch (GoogleOAuthException exception) {
            metrics.recordOAuthRefresh(false, exception.failureCode(), startedAtNanos);
            throw exception;
        }
    }

    private OAuthAccessToken requestTokenWithoutMetrics() {
        final URI endpoint;
        final HttpRequest request;
        try {
            endpoint = URI.create(properties.tokenUrl());
            String form = "grant_type=refresh_token"
                    + "&client_id=" + formEncode(properties.clientId())
                    + "&client_secret=" + formEncode(properties.clientSecret())
                    + "&refresh_token=" + formEncode(properties.refreshToken());
            request = HttpRequest.newBuilder(endpoint)
                    .timeout(properties.requestTimeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new GoogleOAuthException(GoogleOAuthFailureCode.CONFIGURATION_INVALID);
        }

        final HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, info -> new LimitedBodySubscriber(MAX_RESPONSE_BYTES));
        } catch (HttpTimeoutException exception) {
            throw new GoogleOAuthException(GoogleOAuthFailureCode.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GoogleOAuthException(GoogleOAuthFailureCode.INTERRUPTED);
        } catch (IOException exception) {
            if (hasCause(exception, ResponseTooLargeException.class)) {
                throw new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
            }
            throw new GoogleOAuthException(GoogleOAuthFailureCode.UNAVAILABLE);
        } catch (RuntimeException exception) {
            throw new GoogleOAuthException(GoogleOAuthFailureCode.UNAVAILABLE);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw errorMapper.fromStatus(response.statusCode());
        }
        return parseToken(response.body());
    }

    private OAuthAccessToken parseToken(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
            throw new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
        }
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode token = response == null ? null : response.get("access_token");
            JsonNode tokenType = response == null ? null : response.get("token_type");
            JsonNode lifetime = response == null ? null : response.get("expires_in");
            if (token == null || !token.isTextual() || !BEARER_TOKEN.matcher(token.textValue()).matches()
                    || token.textValue().length() > 8_192
                    || tokenType == null || !tokenType.isTextual()
                    || !"bearer".equalsIgnoreCase(tokenType.textValue())
                    || lifetime == null || !lifetime.isIntegralNumber()
                    || !lifetime.canConvertToLong()) {
                throw new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
            }
            long seconds = lifetime.longValue();
            if (seconds < 1 || seconds > MAX_TOKEN_LIFETIME_SECONDS) {
                throw new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
            }
            return new OAuthAccessToken(token.textValue(), clock.instant().plusSeconds(seconds));
        } catch (IOException | ArithmeticException exception) {
            throw new GoogleOAuthException(GoogleOAuthFailureCode.INVALID_RESPONSE);
        }
    }

    private static String formEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> expected) {
        Throwable current = throwable;
        while (current != null) {
            if (expected.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> completion = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return completion;
        }

        @Override
        public void onSubscribe(Flow.Subscription candidate) {
            if (subscription != null) {
                candidate.cancel();
                return;
            }
            subscription = candidate;
            candidate.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (body.size() + (long) length > maxBytes) {
                    subscription.cancel();
                    completion.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                body.write(chunk, 0, chunk.length);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            completion.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            completion.complete(body.toByteArray());
        }
    }

    private static final class ResponseTooLargeException extends IOException {
        private ResponseTooLargeException() {
            super("OAuth response exceeded the configured safety bound.");
        }
    }
}
