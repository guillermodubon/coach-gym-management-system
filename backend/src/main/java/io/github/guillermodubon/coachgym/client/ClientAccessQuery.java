package io.github.guillermodubon.coachgym.client;

import java.util.Optional;

import java.util.UUID;

/**
 * Public client-module boundary used by the access module.
 *
 * <p>Two resolution paths are provided:</p>
 * <ul>
 *   <li>{@link #findByCode(String)} — for client-code identifiers
 *       ({@code CLI-NNNNNN})</li>
 *   <li>{@link #findById(UUID)} — for resolving the client when the
 *       membership code path has already resolved a {@code clientId}</li>
 * </ul>
 *
 * <p>The caller decides whether the returned status is acceptable for its
 * use case.</p>
 */
public interface ClientAccessQuery {

    /**
     * Returns the minimal client projection for the given normalised
     * client code, or empty if no client with that code exists.
     *
     * @param normalizedCode trimmed, upper-cased client code, e.g. {@code CLI-000001}
     */
    Optional<ClientAccessDetails> findByCode(String normalizedCode);

    /**
     * Returns the minimal client projection for the given client identifier,
     * or empty if no client with that id exists.
     */
    Optional<ClientAccessDetails> findById(UUID clientId);
}
