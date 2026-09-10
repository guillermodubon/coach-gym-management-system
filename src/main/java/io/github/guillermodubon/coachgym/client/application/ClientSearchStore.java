package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;

/** Read port for the searchable operational client catalog. */
public interface ClientSearchStore {
    ClientPage findAll(ClientSearchQuery query);
}
