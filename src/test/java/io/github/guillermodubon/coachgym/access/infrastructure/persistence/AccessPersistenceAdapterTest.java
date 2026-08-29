package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.application.AccessRecordPage;
import io.github.guillermodubon.coachgym.access.application.AccessRecordSearchQuery;
import io.github.guillermodubon.coachgym.access.application.AccessSortDirection;
import io.github.guillermodubon.coachgym.access.application.AccessSortField;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AccessPersistenceAdapterTest {

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-09-15T20:00:00Z");

    @Mock
    private AccessRecordJpaRepository accessRecordRepository;

    private AccessPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccessPersistenceAdapter(
                accessRecordRepository);
    }

    @Test
    void persistsAllowedAttempt() {
        given(accessRecordRepository.saveAndFlush(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        AccessRecordDetails result = adapter.persist(
                "MEM-000001",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                PERIOD_ID,
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active and its current period is valid.",
                NOW,
                ACTOR_ID);

        assertThat(result.id()).isNotNull();
        assertThat(result.result())
                .isEqualTo(AccessResult.ALLOWED);
        assertThat(result.clientCode())
                .isEqualTo("CLI-000001");
        assertThat(result.membershipCode())
                .isEqualTo("MEM-000001");

        verify(accessRecordRepository)
                .saveAndFlush(any(AccessRecordJpaEntity.class));
    }

    @Test
    void persistsDeniedUnresolvedAttempt() {
        given(accessRecordRepository.saveAndFlush(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        AccessRecordDetails result = adapter.persist(
                "XYZ-999999",
                null,
                null,
                null,
                null,
                null,
                AccessResult.DENIED,
                AccessReasonCode.IDENTIFIER_NOT_FOUND,
                "The presented identifier could not be resolved.",
                NOW,
                ACTOR_ID);

        assertThat(result.result())
                .isEqualTo(AccessResult.DENIED);
        assertThat(result.clientId()).isNull();
        assertThat(result.membershipId()).isNull();
    }

    @Test
    void returnsRecordById() {
        AccessRecordJpaEntity entity = allowedEntity();

        given(accessRecordRepository.findById(entity.id()))
                .willReturn(Optional.of(entity));

        Optional<AccessRecordDetails> result =
                adapter.findById(entity.id());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().result())
                .isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void returnsEmptyForUnknownRecord() {
        UUID unknownId = UUID.randomUUID();

        given(accessRecordRepository.findById(unknownId))
                .willReturn(Optional.empty());

        assertThat(adapter.findById(unknownId)).isEmpty();
    }

    @Test
    void listsRecordsUsingDynamicSpecification() {
        AccessRecordJpaEntity entity = allowedEntity();

        AccessRecordSearchQuery query =
                new AccessRecordSearchQuery(
                        CLIENT_ID,
                        MEMBERSHIP_ID,
                        AccessResult.ALLOWED,
                        AccessReasonCode.ACCESS_ALLOWED,
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(60),
                        ACTOR_ID,
                        0,
                        10,
                        AccessSortField.CHECKED_IN_AT,
                        AccessSortDirection.DESC);

        given(accessRecordRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<AccessRecordJpaEntity>>any(),
                org.mockito.ArgumentMatchers
                        .<Pageable>any()))
                .willReturn(new PageImpl<>(
                        List.of(entity),
                        PageRequest.of(0, 10),
                        1));

        AccessRecordPage result = adapter.findAll(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);

        verify(accessRecordRepository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<AccessRecordJpaEntity>>any(),
                org.mockito.ArgumentMatchers
                        .<Pageable>any());
    }

    @Test
    void appliesStableCheckedInAtSort() {
        AccessRecordSearchQuery query =
                AccessRecordSearchQuery.from(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        25,
                        "CHECKED_IN_AT",
                        "ASC");

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        given(accessRecordRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<AccessRecordJpaEntity>>any(),
                any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        adapter.findAll(query);

        verify(accessRecordRepository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<AccessRecordJpaEntity>>any(),
                pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getSort().getOrderFor("checkedInAt"))
                .isNotNull();
        assertThat(pageable.getSort().getOrderFor("checkedInAt")
                .isAscending())
                .isTrue();

        assertThat(pageable.getSort().getOrderFor("id"))
                .isNotNull();
        assertThat(pageable.getSort().getOrderFor("id")
                .isAscending())
                .isTrue();
    }

    private static AccessRecordJpaEntity allowedEntity() {
        return AccessRecordJpaEntity.create(
                "MEM-000001",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                PERIOD_ID,
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active and its current period is valid.",
                NOW,
                ACTOR_ID);
    }
}
