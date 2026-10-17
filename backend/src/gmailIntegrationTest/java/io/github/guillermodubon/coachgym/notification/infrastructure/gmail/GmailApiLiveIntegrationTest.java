package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.EmailProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Explicitly opted-in, bounded live Gmail check; never included in the normal test source set. */
class GmailApiLiveIntegrationTest {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_ALLOWLISTED_RECIPIENTS = 5;
    private static final byte[] SYNTHETIC_PDF = ("%PDF-1.4\n"
            + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
            + "2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj\n"
            + "trailer << /Root 1 0 R >>\n%%EOF\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] SYNTHETIC_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/+j8AAAAASUVORK5CYII=");

    @Test
    void sendsAtMostTwoSyntheticMessagesToTheExplicitlyAllowlistedRecipient() {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(environment.get("GMAIL_LIVE_TESTS_ENABLED")),
                "Live Gmail verification is opt-in.");

        String recipient = required(environment, "GMAIL_LIVE_TEST_RECIPIENT");
        Set<String> allowlist = recipientAllowlist(environment);
        if (!allowlist.contains(recipient.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "The live-test recipient must exactly match an allowlisted address.");
        }

        EmailProperties emailProperties = emailProperties(environment);
        GmailApiProperties apiProperties = apiProperties(environment);
        GoogleOAuthProperties oauthProperties = oauthProperties(environment);
        if (!"true".equalsIgnoreCase(environment.get("EMAIL_ENABLED"))
                || !emailProperties.isValidWhenEnabled()
                || new GmailConfigurationReadiness(apiProperties, oauthProperties)
                        .status(true, emailProperties.fromAddress())
                        != GmailConfigurationReadiness.Status.READY) {
            throw new IllegalStateException(
                    "Live Gmail configuration is incomplete or invalid; no message was sent.");
        }

        GmailOperationalMetrics metrics = new GmailOperationalMetrics(new SimpleMeterRegistry());
        GoogleOAuthTokenClient tokenClient = new GoogleOAuthTokenClient(
                HttpClient.newBuilder()
                        .connectTimeout(apiProperties.connectionTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                new ObjectMapper(JsonFactory.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .build()),
                oauthProperties,
                Clock.systemUTC(),
                metrics);
        GmailApiEmailSenderAdapter sender = new GmailApiEmailSenderAdapter(
                GmailApiEmailSenderAdapter.createHttpClient(apiProperties),
                apiProperties,
                tokenClient,
                metrics);

        assertAccepted(sender.send(syntheticMessage(
                recipient, emailProperties, "PDF", new EmailAttachment(
                        "gmail-live-check.pdf", "application/pdf", SYNTHETIC_PDF))));
        assertAccepted(sender.send(syntheticMessage(
                recipient, emailProperties, "PNG", new EmailAttachment(
                        "gmail-live-check.png", "image/png", SYNTHETIC_PNG))));
    }

    private static EmailMessage syntheticMessage(
            String recipient, EmailProperties properties, String format, EmailAttachment attachment) {
        return new EmailMessage(
                recipient,
                properties.fromAddress(),
                properties.fromName(),
                properties.replyTo(),
                "Coach Gym Gmail API live verification (synthetic " + format + ")",
                "Synthetic Gmail API transport check. This message contains no customer or staff data.",
                "<p>Synthetic Gmail API transport check.</p>"
                        + "<p>This message contains no customer or staff data.</p>",
                attachment);
    }

    private static void assertAccepted(EmailSendResult result) {
        assertThat(result.result()).isEqualTo(EmailAttemptResult.SENT);
        assertThat(result.providerMessageId() != null
                        && result.providerMessageId().matches("[A-Za-z0-9_-]{1,200}"))
                .as("provider acceptance must return a bounded message identifier")
                .isTrue();
    }

    private static Set<String> recipientAllowlist(Map<String, String> environment) {
        String configured = required(environment, "GMAIL_LIVE_TEST_RECIPIENT_ALLOWLIST");
        String[] entries = configured.split(",", -1);
        if (entries.length == 0 || entries.length > MAX_ALLOWLISTED_RECIPIENTS) {
            throw new IllegalStateException("The live-test recipient allow-list is invalid.");
        }
        Set<String> recipients = Arrays.stream(entries)
                .map(String::strip)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (recipients.stream().anyMatch(value -> !EMAIL.matcher(value).matches())) {
            throw new IllegalStateException("The live-test recipient allow-list is invalid.");
        }
        return recipients;
    }

    private static EmailProperties emailProperties(Map<String, String> environment) {
        return new EmailProperties(
                true,
                value(environment, "EMAIL_ORGANIZATION_NAME", "Coach Gym"),
                value(environment, "EMAIL_TEMPLATE_VERSION", "v1"),
                required(environment, "EMAIL_FROM_ADDRESS"),
                value(environment, "EMAIL_FROM_NAME", "Coach Gym"),
                environment.get("EMAIL_REPLY_TO"),
                integer(environment, "EMAIL_MAX_ATTACHMENT_BYTES", 10 * 1024 * 1024),
                integer(environment, "EMAIL_MAX_SUBJECT_LENGTH", 200),
                integer(environment, "EMAIL_MAX_RETRY_COUNT", 3),
                Duration.parse(value(environment, "EMAIL_STALE_PENDING_THRESHOLD", "PT15M")));
    }

    private static GmailApiProperties apiProperties(Map<String, String> environment) {
        return new GmailApiProperties(
                value(environment, "GMAIL_API_BASE_URL", "https://gmail.googleapis.com"),
                required(environment, "GMAIL_SENDER_ADDRESS"),
                Duration.parse(value(environment, "GMAIL_CONNECT_TIMEOUT", "PT5S")),
                Duration.parse(value(environment, "GMAIL_READ_TIMEOUT", "PT15S")),
                Duration.parse(value(environment, "GMAIL_WRITE_TIMEOUT", "PT15S")),
                integer(environment, "EMAIL_MAX_MESSAGE_BYTES", 12 * 1024 * 1024));
    }

    private static GoogleOAuthProperties oauthProperties(Map<String, String> environment) {
        return new GoogleOAuthProperties(
                value(environment, "GOOGLE_OAUTH_TOKEN_URL", "https://oauth2.googleapis.com/token"),
                required(environment, "GMAIL_CLIENT_ID"),
                required(environment, "GMAIL_CLIENT_SECRET"),
                required(environment, "GMAIL_REFRESH_TOKEN"),
                Duration.parse(value(environment, "GMAIL_CONNECT_TIMEOUT", "PT5S")),
                Duration.parse(value(environment, "GMAIL_READ_TIMEOUT", "PT15S")),
                Duration.parse(value(environment, "GOOGLE_OAUTH_EXPIRY_SAFETY_MARGIN", "PT60S")),
                Duration.parse(value(environment, "GOOGLE_OAUTH_REFRESH_WAIT_TIMEOUT", "PT20S")));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required live-test setting is missing: " + name);
        }
        return value.strip();
    }

    private static String value(Map<String, String> environment, String name, String fallback) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int integer(Map<String, String> environment, String name, int fallback) {
        try {
            return Integer.parseInt(value(environment, name, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Live-test setting must be a valid integer: " + name);
        }
    }
}
