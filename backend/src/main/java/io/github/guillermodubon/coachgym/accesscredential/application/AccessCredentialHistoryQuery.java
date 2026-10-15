package io.github.guillermodubon.coachgym.accesscredential.application;

import java.util.UUID;

/** Read-only history query port. */
public interface AccessCredentialHistoryQuery {

    AccessCredentialHistoryPage findByCredentialId(UUID credentialId, int page, int size);

    AccessCredentialHistoryPage findByClientId(UUID clientId, int page, int size);
}
