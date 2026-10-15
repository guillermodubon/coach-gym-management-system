package io.github.guillermodubon.coachgym.membership;

import java.util.Optional;
import java.util.UUID;

/**
 * Public membership-module boundary used by the access module.
 *
 * <p>Provides two resolution paths matching the two identifier types:</p>
 * <ul>
 *   <li>{@link #findByCode(String)} — for membership-code identifiers
 *       ({@code MEM-NNNNNN})</li>
 *   <li>{@link #findCurrentByClientId(UUID)} — for client-code identifiers
 *       ({@code CLI-NNNNNN}) after the client has been resolved</li>
 * </ul>
 *
 * <p>Both methods return the minimal projection including current period
 * boundaries and the open freeze window. Period and freeze data are always
 * present when the membership is found (they are invariants of a valid
 * membership). Freeze fields within the projection are {@code null} when no
 * open freeze exists.</p>
 */
public interface MembershipAccessQuery {

    /**
     * Returns the membership projection for the given normalised membership
     * code, or empty if no membership with that code exists.
     *
     * @param normalizedCode trimmed, upper-cased membership code,
     *                       e.g. {@code MEM-000001}
     */
    Optional<MembershipAccessDetails> findByCode(String normalizedCode);

    /**
     * Returns the current (ACTIVE or FROZEN) membership for the given client,
     * or empty if the client has no current membership.
     *
     * @param clientId the client identifier
     */
    Optional<MembershipAccessDetails> findCurrentByClientId(UUID clientId);
}
