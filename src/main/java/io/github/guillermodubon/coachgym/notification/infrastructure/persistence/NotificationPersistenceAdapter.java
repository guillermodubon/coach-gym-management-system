package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.application.NotificationNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.NotificationPage;
import io.github.guillermodubon.coachgym.notification.application.NotificationReadFilter;
import io.github.guillermodubon.coachgym.notification.application.NotificationSearchQuery;
import io.github.guillermodubon.coachgym.notification.application.NotificationSortDirection;
import io.github.guillermodubon.coachgym.notification.application.NotificationSortField;
import io.github.guillermodubon.coachgym.notification.application.NotificationStore;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import io.github.guillermodubon.coachgym.notification.domain.NotificationPolicy;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class NotificationPersistenceAdapter implements NotificationStore {

    private final NotificationJpaRepository repository;
    private final NotificationPolicy policy = new NotificationPolicy();

    NotificationPersistenceAdapter(NotificationJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    @Transactional
    public NotificationDetails create(
            NotificationDefinition definition,
            Instant createdAt) {
        policy.validate(definition);
        NotificationJpaEntity entity = NotificationJpaEntity.create(definition, createdAt);
        return repository.saveAndFlush(entity).toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationDetails> findByIdAndRecipientUserId(
            UUID notificationId,
            UUID recipientUserId) {
        requireId(notificationId, "Notification id");
        requireId(recipientUserId, "Notification recipient user id");
        return repository.findByIdAndRecipientUserId(notificationId, recipientUserId)
                .map(NotificationJpaEntity::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage findAllByRecipientUserId(
            UUID recipientUserId,
            NotificationSearchQuery query) {
        requireId(recipientUserId, "Notification recipient user id");
        Objects.requireNonNull(query, "Notification search query is required.");

        Pageable pageable = PageRequest.of(
                query.page(), query.size(), sort(query.sortField(), query.sortDirection()));
        Page<NotificationJpaEntity> result = repository.findAll(
                specification(recipientUserId, query), pageable);
        List<NotificationDetails> items = result.getContent().stream()
                .map(NotificationJpaEntity::toDetails)
                .toList();
        return new NotificationPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadByRecipientUserId(UUID recipientUserId) {
        requireId(recipientUserId, "Notification recipient user id");
        return repository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    @Override
    @Transactional
    public NotificationDetails markAsRead(
            UUID notificationId,
            UUID recipientUserId,
            Instant readAt) {
        requireId(notificationId, "Notification id");
        requireId(recipientUserId, "Notification recipient user id");
        Objects.requireNonNull(readAt, "Notification read timestamp is required.");

        NotificationJpaEntity entity = repository
                .findByIdAndRecipientUserId(notificationId, recipientUserId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (entity.markAsRead(readAt)) {
            entity = repository.saveAndFlush(entity);
        }
        return entity.toDetails();
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID recipientUserId, Instant readAt) {
        requireId(recipientUserId, "Notification recipient user id");
        Objects.requireNonNull(readAt, "Notification read timestamp is required.");

        List<NotificationJpaEntity> unread = repository.findAll(
                unreadByRecipient(recipientUserId));
        unread.forEach(entity -> entity.markAsRead(readAt));
        if (!unread.isEmpty()) {
            repository.saveAllAndFlush(unread);
        }
        return unread.size();
    }

    private static Specification<NotificationJpaEntity> specification(
            UUID recipientUserId,
            NotificationSearchQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("recipientUserId"), recipientUserId));

            if (query.readFilter() == NotificationReadFilter.UNREAD) {
                predicates.add(builder.isNull(root.get("readAt")));
            } else if (query.readFilter() == NotificationReadFilter.READ) {
                predicates.add(builder.isNotNull(root.get("readAt")));
            }
            if (query.notificationType() != null) {
                predicates.add(builder.equal(
                        root.get("notificationType"), query.notificationType()));
            }
            if (query.severity() != null) {
                predicates.add(builder.equal(root.get("severity"), query.severity()));
            }
            if (query.resourceType() != null) {
                predicates.add(builder.equal(
                        root.get("resourceType"), query.resourceType()));
            }
            if (query.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("createdAt"), query.createdFrom()));
            }
            if (query.createdUntil() != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("createdAt"), query.createdUntil()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Specification<NotificationJpaEntity> unreadByRecipient(
            UUID recipientUserId) {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("recipientUserId"), recipientUserId),
                builder.isNull(root.get("readAt")));
    }

    private static Sort sort(
            NotificationSortField field,
            NotificationSortDirection direction) {
        String property = switch (field) {
            case CREATED_AT -> "createdAt";
            case SEVERITY -> "severity";
            case NOTIFICATION_TYPE -> "notificationType";
            case READ_AT -> "readAt";
            case ID -> "id";
        };
        Sort.Direction springDirection = direction == NotificationSortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort result = Sort.by(springDirection, property);
        if (field != NotificationSortField.ID) {
            result = result.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return result;
    }

    private static void requireId(UUID id, String label) {
        if (id == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }
}
