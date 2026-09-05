package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.application.NotificationNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.NotificationSearchQuery;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class NotificationPersistenceAdapterTest {

    @Mock private NotificationJpaRepository repository;
    private NotificationPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationPersistenceAdapter(repository);
    }

    @Test
    void createsNotificationThroughRepository() {
        Instant now = Instant.parse("2026-09-05T13:00:00Z");
        NotificationDefinition definition = new NotificationDefinition(
                UUID.randomUUID(), NotificationType.SYSTEM,
                NotificationSeverity.INFO, "System notice", "System information.",
                null, null);
        when(repository.saveAndFlush(any(NotificationJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = adapter.create(definition, now);

        assertThat(result.notificationType()).isEqualTo(NotificationType.SYSTEM);
        assertThat(result.read()).isFalse();
        verify(repository).saveAndFlush(any(NotificationJpaEntity.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsRecipientScopedPageWithStableSecondarySort() {
        UUID recipient = UUID.randomUUID();
        when(repository.findAll(
                any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<NotificationJpaEntity>(List.of(), pageable, 0);
                });

        adapter.findAllByRecipientUserId(recipient, NotificationSearchQuery.defaults());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void idempotentReadDoesNotSaveAlreadyReadEntityAgain() {
        UUID id = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        NotificationJpaEntity entity = NotificationJpaEntity.create(
                new NotificationDefinition(
                        recipient, NotificationType.SYSTEM, NotificationSeverity.INFO,
                        "System notice", "System information.", null, null),
                Instant.parse("2026-09-05T13:00:00Z"));
        entity.markAsRead(Instant.parse("2026-09-05T14:00:00Z"));
        when(repository.findByIdAndRecipientUserId(id, recipient))
                .thenReturn(Optional.of(entity));

        var result = adapter.markAsRead(
                id, recipient, Instant.parse("2026-09-05T15:00:00Z"));

        assertThat(result.readAt()).isEqualTo(Instant.parse("2026-09-05T14:00:00Z"));
        verify(repository, never()).saveAndFlush(any(NotificationJpaEntity.class));
    }

    @Test
    void hidesNotificationsThatDoNotBelongToRecipient() {
        UUID id = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        when(repository.findByIdAndRecipientUserId(id, recipient))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.markAsRead(
                id, recipient, Instant.parse("2026-09-05T15:00:00Z")))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
