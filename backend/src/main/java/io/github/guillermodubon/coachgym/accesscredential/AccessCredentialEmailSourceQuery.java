package io.github.guillermodubon.coachgym.accesscredential;

import java.util.Optional;
import java.util.UUID;

/** Public boundary for delivering an existing active credential artifact. */
public interface AccessCredentialEmailSourceQuery {

    Optional<AccessCredentialEmailSource> findByCredentialId(UUID credentialId);

    Optional<AccessCredentialEmailSource> findActiveByClientId(UUID clientId);
}
