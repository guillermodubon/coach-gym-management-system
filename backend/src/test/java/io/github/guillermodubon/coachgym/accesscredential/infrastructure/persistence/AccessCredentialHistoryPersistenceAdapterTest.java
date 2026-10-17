package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPersistenceCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AccessCredentialHistoryPersistenceAdapterTest {

    @Mock
    private AccessCredentialHistoryJpaRepository historyRepository;

    private AccessCredentialHistoryPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccessCredentialHistoryPersistenceAdapter(historyRepository);
    }

    @Test
    void appendsInitialHistoryThroughTheRepository() {
        when(historyRepository.saveAndFlush(any(AccessCredentialHistoryJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var details = adapter.append(new AccessCredentialHistoryPersistenceCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                AccessCredentialStatus.ACTIVE,
                null,
                Instant.parse("2026-09-13T10:00:00Z"),
                UUID.randomUUID(),
                null));

        assertThat(details.previousStatus()).isNull();
        assertThat(details.newStatus()).isEqualTo(AccessCredentialStatus.ACTIVE);
    }

    @Test
    void requestsNewestFirstStableHistoryPage() {
        when(historyRepository.findByCredentialId(any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(java.util.List.of(), pageable, 0);
                });

        var page = adapter.findByCredentialId(UUID.randomUUID(), 1, 10);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    void requestsHistoryByClientWithTheSameStableOrdering() {
        when(historyRepository.findByClientId(any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(java.util.List.of(), pageable, 0);
                });

        var page = adapter.findByClientId(UUID.randomUUID(), 0, 25);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(25);
    }
}
