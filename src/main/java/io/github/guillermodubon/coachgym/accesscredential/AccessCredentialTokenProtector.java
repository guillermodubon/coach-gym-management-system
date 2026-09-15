package io.github.guillermodubon.coachgym.accesscredential;

/**
 * Technology-neutral protected-token contract shared with future QR lookup.
 *
 * <p>The protector accepts the complete canonical versioned payload and
 * derives the value persisted in {@code token_fingerprint}. It never returns
 * the original token.</p>
 */
public interface AccessCredentialTokenProtector {

    /**
     * Identifies the protection scheme persisted alongside a fingerprint.
     *
     * @return a stable, non-secret scheme identifier
     */
    String schemeVersion();

    /**
     * Derives the canonical fingerprint for a versioned credential payload.
     *
     * @param payload canonical {@code cgac:v1:<opaque-token>} payload
     * @return lowercase hexadecimal fingerprint
     * @throws IllegalArgumentException when the payload is malformed
     */
    String fingerprint(String payload);

    /**
     * Compares a presented payload with a stored fingerprint safely.
     *
     * @param payload presented versioned payload
     * @param expectedFingerprint persisted lowercase hexadecimal fingerprint
     * @return {@code true} only when both values are valid and equal
     */
    boolean matches(String payload, String expectedFingerprint);
}
