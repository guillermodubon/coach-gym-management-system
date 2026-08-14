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
            Instant occurredAt);

    Optional<ClientDetails> findById(UUID id);
}
