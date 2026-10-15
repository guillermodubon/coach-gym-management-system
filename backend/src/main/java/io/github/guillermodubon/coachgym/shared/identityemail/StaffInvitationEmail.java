package io.github.guillermodubon.coachgym.shared.identityemail;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Ephemeral invitation email input. It must never be persisted or published as an event. */
public record StaffInvitationEmail(
        String recipient,
        String roleLabel,
        String scopeLabel,
        List<String> branchNames,
        Instant expiresAt,
        String token) {

    public StaffInvitationEmail {
        if (recipient == null || recipient.isBlank() || recipient.length() > 254
                || recipient.indexOf('\r') >= 0 || recipient.indexOf('\n') >= 0
                || !recipient.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Invitation recipient is invalid.");
        }
        recipient = recipient.strip().toLowerCase(Locale.ROOT);
        roleLabel = requiredLabel(roleLabel, "role");
        scopeLabel = requiredLabel(scopeLabel, "scope");
        branchNames = branchNames == null ? List.of() : List.copyOf(branchNames);
        if (branchNames.size() > 100 || branchNames.stream().anyMatch(name ->
                name == null || name.isBlank() || name.length() > 160)) {
            throw new IllegalArgumentException("Invitation branch summary is invalid.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Invitation expiration is required.");
        }
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invitation token is invalid.");
        }
    }

    @Override
    public String toString() {
        return "StaffInvitationEmail[recipient=<redacted>, roleLabel=" + roleLabel
                + ", scopeLabel=" + scopeLabel
                + ", branchCount=" + branchNames.size()
                + ", expiresAt=" + expiresAt
                + ", token=<redacted>]";
    }

    private static String requiredLabel(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 40
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invitation " + field + " label is invalid.");
        }
        return value.strip();
    }
}
