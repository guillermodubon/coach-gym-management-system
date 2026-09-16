package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import io.github.guillermodubon.coachgym.notification.ComposedEmail;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryTemplateData;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailCompositionException;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;

/** Renders the packaged v1 receipt and credential templates without user HTML. */
class EmailTemplateComposer implements EmailComposer {

    private static final String TEMPLATE_ROOT = "email/templates/";
    private static final String SUPPORTED_TEMPLATE_VERSION = "v1";

    private final EmailProperties properties;

    EmailTemplateComposer(EmailProperties properties) {
        this.properties = Objects.requireNonNull(properties, "Email properties are required.");
    }

    @Override
    public ComposedEmail compose(EmailDeliverySource source) {
        Objects.requireNonNull(source, "Email delivery source is required.");
        if (!SUPPORTED_TEMPLATE_VERSION.equals(properties.templateVersion())) {
            throw new EmailCompositionException("Email template version is not supported.");
        }
        if (source.attachment().sizeBytes() > properties.maxAttachmentBytes()) {
            throw new EmailCompositionException("Email attachment exceeds the configured limit.");
        }

        EmailDeliveryTemplateData data = source.templateData();
        Map<String, String> values = values(source, data);
        String templateName = source.deliveryType() == EmailDeliveryType.PAYMENT_RECEIPT
                ? "payment-receipt" : "access-credential";
        String plainText = render(load(templateName + ".txt"), values, false);
        String html = render(load(templateName + ".html"), values, true);
        String subject = subject(source, data);
        try {
            return new ComposedEmail(
                    properties.templateVersion(),
                    new EmailMessage(
                            source.recipient(),
                            properties.fromAddress(),
                            properties.fromName(),
                            properties.replyTo(),
                            subject,
                            plainText,
                            html,
                            source.attachment()));
        } catch (IllegalArgumentException exception) {
            throw new EmailCompositionException("Email message could not be composed safely.", exception);
        }
    }

    private Map<String, String> values(
            EmailDeliverySource source, EmailDeliveryTemplateData data) {
        Map<String, String> values = new HashMap<>();
        values.put("organizationName", firstValue(data.organizationName(), properties.organizationName()));
        values.put("clientGreeting", firstValue(data.clientDisplayName(), "there"));
        values.put("primaryReference", firstValue(
                data.primaryReference(), source.sourceResourceId().toString()));
        values.put("secondaryReference", value(data.secondaryReference()));
        values.put("status", value(data.status()));
        values.put("eventTimestamp", value(data.eventTimestamp()));
        values.put("amount", value(data.amount()));
        values.put("currency", value(data.currency()));
        values.put("membershipCode", value(data.membershipCode()));
        values.put("planName", value(data.planName()));
        values.put("credentialCode", value(data.credentialCode()));
        values.put("testModeNotice", data.testMode()
                ? "This payment was processed in Stripe Test Mode."
                : "");
        return values;
    }

    private String subject(EmailDeliverySource source, EmailDeliveryTemplateData data) {
        String organizationName = firstValue(data.organizationName(), properties.organizationName());
        String subject = switch (source.deliveryType()) {
            case PAYMENT_RECEIPT -> organizationName
                    + " payment receipt "
                    + firstValue(data.primaryReference(), source.sourceResourceId().toString());
            case ACCESS_CREDENTIAL -> organizationName + " access credential";
        };
        int maximum = Math.min(properties.maxSubjectLength(), EmailDeliveryValuePolicy.MAX_SUBJECT_LENGTH);
        if (subject.length() > maximum) {
            subject = subject.substring(0, maximum).strip();
        }
        return subject;
    }

    private String load(String name) {
        ClassPathResource resource = new ClassPathResource(
                TEMPLATE_ROOT + properties.templateVersion() + "/" + name);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new EmailCompositionException("Email template could not be loaded.", exception);
        }
    }

    private static String render(
            String template, Map<String, String> values, boolean html) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String replacement = html ? escapeHtml(entry.getValue()) : entry.getValue();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", replacement);
        }
        return rendered.strip();
    }

    private static String firstValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(value.charAt(index));
            }
        }
        return escaped.toString();
    }
}
