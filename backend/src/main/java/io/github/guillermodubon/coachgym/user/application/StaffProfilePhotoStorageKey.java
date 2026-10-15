package io.github.guillermodubon.coachgym.user.application;

import java.util.Locale;
import java.util.UUID;

/** Server-owned namespace and canonical key policy for staff photo objects. */
public final class StaffProfilePhotoStorageKey {

    private static final String PREFIX = "staff-profiles/";
    private static final int MAX_LENGTH = 500;

    private StaffProfilePhotoStorageKey() {
    }

    public static String forUser(UUID userId, String contentType) {
        if (userId == null) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile user id is required for storage.");
        }
        return PREFIX + userId + "/" + UUID.randomUUID()
                + extensionFor(contentType);
    }

    public static String requireCanonical(String storageKey) {
        if (!isCanonical(storageKey)) {
            throw new StaffProfilePhotoStorageException(
                    "Invalid staff profile photo storage key.");
        }
        return storageKey.strip();
    }

    public static String requireCanonicalForUser(String storageKey, UUID userId) {
        String canonical = requireCanonical(storageKey);
        if (userId == null || !canonical.startsWith(PREFIX + userId + "/")) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo storage key ownership is invalid.");
        }
        return canonical;
    }

    public static String requireCanonicalForContentType(
            String storageKey, String contentType) {
        String canonical = requireCanonical(storageKey);
        String expectedExtension = extensionFor(contentType);
        if (!canonical.endsWith(expectedExtension)) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo storage key type is invalid.");
        }
        return canonical;
    }

    public static boolean isCanonical(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        String normalized = storageKey.strip();
        if (normalized.length() > MAX_LENGTH || normalized.contains("\\")
                || normalized.contains("..")) {
            return false;
        }
        String[] parts = normalized.split("/", -1);
        if (parts.length != 3 || !PREFIX.substring(0, PREFIX.length() - 1)
                .equals(parts[0]) || parts[1].isBlank()) {
            return false;
        }
        UUID owner;
        try {
            owner = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!owner.toString().equals(parts[1])) {
            return false;
        }
        int extensionIndex = parts[2].lastIndexOf('.');
        if (extensionIndex <= 0) {
            return false;
        }
        String objectId = parts[2].substring(0, extensionIndex);
        String extension = parts[2].substring(extensionIndex).toLowerCase(Locale.ROOT);
        try {
            if (!UUID.fromString(objectId).toString().equals(objectId)) {
                return false;
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return extension.equals(".jpg")
                || extension.equals(".png")
                || extension.equals(".webp");
    }

    public static String extensionFor(String contentType) {
        if (contentType == null) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo content type is required.");
        }
        return switch (contentType.strip().toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new StaffProfilePhotoStorageException(
                    "Unsupported staff profile photo content type.");
        };
    }
}
