package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private static final UUID UNKNOWN_FREEZE_ID =
            UUID.fromString(
                    "90000000-0000-0000-0000-000000000001");

    private static final UUID ANOTHER_MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000099");

    private static final LocalDate STARTS_ON =
            LocalDate.of(
                    2026,
                    9,
                    1);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    private static final LocalDate REACTIVATED_ON =
            LocalDate.of(
                    2026,
                    9,
                    10);

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    private static final Instant OCCURRED_AT =
            Instant.parse(
                    "2026-09-01T14:00:00Z");

    private static final Instant REACTIVATED_AT =
            Instant.parse(
                    "2026-09-10T14:00:00Z");

    private static final Instant CANCELLED_AT =
            Instant.parse(
                    "2026-09-15T14:00:00Z");

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
                        .findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(entity));

        Optional<MembershipFreezeDetails> result =
                adapter.findOpenByMembershipId(
                        MEMBERSHIP_ID);

        assertThat(result)
                .isPresent();

        assertThat(
                result.orElseThrow()
                        .membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(
                result.orElseThrow()
                        .open())
                .isTrue();

        assertThat(
                result.orElseThrow()
                        .reactivated())
                .isFalse();

        assertThat(
                result.orElseThrow()
                        .closedByCancellation())
                .isFalse();
    }

    @Test
    void shouldReturnEmptyWhenAnOpenFreezeDoesNotExist() {
        when(
                freezeRepository
                        .findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.empty());

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
                        .existsByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
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

        when(
                freezeRepository.saveAndFlush(
                        any(
                                MembershipFreezeJpaEntity.class)))
                .thenAnswer(
                        invocation ->
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

        assertThat(result.reactivatedOn())
                .isNull();

        assertThat(result.reactivatedByUserId())
                .isNull();

        assertThat(result.cancelledOn())
                .isNull();

        assertThat(result.cancelledByUserId())
                .isNull();

        verify(freezeRepository)
                .saveAndFlush(
                        any(
                                MembershipFreezeJpaEntity.class));
    }

    @Test
    void shouldReactivateAnOpenFreeze() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        UUID freezeId =
                entity.id();

        when(
                freezeRepository.findById(
                        freezeId))
                .thenReturn(
                        Optional.of(entity));

        when(
                freezeRepository.saveAndFlush(
                        entity))
                .thenReturn(entity);

        MembershipFreezeDetails result =
                adapter.reactivate(
                        MEMBERSHIP_ID,
                        freezeId,
                        REACTIVATED_ON,
                        0L,
                        actor(),
                        REACTIVATED_AT);

        assertThat(result.reactivatedOn())
                .isEqualTo(REACTIVATED_ON);

        assertThat(result.reactivatedByUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(result.cancelledOn())
                .isNull();

        assertThat(result.cancelledByUserId())
                .isNull();

        assertThat(result.open())
                .isFalse();

        assertThat(result.reactivated())
                .isTrue();

        assertThat(result.closedByCancellation())
                .isFalse();

        assertThat(result.updatedAt())
                .isEqualTo(REACTIVATED_AT);

        verify(freezeRepository)
                .saveAndFlush(entity);
    }

    @Test
    void shouldRejectAnUnknownFreeze() {
        when(
                freezeRepository.findById(
                        UNKNOWN_FREEZE_ID))
                .thenReturn(
                        Optional.empty());

        assertThatThrownBy(
                () ->
                        adapter.reactivate(
                                MEMBERSHIP_ID,
                                UNKNOWN_FREEZE_ID,
                                REACTIVATED_ON,
                                0L,
                                actor(),
                                REACTIVATED_AT))
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

        when(
                freezeRepository.findById(
                        entity.id()))
                .thenReturn(
                        Optional.of(entity));

        assertThatThrownBy(
                () ->
                        adapter.reactivate(
                                ANOTHER_MEMBERSHIP_ID,
                                entity.id(),
                                REACTIVATED_ON,
                                0L,
                                actor(),
                                REACTIVATED_AT))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class);
    }

    @Test
    void shouldRejectAStaleFreezeVersion() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        when(
                freezeRepository.findById(
                        entity.id()))
                .thenReturn(
                        Optional.of(entity));

        assertThatThrownBy(
                () ->
                        adapter.reactivate(
                                MEMBERSHIP_ID,
                                entity.id(),
                                REACTIVATED_ON,
                                1L,
                                actor(),
                                REACTIVATED_AT))
                .isInstanceOf(
                        MembershipVersionConflictException.class);
    }

    @Test
    void shouldCloseFreezeForCancellation() {
        AuthenticatedActor cancellationActor =
                actor();

        MembershipFreezeJpaEntity entity =
                MembershipFreezeJpaEntity.create(
                        membershipFreeze(),
                        cancellationActor,
                        OCCURRED_AT);

        when(
                freezeRepository.findById(
                        entity.id()))
                .thenReturn(
                        Optional.of(entity));

        when(
                freezeRepository.saveAndFlush(
                        entity))
                .thenReturn(entity);

        MembershipFreezeDetails result =
                adapter.closeForCancellation(
                        MEMBERSHIP_ID,
                        entity.id(),
                        CANCELLED_ON,
                        0L,
                        cancellationActor,
                        CANCELLED_AT);

        assertThat(result.open())
                .isFalse();

        assertThat(result.reactivated())
                .isFalse();

        assertThat(result.closedByCancellation())
                .isTrue();

        assertThat(result.cancelledOn())
                .isEqualTo(CANCELLED_ON);

        assertThat(result.cancelledByUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(result.reactivatedOn())
                .isNull();

        assertThat(result.reactivatedByUserId())
                .isNull();

        assertThat(result.updatedAt())
                .isEqualTo(CANCELLED_AT);

        verify(freezeRepository)
                .saveAndFlush(entity);
    }

    @Test
    void shouldRejectCancellationOfAnUnknownFreeze() {
        when(
                freezeRepository.findById(
                        UNKNOWN_FREEZE_ID))
                .thenReturn(
                        Optional.empty());

        assertThatThrownBy(
                () ->
                        adapter.closeForCancellation(
                                MEMBERSHIP_ID,
                                UNKNOWN_FREEZE_ID,
                                CANCELLED_ON,
                                0L,
                                actor(),
                                CANCELLED_AT))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class)
                .hasMessage(
                        "An open freeze for membership "
                                + MEMBERSHIP_ID
                                + " was not found.");
    }

    @Test
    void shouldRejectCancellationOfAFreezeFromAnotherMembership() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        when(
                freezeRepository.findById(
                        entity.id()))
                .thenReturn(
                        Optional.of(entity));

        assertThatThrownBy(
                () ->
                        adapter.closeForCancellation(
                                ANOTHER_MEMBERSHIP_ID,
                                entity.id(),
                                CANCELLED_ON,
                                0L,
                                actor(),
                                CANCELLED_AT))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class);
    }

    @Test
    void shouldRejectCancellationWithAStaleFreezeVersion() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        when(
                freezeRepository.findById(
                        entity.id()))
                .thenReturn(
                        Optional.of(entity));

        assertThatThrownBy(
                () ->
                        adapter.closeForCancellation(
                                MEMBERSHIP_ID,
                                entity.id(),
                                CANCELLED_ON,
                                1L,
                                actor(),
                                CANCELLED_AT))
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