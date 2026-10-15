package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;

/** Append-only history write port. */
public interface AccessCredentialHistoryStore {

    AccessCredentialHistoryDetails append(AccessCredentialHistoryPersistenceCommand command);
}
