package io.github.guillermodubon.coachgym.shared.identityemail;

import java.time.Instant;
import java.util.Locale;

/** Ephemeral, provider-neutral account-activated notice; it contains no token or credential. */
public record StaffAccountActivatedEmail(
        String recipient,
        String displayName,
        String roleLabel,
        String scopeLabel,
        Instant activatedAt) {

    public StaffAccountActivatedEmail {
        if (recipient == null || recipient.isBlank() || recipient.length() > 254
                || recipient.indexOf('\r') >= 0 || recipient.indexOf('\n') >= 0
                || !recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Activation email recipient is invalid.");
        }
        recipient = recipient.strip().toLowerCase(Locale.ROOT);
        displayName = requiredText(displayName, "display name", 201);
        roleLabel = requiredText(roleLabel, "role", 40);
        scopeLabel = requiredText(scopeLabel, "scope", 40);
        if (activatedAt == null) {
            throw new IllegalArgumentException("Activation time is required.");
        }
    }

    @Override
    public String toString() {
        return "StaffAccountActivatedEmail[recipient=<redacted>, displayNamePresent=true"
                + ", roleLabel=" + roleLabel
                + ", scopeLabel=" + scopeLabel
                + ", activatedAt=" + activatedAt + ']';
    }

    private static String requiredText(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.strip().length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Activation email " + field + " is invalid.");
        }
        return value.strip().replaceAll("\\s+", " ");
    }
}
