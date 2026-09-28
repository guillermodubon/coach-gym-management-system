package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

/** Gmail HTTPS transport selected through the provider-neutral email sender boundary. */
final class GmailApiEmailSenderAdapter implements EmailSender {

    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Pattern PROVIDER_MESSAGE_ID = Pattern.compile("[A-Za-z0-9_-]{1,200}");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GmailApiProperties apiProperties;
    private final GoogleOAuthTokenClient tokenClient;
    private final GmailMimeEncoder mimeEncoder;
    private final boolean allowLoopbackHttp;
    private final GmailOperationalMetrics metrics;

    GmailApiEmailSenderAdapter(
            HttpClient httpClient,
            GmailApiProperties apiProperties,
            GoogleOAuthTokenClient tokenClient,
            GmailOperationalMetrics metrics) {
        this(httpClient, apiProperties, tokenClient, false, metrics);
    }

    GmailApiEmailSenderAdapter(
            HttpClient httpClient,
            GmailApiProperties apiProperties,
            GoogleOAuthTokenClient tokenClient,
            boolean allowLoopbackHttp,
            GmailOperationalMetrics metrics) {
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required.");
        this.apiProperties = Objects.requireNonNull(apiProperties, "Gmail API properties are required.");
        this.tokenClient = Objects.requireNonNull(tokenClient, "OAuth token client is required.");
        this.allowLoopbackHttp = allowLoopbackHttp;
        this.metrics = Objects.requireNonNull(metrics, "Gmail metrics are required.");
        this.mimeEncoder = new GmailMimeEncoder();
        this.objectMapper = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
    }

    static HttpClient createHttpClient(GmailApiProperties properties) {
        Objects.requireNonNull(properties, "Gmail API properties are required.");
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        long startedAtNanos = System.nanoTime();
        EmailSendResult result = null;
        try {
            result = sendInternal(message);
            return result;
        } finally {
            metrics.recordGmailSend(result, startedAtNanos);
        }
    }

