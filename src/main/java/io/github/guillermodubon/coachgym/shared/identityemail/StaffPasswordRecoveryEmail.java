package io.github.guillermodubon.coachgym.shared.identityemail;

import java.util.Base64;
import java.time.Instant;
import java.util.Locale;

/** Ephemeral recovery-link data; never persist or publish this token-bearing contract. */
public record StaffPasswordRecoveryEmail(
        String recipient,
        String displayName,
        String token,
        Instant expiresAt) {

    public StaffPasswordRecoveryEmail {
        if (recipient == null || recipient.isBlank() || recipient.length() > 254
                || recipient.indexOf('\r') >= 0 || recipient.indexOf('\n') >= 0
                || !recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Password recovery recipient is invalid.");
        }
        recipient = recipient.strip().toLowerCase(Locale.ROOT);
        if (displayName == null || displayName.isBlank() || displayName.length() > 201
                || displayName.indexOf('\r') >= 0 || displayName.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Password recovery display name is invalid.");
        }
        displayName = displayName.strip();
        token = requireToken(token);
        if (expiresAt == null) {
            throw new IllegalArgumentException("Password recovery expiration is required.");
        }
    }

    @Override
    public String toString() {
        return "StaffPasswordRecoveryEmail[recipient=<redacted>, displayNamePresent="
                + !displayName.isBlank() + ", token=<redacted>, expiresAt=" + expiresAt + ']';
    }

    private static String requireToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Password recovery token is invalid.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
                throw new IllegalArgumentException("Password recovery token is invalid.");
            }
        } catch (IllegalArgumentException invalidToken) {
            throw new IllegalArgumentException("Password recovery token is invalid.");
        }
        return value;
    }
}
