package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface NotificationJpaRepository extends
        JpaRepository<NotificationJpaEntity, UUID>,
        JpaSpecificationExecutor<NotificationJpaEntity> {

    Optional<NotificationJpaEntity> findByIdAndRecipientUserId(
            UUID id,
            UUID recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);
}
