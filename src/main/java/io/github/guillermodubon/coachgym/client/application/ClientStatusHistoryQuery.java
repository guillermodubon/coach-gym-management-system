package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientStatusHistoryDetails;
import java.util.List;
import java.util.UUID;

/** Read port for append-only client lifecycle history. */
public interface ClientStatusHistoryQuery {

    List<ClientStatusHistoryDetails> findByClientId(UUID clientId);
}
