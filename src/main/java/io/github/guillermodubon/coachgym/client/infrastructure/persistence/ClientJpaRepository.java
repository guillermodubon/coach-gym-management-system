package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClientJpaRepository extends JpaRepository<ClientJpaEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "emergencyContacts")
    Optional<ClientJpaEntity> findWithEmergencyContactsById(UUID id);
}
