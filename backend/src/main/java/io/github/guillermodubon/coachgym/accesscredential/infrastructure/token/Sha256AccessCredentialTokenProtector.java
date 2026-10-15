package io.github.guillermodubon.coachgym.accesscredential.infrastructure.token;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * SHA-256 fingerprint adapter for the canonical {@code cgac:v1:} payload.
 *
 * <p>A fresh digest is created for every operation. This keeps the adapter
 * thread-safe and prevents mutable digest state from being shared between
 * requests.</p>
 */
@Component
class Sha256AccessCredentialTokenProtector implements AccessCredentialTokenProtector {

    static final String SCHEME_VERSION = "sha256-v1";
    static final String PAYLOAD_PREFIX = "cgac:v1:";
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;
    private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]{43}";
    private static final String FINGERPRINT_PATTERN = "[0-9a-f]{64}";

    @Override
    public String schemeVersion() {
        return SCHEME_VERSION;
    }

    @Override
    public String fingerprint(String payload) {
        String canonicalPayload = canonicalPayload(payload);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Credential token protection is unavailable.", exception);
        }
    }

    @Override
    public boolean matches(String payload, String expectedFingerprint) {
        if (expectedFingerprint == null
                || !expectedFingerprint.matches(FINGERPRINT_PATTERN)) {
            return false;
        }
        try {
            byte[] actual = fingerprint(payload).getBytes(StandardCharsets.US_ASCII);
            byte[] expected = expectedFingerprint.getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String canonicalPayload(String payload) {
        if (payload == null) {
            throw invalidPayload();
        }
        String normalized = payload.strip();
        if (normalized.length() != PAYLOAD_PREFIX.length() + TOKEN_LENGTH
                || !normalized.startsWith(PAYLOAD_PREFIX)) {
            throw invalidPayload();
        }
        String opaqueToken = normalized.substring(PAYLOAD_PREFIX.length());
        if (!opaqueToken.matches(TOKEN_PATTERN) || !isCanonicalToken(opaqueToken)) {
            throw invalidPayload();
        }
        return normalized;
    }

    private static boolean isCanonicalToken(String token) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            return decoded.length == TOKEN_BYTES
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
                            .equals(token)
                    && token.length() == TOKEN_LENGTH;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static IllegalArgumentException invalidPayload() {
        return new IllegalArgumentException("Credential token payload is invalid.");
    }
}
