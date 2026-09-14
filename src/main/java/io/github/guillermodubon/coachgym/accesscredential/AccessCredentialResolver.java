package io.github.guillermodubon.coachgym.accesscredential;

import java.util.Optional;

/**
 * Public boundary for resolving an approved QR payload to an active client
 * identity.
 *
 * <p>The credential module owns token protection and persistence. Consumers
 * must pass the canonical {@link AccessCredentialQrPayload} and may depend
 * only on the minimal {@link ResolvedAccessCredential} projection. An empty
 * result intentionally covers unknown and inactive credentials without
 * revealing which protected lookup state failed.</p>
 */
public interface AccessCredentialResolver {

    /**
     * Resolves one canonical QR payload without returning token material.
     *
     * @param payload canonical versioned payload parsed by the caller
     * @return the active credential identity, or empty when it cannot be used
     */
    Optional<ResolvedAccessCredential> resolve(
            AccessCredentialQrPayload payload);

    /**
     * Resolves an active credential while taking the database row lock needed
     * to serialize duplicate-scan decisions for that credential.
     *
     * <p>The lock is held by the caller's transaction. It is deliberately
     * scoped to one credential row and never exposes token material.</p>
     *
     * @param payload canonical versioned payload parsed by the caller
     * @return the active credential identity, or empty when it cannot be used
     */
    Optional<ResolvedAccessCredential> resolveAndLock(
            AccessCredentialQrPayload payload);
}
