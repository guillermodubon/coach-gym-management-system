package io.github.guillermodubon.coachgym.user;

import java.util.Set;
import java.util.UUID;

/**
 * Technology-neutral self-profile projection for an authenticated staff user.
 *
 * <p>The projection contains only identity and safe presentation data. It does
 * not contain credentials, sessions, permissions internals, storage keys, or
 * future organization/branch assignments.</p>
 */
public record StaffSelfProfileDetails(
        UUID userId,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<RoleCode> roles,
        StaffAccountStatus status,
        StaffProfilePhotoDetails photo,
        long version) {

    public StaffSelfProfileDetails {
        if (userId == null) {
            throw new IllegalArgumentException("Staff profile user id is required.");
        }
        username = requiredText(username, "Staff username", 100);
        email = requiredText(email, "Staff email", 254).toLowerCase(java.util.Locale.ROOT);
        firstName = requiredText(firstName, "Staff first name", 100);
        lastName = requiredText(lastName, "Staff last name", 100);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        if (status == null) {
            throw new IllegalArgumentException("Staff account status is required.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Staff profile version must not be negative.");
        }
    }

    /** Display name is derived from the two persisted personal-name fields. */
    public String displayName() {
        return firstName + " " + lastName;
    }

    public boolean photoPresent() {
        return photo != null;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }
}
