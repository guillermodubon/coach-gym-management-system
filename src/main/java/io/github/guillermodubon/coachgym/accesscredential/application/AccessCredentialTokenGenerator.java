package io.github.guillermodubon.coachgym.accesscredential.application;

/**
 * Generates the opaque token used in a client access credential payload.
 *
 * <p>The returned value is an in-memory secret. Callers must use it only for
 * payload composition and artifact generation and must never persist or log
 * it.</p>
 */
public interface AccessCredentialTokenGenerator {

    /**
     * Generates one cryptographically random, URL-safe token.
     *
     * @return a token that is never reused by the production adapter
     */
    String generate();
}
