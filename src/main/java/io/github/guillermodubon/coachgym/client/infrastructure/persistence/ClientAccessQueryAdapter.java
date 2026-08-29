package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ClientAccessQueryAdapter implements ClientAccessQuery {

    private final ClientJpaRepository clientRepository;

    ClientAccessQueryAdapter(ClientJpaRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientAccessDetails> findByCode(String normalizedCode) {
        return clientRepository
                .findByClientCodeIgnoreCase(normalizedCode)
                .map(this::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientAccessDetails> findById(UUID clientId) {
        return clientRepository
                .findById(clientId)
                .map(this::toDetails);
    }

    private ClientAccessDetails toDetails(ClientJpaEntity entity) {
        return new ClientAccessDetails(
                entity.id(),
                entity.clientCode(),
                entity.status());
    }
}
