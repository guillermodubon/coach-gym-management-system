package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipVersionConflictException;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipFreezePersistenceAdapterTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final LocalDate STARTS_ON =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(2026, 9, 15);

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-01T14:00:00Z");

    @Mock
    private MembershipFreezeJpaRepository
            freezeRepository;

    private MembershipFreezePersistenceAdapter
            adapter;

    @BeforeEach
    void setUp() {
        adapter =
                new MembershipFreezePersistenceAdapter(
                        freezeRepository);
    }

    @Test
    void shouldFindAnOpenFreezeByMembershipId() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        when(
                freezeRepository
                        .findFirstByMembershipIdAndReactivatedOnIsNull(
                                MEMBERSHIP_ID))
                .thenReturn(Optional.of(entity));

        Optional<MembershipFreezeDetails> result =
                adapter.findOpenByMembershipId(
                        MEMBERSHIP_ID);

        assertThat(result)
                .isPresent();

        assertThat(result.orElseThrow().membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(result.orElseThrow().open())
                .isTrue();
    }

    @Test
    void shouldReturnEmptyWhenAnOpenFreezeDoesNotExist() {
        when(
                freezeRepository
                        .findFirstByMembershipIdAndReactivatedOnIsNull(
                                MEMBERSHIP_ID))
                .thenReturn(Optional.empty());

        Optional<MembershipFreezeDetails> result =
                adapter.findOpenByMembershipId(
                        MEMBERSHIP_ID);

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldReportWhetherAnOpenFreezeExists() {
        when(
                freezeRepository
                        .existsByMembershipIdAndReactivatedOnIsNull(
                                MEMBERSHIP_ID))
                .thenReturn(true);

        boolean result =
                adapter.hasOpenFreeze(
                        MEMBERSHIP_ID);

        assertThat(result)
                .isTrue();
    }

    @Test
    void shouldPersistAMembershipFreeze() {
        MembershipFreeze freeze =
                membershipFreeze();

        when(freezeRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any(
                        MembershipFreezeJpaEntity.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        MembershipFreezeDetails result =
                adapter.create(
                        freeze,
                        actor(),
                        OCCURRED_AT);

        assertThat(result.id())
                .isNotNull();

        assertThat(result.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(result.membershipPeriodId())
                .isEqualTo(PERIOD_ID);

        assertThat(result.open())
                .isTrue();

        verify(freezeRepository)
                .saveAndFlush(
                        org.mockito.ArgumentMatchers.any(
                                MembershipFreezeJpaEntity.class));
    }

    @Test
    void shouldReactivateAnOpenFreeze() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        UUID freezeId = entity.id();

        when(freezeRepository.findById(freezeId))
                .thenReturn(Optional.of(entity));

        when(freezeRepository.saveAndFlush(entity))
                .thenReturn(entity);

        LocalDate reactivatedOn =
                LocalDate.of(2026, 9, 10);

        MembershipFreezeDetails result =
                adapter.reactivate(
                        MEMBERSHIP_ID,
                        freezeId,
                        reactivatedOn,
                        0L,
                        actor(),
                        OCCURRED_AT.plusSeconds(3_600));

        assertThat(result.reactivatedOn())
                .isEqualTo(reactivatedOn);

        assertThat(result.reactivatedByUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(result.open())
                .isFalse();

        verify(freezeRepository)
                .saveAndFlush(entity);
    }

    @Test
    void shouldRejectAnUnknownFreeze() {
        UUID unknownFreezeId =
                UUID.fromString(
                        "90000000-0000-0000-0000-000000000001");

        when(freezeRepository.findById(unknownFreezeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> adapter.reactivate(
                        MEMBERSHIP_ID,
                        unknownFreezeId,
                        LocalDate.of(2026, 9, 10),
                        0L,
                        actor(),
                        OCCURRED_AT))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class)
                .hasMessage(
                        "An open freeze for membership "
                                + MEMBERSHIP_ID
                                + " was not found.");
    }

    @Test
    void shouldRejectAFreezeFromAnotherMembership() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        UUID anotherMembershipId =
                UUID.fromString(
                        "10000000-0000-0000-0000-000000000099");

        when(freezeRepository.findById(entity.id()))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(
                () -> adapter.reactivate(
                        anotherMembershipId,
                        entity.id(),
                        LocalDate.of(2026, 9, 10),
                        0L,
                        actor(),
                        OCCURRED_AT))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class);
    }

    @Test
    void shouldRejectAStaleFreezeVersion() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        when(freezeRepository.findById(entity.id()))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(
                () -> adapter.reactivate(
                        MEMBERSHIP_ID,
                        entity.id(),
                        LocalDate.of(2026, 9, 10),
                        1L,
                        actor(),
                        OCCURRED_AT))
                .isInstanceOf(
                        MembershipVersionConflictException.class);
    }

    private static MembershipFreezeJpaEntity
    openFreezeEntity() {

        return MembershipFreezeJpaEntity.create(
                membershipFreeze(),
                actor(),
                OCCURRED_AT);
    }

    private static MembershipFreeze membershipFreeze() {
        return new MembershipFreeze(
                MEMBERSHIP_ID,
                PERIOD_ID,
                STARTS_ON,
                PLANNED_ENDS_ON,
                "Medical leave");
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(
                ACTOR_ID,
                "front-desk");
    }
}
