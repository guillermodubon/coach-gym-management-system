package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import java.util.UUID;

/** Read port for the searchable operational client catalog. */
public interface ClientSearchStore {
    ClientPage findAll(ClientSearchQuery query);

    default ClientPage findAll(ClientSearchQuery query, UUID branchId) {
        return findAll(query);
    }
}
