package io.github.guillermodubon.coachgym.accesscredential;

import java.util.Base64;
import java.util.Objects;

/**
 * Canonical, versioned value encoded in an access-credential QR code.
 *
 * <p>The payload contains only the opaque token. It deliberately has no
 * accessor for a separate token value and redacts its value from
 * {@link #toString()} so accidental diagnostics cannot disclose the secret.</p>
 */
public final class AccessCredentialQrPayload {

    public static final String PREFIX = "cgac:";
    public static final String CURRENT_VERSION = "v1";
    public static final String PAYLOAD_PREFIX = PREFIX + CURRENT_VERSION + ":";
    /** Length of the fixed {@code cgac:v1:} prefix. */
    public static final int PAYLOAD_PREFIX_LENGTH = 8;
    public static final int TOKEN_BYTES = 32;
    public static final int TOKEN_LENGTH = 43;
    public static final int MAX_LENGTH = PAYLOAD_PREFIX_LENGTH + TOKEN_LENGTH;

    private final String value;

    private AccessCredentialQrPayload(String value) {
        this.value = value;
    }

    /**
     * Composes the current canonical payload from a generated opaque token.
     *
     * @param token URL-safe, unpadded Base64 encoding of 32 random bytes
     * @return canonical QR payload
     */
    public static AccessCredentialQrPayload fromToken(String token) {
        String normalized = requiredToken(token);
        return new AccessCredentialQrPayload(PAYLOAD_PREFIX + normalized);
    }

    /**
     * Parses and canonicalizes a payload received from an offline scanner.
     *
     * @param payload versioned QR payload
     * @return canonical payload
     */
    public static AccessCredentialQrPayload parse(String payload) {
        if (payload == null) {
            throw invalidPayload();
        }
        String normalized = payload.strip();
        if (!normalized.startsWith(PAYLOAD_PREFIX)
                || normalized.length() != MAX_LENGTH) {
            throw invalidPayload();
        }
        String token = normalized.substring(PAYLOAD_PREFIX.length());
        return fromToken(token);
    }

    /** Returns the exact canonical value to encode in the QR image. */
    public String value() {
        return value;
    }

    /** Returns the stable version identifier without exposing token material. */
    public String version() {
        return CURRENT_VERSION;
    }

    @Override
    public String toString() {
        return "AccessCredentialQrPayload[version=" + CURRENT_VERSION
                + ",length=" + value.length() + "]";
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof AccessCredentialQrPayload payload
                && value.equals(payload.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    private static String requiredToken(String token) {
        if (token == null) {
            throw invalidPayload();
        }
        String normalized = token.strip();
        if (!normalized.matches("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}")) {
            throw invalidPayload();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(normalized);
            if (decoded.length != TOKEN_BYTES
                    || !Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(decoded).equals(normalized)) {
                throw invalidPayload();
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw invalidPayload();
        }
    }

    private static IllegalArgumentException invalidPayload() {
        return new IllegalArgumentException("Access credential QR payload is invalid.");
    }
}
