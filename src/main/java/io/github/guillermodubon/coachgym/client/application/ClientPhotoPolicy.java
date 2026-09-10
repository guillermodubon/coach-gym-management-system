package io.github.guillermodubon.coachgym.client.application;

import java.util.Locale;
import java.util.Set;

final class ClientPhotoPolicy {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private ClientPhotoPolicy() {
    }

    static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ClientPhotoValidationException("Client photo content type is required.");
        }
        String normalized = contentType.strip().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new ClientPhotoValidationException("Unsupported client photo content type.");
        }
        return normalized;
    }

    static void requireMatchingSignature(String contentType, byte[] bytes) {
        boolean matches = switch (contentType) {
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> bytes.length >= 12
                    && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                    && at(bytes, 8, 0x57, 0x45, 0x42, 0x50);
            default -> false;
        };
        if (!matches) {
            throw new ClientPhotoValidationException(
                    "Client photo content does not match its declared type.");
        }
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        return at(bytes, 0, signature);
    }

    private static boolean at(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[offset + index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
