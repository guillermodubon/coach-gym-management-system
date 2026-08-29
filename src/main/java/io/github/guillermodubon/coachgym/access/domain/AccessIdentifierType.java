package io.github.guillermodubon.coachgym.access.domain;

/**
 * The category of an access identifier inferred from its code prefix.
 *
 * <p>Prefix mapping (case-insensitive, evaluated after normalisation):</p>
 * <ul>
 *   <li>{@code MEM-} → {@code MEMBERSHIP_CODE}</li>
 *   <li>{@code CLI-} → {@code CLIENT_CODE}</li>
 *   <li>anything else → {@code UNKNOWN}</li>
 * </ul>
 *
 * <p>{@code UNKNOWN} is not a validation error: the application service
 * passes the identifier to both resolver queries, and the policy emits
 * {@code IDENTIFIER_NOT_FOUND} when neither resolves it.</p>
 */
public enum AccessIdentifierType {

    /** The code resolves via the membership code lookup (prefix {@code MEM-}). */
    MEMBERSHIP_CODE,

    /** The code resolves via the client code lookup (prefix {@code CLI-}). */
    CLIENT_CODE,

    /**
     * The prefix is not recognised. The identifier is still presented to
     * the resolvers; the policy will emit {@code IDENTIFIER_NOT_FOUND}.
     */
    UNKNOWN
}