    private EmailSendResult sendInternal(EmailMessage message) {
        if (message == null) {
            return failed(EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message is required.");
        }
        boolean validApiConfiguration = allowLoopbackHttp
                ? apiProperties.isValidForLoopbackStub()
                : apiProperties.isValidWhenEnabled();
        if (!validApiConfiguration) {
            return failed(EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "Gmail API configuration is invalid.");
        }

        final byte[] mime;
        try {
            mime = mimeEncoder.encode(message, apiProperties.senderAddress());
        } catch (GmailMimeEncodingException exception) {
            return failed(EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message is invalid for Gmail delivery.");
        }
        if (mime.length > apiProperties.maxMessageBytes()) {
            return failed(EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message exceeds the configured size limit.");
        }

        final byte[] requestBody;
        try {
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime);
            requestBody = objectMapper.writeValueAsBytes(Map.of("raw", raw));
        } catch (IOException | IllegalArgumentException exception) {
            return failed(EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The email message could not be prepared for Gmail delivery.");
        }

        final String firstToken;
        try {
            firstToken = tokenClient.accessToken().value();
        } catch (GoogleOAuthException exception) {
            return mapOAuthFailure(exception);
        }

        Submission firstSubmission = submit(firstToken, requestBody);
        if (firstSubmission.result() != null) {
            return firstSubmission.result();
        }
        HttpResponse<byte[]> firstResponse = firstSubmission.response();
        if (firstResponse.statusCode() != 401) {
            return mapGmailResponse(firstResponse);
        }

        // A 401 is a definitive rejection. Refresh once and resubmit once; no other
        // status or transport failure is retried because Gmail may have accepted it.
        final String refreshedToken;
        try {
            refreshedToken = tokenClient.forceRefresh().value();
        } catch (GoogleOAuthException exception) {
            return mapOAuthFailure(exception);
        }
        Submission retrySubmission = submit(refreshedToken, requestBody);
        if (retrySubmission.result() != null) {
            return retrySubmission.result();
        }
        return mapGmailResponse(retrySubmission.response());
    }

    private Submission submit(String accessToken, byte[] requestBody) {
        final HttpRequest request;
        try {
            URI endpoint = URI.create(apiProperties.apiBaseUrl())
                    .resolve("/gmail/v1/users/me/messages/send");
            Duration boundedRequestTimeout = apiProperties.readTimeout()
                    .compareTo(apiProperties.writeTimeout()) >= 0
                    ? apiProperties.readTimeout()
                    : apiProperties.writeTimeout();
            request = HttpRequest.newBuilder(endpoint)
                    .timeout(boundedRequestTimeout)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
        } catch (IllegalArgumentException exception) {
            return Submission.failed(failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The Gmail API request could not be constructed."));
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(request, responseInfo -> {
                int status = responseInfo.statusCode();
                if (status >= 200 && status < 300) {
                    return new LimitedBodySubscriber(MAX_RESPONSE_BYTES);
                }
                return HttpResponse.BodySubscribers.replacing(new byte[0]);
            });
            return Submission.completed(response);
        } catch (HttpConnectTimeoutException exception) {
            return Submission.failed(failed(
                    EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                    "The Gmail API connection timed out before submission."));
        } catch (IOException exception) {
            return Submission.failed(EmailSendResult.ambiguous(
                    "The Gmail delivery outcome could not be confirmed."));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Submission.failed(EmailSendResult.ambiguous(
                    "The Gmail delivery outcome could not be confirmed after interruption."));
        } catch (RuntimeException exception) {
            return Submission.failed(EmailSendResult.ambiguous(
                    "The Gmail delivery outcome could not be confirmed."));
        }
    }

    private EmailSendResult mapGmailResponse(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            String messageId = parseMessageId(response.body());
            return messageId == null
                    ? EmailSendResult.ambiguous(
                            "Gmail may have accepted the message, but its identifier was invalid.")
                    : EmailSendResult.sent(messageId);
        }
        if (status == 401 || status == 403) {
            return failed(EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED,
                    "Gmail rejected the delivery credentials or sender authority.");
        }
        if (status == 408 || status >= 500) {
            return EmailSendResult.ambiguous(
                    "Gmail returned an uncertain response before acceptance could be confirmed.");
        }
        if (status >= 400 && status < 500) {
            return failed(EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                    "Gmail rejected the email request.");
        }
        return failed(EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                "Gmail returned an unsupported response.");
    }

    private String parseMessageId(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0
                || responseBody.length > MAX_RESPONSE_BYTES) {
            return null;
        }
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode id = response == null ? null : response.get("id");
            if (id == null || !id.isTextual()) {
                return null;
            }
            String value = id.textValue();
            return PROVIDER_MESSAGE_ID.matcher(value).matches() ? value : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static EmailSendResult mapOAuthFailure(GoogleOAuthException exception) {
        return switch (exception.failureCode()) {
            case AUTHENTICATION_FAILED -> failed(
                    EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED,
                    "Gmail OAuth authentication failed.");
            case RATE_LIMITED -> failed(
                    EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                    "Google OAuth temporarily limited token requests.");
            case TIMEOUT, REFRESH_WAIT_TIMEOUT, INTERRUPTED -> failed(
                    EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                    "Gmail OAuth could not provide a token before the request deadline.");
            case CONFIGURATION_INVALID -> failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "Gmail OAuth configuration is invalid.");
            case INVALID_RESPONSE, UNAVAILABLE -> failed(
                    EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                    "Gmail OAuth could not provide a valid access token.");
        };
    }

    private static EmailSendResult failed(
            EmailDeliveryFailureCode code, String safeMessage) {
        return EmailSendResult.failed(code, safeMessage);
    }

    private record Submission(HttpResponse<byte[]> response, EmailSendResult result) {

        private static Submission completed(HttpResponse<byte[]> response) {
            return new Submission(Objects.requireNonNull(response), null);
        }

        private static Submission failed(EmailSendResult result) {
            return new Submission(null, Objects.requireNonNull(result));
        }
    }

    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {

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
                    completion.completeExceptionally(new IOException());
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                body.write(chunk, 0, chunk.length);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) {
            completion.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            completion.complete(body.toByteArray());
        }
    }
}
