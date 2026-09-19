package io.github.guillermodubon.coachgym.notification.infrastructure.resend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.EmailProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Resend HTTPS adapter. Provider payloads and credentials stop at this
 * infrastructure boundary; callers receive only the provider-neutral result.
 */
public final class ResendEmailSender implements EmailSender {

    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EmailProperties emailProperties;
    private final ResendProperties resendProperties;

    public ResendEmailSender(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            EmailProperties emailProperties,
            ResendProperties resendProperties) {
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper is required.");
        this.emailProperties = Objects.requireNonNull(emailProperties, "Email properties are required.");
        this.resendProperties = Objects.requireNonNull(resendProperties, "Resend properties are required.");
    }

    public static HttpClient createHttpClient(ResendProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (message == null) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message is required.");
        }
        if (!emailProperties.enabled()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.DELIVERY_DISABLED,
                    "Transactional email delivery is disabled.");
        }
        if (!"resend".equals(emailProperties.provider())
                || !emailProperties.isValidWhenEnabled()
                || !resendProperties.isValidWhenEnabled()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "Transactional email configuration is invalid.");
        }
        if (estimatedSize(message) > emailProperties.maxMessageBytes()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message exceeds the configured size limit.");
        }

        final byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(toPayload(message));
        } catch (IOException | IllegalArgumentException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The email message could not be prepared for HTTPS delivery.");
        }
        if (payload.length > emailProperties.maxMessageBytes()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message exceeds the configured size limit.");
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(resendProperties.endpoint()))
                    .timeout(resendProperties.requestTimeout())
                    .header("Authorization", "Bearer " + resendProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
        } catch (IllegalArgumentException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The email provider endpoint is invalid.");
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body() == null || response.body().length > MAX_RESPONSE_BYTES) {
                return EmailSendResult.failed(
                        EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                        "The email provider returned an invalid response.");
            }
            return mapResponse(response);
        } catch (HttpTimeoutException exception) {
            return EmailSendResult.ambiguous(
                    "The HTTPS email transport outcome could not be confirmed.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return EmailSendResult.ambiguous(
                    "The HTTPS email transport was interrupted before its outcome was confirmed.");
        } catch (IOException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The email provider could not be reached.");
        } catch (RuntimeException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                    "The email could not be delivered.");
        }
    }

    private EmailSendResult mapResponse(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            String providerId = null;
            try {
                JsonNode body = objectMapper.readTree(response.body());
                if (body != null && body.path("id").isTextual()) {
                    providerId = body.path("id").asText();
                }
            } catch (IOException ignored) {
                // A successful provider response is still safe to record as sent;
                // the provider id is optional and never affects idempotency.
            }
            return EmailSendResult.sent(providerId);
        }
        if (status == 401 || status == 403) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED,
                    "The email provider rejected authentication.");
        }
        if (status == 408 || status == 429 || (status >= 400 && status < 500)) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                    "The email provider rejected the message.");
        }
        if (status >= 500) {
            return EmailSendResult.ambiguous(
                    "The email provider returned a server failure before its outcome was confirmed.");
        }
        return EmailSendResult.failed(
                EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                "The email provider returned an unsupported response.");
    }

    private ObjectNode toPayload(EmailMessage message) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("from", message.fromName() == null
                ? message.fromAddress()
                : message.fromName() + " <" + message.fromAddress() + ">");
        payload.putArray("to").add(message.recipient());
        if (message.replyTo() != null) {
            payload.putArray("reply_to").add(message.replyTo());
        }
        payload.put("subject", message.subject());
        payload.put("text", message.plainTextBody());
        payload.put("html", message.htmlBody());
        ArrayNode attachments = payload.putArray("attachments");
        ObjectNode attachment = attachments.addObject();
        attachment.put("filename", message.attachment().filename());
        attachment.put("content", Base64.getEncoder().encodeToString(message.attachment().bytes()));
        return payload;
    }

    private static long estimatedSize(EmailMessage message) {
        long attachmentBytes = message.attachment().sizeBytes();
        long encodedAttachmentBytes = ((attachmentBytes + 2) / 3) * 4;
        long size = 8 * 1024L + message.subject().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + message.plainTextBody().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + message.htmlBody().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + encodedAttachmentBytes;
        return size < 0 ? Long.MAX_VALUE : size;
    }
}
