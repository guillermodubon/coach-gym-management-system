package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.application.ClientStore;
import io.github.guillermodubon.coachgym.client.domain.ClientRegistration;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ClientPersistenceAdapter implements ClientStore {

    private final ClientJpaRepository clientRepository;
    private final EntityManager entityManager;

    ClientPersistenceAdapter(ClientJpaRepository clientRepository, EntityManager entityManager) {
        this.clientRepository = clientRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return clientRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    @Transactional
    public ClientDetails register(
            ClientRegistration registration,
            AuthenticatedActor actor,
            Instant occurredAt) {
        ClientJpaEntity client = clientRepository.saveAndFlush(
                ClientJpaEntity.register(registration, actor, occurredAt));
        entityManager.refresh(client);
        return client.toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDetails> findById(UUID id) {
        return clientRepository.findWithEmergencyContactsById(id).map(ClientJpaEntity::toDetails);
    }
}
