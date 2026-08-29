package io.github.guillermodubon.coachgym.access.domain;

/**
 * A normalised gym access identifier with an inferred type.
 *
 * <p>Normalisation rules applied by {@link #of(String)}:</p>
 * <ol>
 *   <li>Strip leading and trailing whitespace.</li>
 *   <li>Convert to upper-case (codes are stored and matched case-insensitively,
 *       but upper-case is the canonical form).</li>
 * </ol>
 *
 * <p>A blank or null raw value is rejected with
 * {@link AccessValidationException}. The normalised value is always
 * non-blank.</p>
 *
 * <p>Type inference (after normalisation):</p>
 * <ul>
 *   <li>Starts with {@code "MEM-"} → {@link AccessIdentifierType#MEMBERSHIP_CODE}</li>
 *   <li>Starts with {@code "CLI-"} → {@link AccessIdentifierType#CLIENT_CODE}</li>
 *   <li>Anything else → {@link AccessIdentifierType#UNKNOWN}</li>
 * </ul>
 */
public record AccessIdentifier(
        String value,
        AccessIdentifierType type) {

    private static final String MEMBERSHIP_PREFIX = "MEM-";
    private static final String CLIENT_PREFIX = "CLI-";

    /**
     * Normalises {@code raw} and infers its type.
     *
     * @param raw the raw identifier string from the request
     * @return a normalised {@code AccessIdentifier}
     * @throws AccessValidationException if {@code raw} is null or blank
     */
    public static AccessIdentifier of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AccessValidationException(
                    "Access identifier must be provided.");
        }

        String normalised = raw.strip().toUpperCase();

        if (normalised.isBlank()) {
            throw new AccessValidationException(
                    "Access identifier must not be blank.");
        }

        AccessIdentifierType type = inferType(normalised);
        return new AccessIdentifier(normalised, type);
    }

    private static AccessIdentifierType inferType(String normalised) {
        if (normalised.startsWith(MEMBERSHIP_PREFIX)) {
            return AccessIdentifierType.MEMBERSHIP_CODE;
        }
        if (normalised.startsWith(CLIENT_PREFIX)) {
            return AccessIdentifierType.CLIENT_CODE;
        }
        return AccessIdentifierType.UNKNOWN;
    }
}
