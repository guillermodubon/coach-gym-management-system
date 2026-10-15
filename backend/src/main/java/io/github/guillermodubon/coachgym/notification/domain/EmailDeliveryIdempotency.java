package io.github.guillermodubon.coachgym.notification.domain;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Server-side policy for deterministic, secret-free logical delivery keys. */
public final class EmailDeliveryIdempotency {

    private EmailDeliveryIdempotency() {
    }

    /**
     * Derives a SHA-256 digest from the logical resource, normalized recipient,
     * and template version. The raw composite is never returned or persisted.
     */
    public static String derive(
            EmailDeliveryType deliveryType,
            UUID sourceResourceId,
            String recipient,
            String templateVersion) {
        if (deliveryType == null) {
            throw new EmailDeliveryValidationException("Email delivery type is required.");
        }
        if (sourceResourceId == null) {
            throw new EmailDeliveryValidationException("Email source resource id is required.");
        }
        String normalizedRecipient = EmailDeliveryValuePolicy.normalizeRecipient(recipient);
        String normalizedTemplate = EmailDeliveryValuePolicy.normalizeTemplateVersion(templateVersion);
        String logicalKey = deliveryType.name() + "|" + sourceResourceId
                + "|" + normalizedRecipient + "|" + normalizedTemplate;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(logicalKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
