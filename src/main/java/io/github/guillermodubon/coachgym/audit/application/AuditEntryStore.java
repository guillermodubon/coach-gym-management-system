package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.client.ClientRegistered;

public interface AuditEntryStore {

    void recordClientRegistered(ClientRegistered event);
}
