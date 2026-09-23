package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.domain.ClientRegistration;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ClientStore {

    boolean existsByEmail(String email);

    ClientDetails register(
            ClientRegistration registration,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID homeBranchId);

    default ClientDetails register(
            ClientRegistration registration,
            AuthenticatedActor actor,
            Instant occurredAt) {
        return register(registration, actor, occurredAt, null);
    }

    Optional<ClientDetails> findById(UUID id);

    default Optional<ClientDetails> findById(UUID id, UUID homeBranchId) {
        return findById(id);
    }
}
