package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationJpaRepository extends
        JpaRepository<NotificationJpaEntity, UUID>,
        JpaSpecificationExecutor<NotificationJpaEntity> {

    Optional<NotificationJpaEntity> findByIdAndRecipientUserId(
            UUID id,
            UUID recipientUserId);

    Optional<NotificationJpaEntity> findByIdAndRecipientUserIdAndBranchId(
            UUID id,
            UUID recipientUserId,
            UUID branchId);

    @Query("""
            select notification
            from NotificationJpaEntity notification
            where notification.id = :id
              and notification.recipientUserId = :recipientUserId
              and (:branchId is null
                   or notification.branchId is null
                   or notification.branchId = :branchId)
            """)
    Optional<NotificationJpaEntity> findVisibleToRecipientAtBranch(
            @Param("id") UUID id,
            @Param("recipientUserId") UUID recipientUserId,
            @Param("branchId") UUID branchId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    @Query("""
            select count(notification)
            from NotificationJpaEntity notification
            where notification.recipientUserId = :recipientUserId
              and notification.readAt is null
              and (:branchId is null
                   or notification.branchId is null
                   or notification.branchId = :branchId)
            """)
    long countUnreadVisibleToRecipientAtBranch(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("branchId") UUID branchId);
}
