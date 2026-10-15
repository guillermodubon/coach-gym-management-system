package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Server-owned canonical key format for credential PNG artifacts. */
public final class AccessCredentialStorageKey {

    private static final String PREFIX = "access-credentials/";
    private static final String SUFFIX = ".png";

    private AccessCredentialStorageKey() {
    }

    public static String forCredential(UUID credentialId) {
        if (credentialId == null) {
            throw new AccessCredentialStorageException("Credential id is required for storage.");
        }
        return PREFIX + credentialId + SUFFIX;
    }

    /** Validates a key without resolving it against a filesystem path. */
    public static boolean isCanonical(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        String normalized = storageKey.strip();
        if (!normalized.startsWith(PREFIX) || !normalized.endsWith(SUFFIX)) {
            return false;
        }
        String identifier = normalized.substring(PREFIX.length(),
                normalized.length() - SUFFIX.length());
        try {
            return forCredential(UUID.fromString(identifier)).equals(normalized);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String requireCanonical(String storageKey) {
        if (!isCanonical(storageKey)) {
            throw new AccessCredentialStorageException("Invalid access credential storage key.");
        }
        return storageKey.strip();
    }
}
