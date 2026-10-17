package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipCancelled;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCancellation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MembershipCancellationApplicationServiceTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final UUID FREEZE_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "60000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-15T14:00:00Z");

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    @Mock
    private MembershipStore membershipStore;

    @Mock
    private MembershipFreezeStore freezeStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MembershipCancellationApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new MembershipCancellationApplicationService(
                        membershipStore,
                        freezeStore,
                        eventPublisher,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC));
    }

    @Test
    void shouldCancelActiveMembership() {
        MembershipDetails activeMembership =
                membership(
                        MembershipStatus.ACTIVE,
                        0L);

        MembershipDetails cancelledMembership =
                membership(
                        MembershipStatus.CANCELLED,
                        1L);

        when(membershipStore.findById(
                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(activeMembership));

        when(membershipStore.cancel(
                org.mockito.ArgumentMatchers.eq(
                        MEMBERSHIP_ID),
                any(MembershipCancellation.class),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(ACTOR),
                org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(cancelledMembership);

        MembershipDetails result =
                service.cancel(
                        MEMBERSHIP_ID,
                        new CancelMembershipCommand(
                                CANCELLED_ON,
                                "Client requested cancellation",
                                0L),
                        ACTOR);

        assertThat(result.status())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        verify(freezeStore, never())
                .findOpenByMembershipId(
                        any());

        verify(freezeStore, never())
                .closeForCancellation(
                        any(),
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        any(),
                        any());

        ArgumentCaptor<MembershipCancelled> captor =
                ArgumentCaptor.forClass(
                        MembershipCancelled.class);

        verify(eventPublisher)
                .publishEvent(
                        captor.capture());

        MembershipCancelled event =
                captor.getValue();

        assertThat(event.previousStatus())
                .isEqualTo(
                        MembershipStatus.ACTIVE);

        assertThat(event.resultingStatus())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        assertThat(event.closedOpenFreeze())
                .isFalse();

        assertThat(event.cancelledOn())
                .isEqualTo(CANCELLED_ON);
    }

    @Test
    void shouldCancelFrozenMembershipAndCloseFreeze() {
        MembershipDetails frozenMembership =
                membership(
                        MembershipStatus.FROZEN,
                        1L);

        MembershipDetails cancelledMembership =
                membership(
                        MembershipStatus.CANCELLED,
                        2L);

        MembershipFreezeDetails openFreeze =
                openFreeze();

        when(membershipStore.findById(
                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(frozenMembership));

        when(freezeStore.findOpenByMembershipId(
                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(openFreeze));

        when(freezeStore.closeForCancellation(
                MEMBERSHIP_ID,
                FREEZE_ID,
                CANCELLED_ON,
                0L,
                ACTOR,
                NOW))
                .thenReturn(
                        closedFreeze());

        when(membershipStore.cancel(
                org.mockito.ArgumentMatchers.eq(
                        MEMBERSHIP_ID),
                any(MembershipCancellation.class),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(ACTOR),
                org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(cancelledMembership);

        MembershipDetails result =
                service.cancel(
                        MEMBERSHIP_ID,
                        new CancelMembershipCommand(
                                CANCELLED_ON,
                                "Client requested cancellation",
                                1L),
                        ACTOR);

        assertThat(result.status())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        verify(freezeStore)
                .closeForCancellation(
                        MEMBERSHIP_ID,
                        FREEZE_ID,
                        CANCELLED_ON,
                        0L,
                        ACTOR,
                        NOW);

        ArgumentCaptor<MembershipCancelled> captor =
                ArgumentCaptor.forClass(
                        MembershipCancelled.class);

        verify(eventPublisher)
                .publishEvent(
                        captor.capture());

        assertThat(
                captor.getValue()
                        .closedOpenFreeze())
                .isTrue();

        assertThat(
                captor.getValue()
                        .previousStatus())
                .isEqualTo(
                        MembershipStatus.FROZEN);
    }

    @Test
    void shouldRejectStaleVersion() {
        when(membershipStore.findById(
                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(
                                membership(
                                        MembershipStatus.ACTIVE,
                                        2L)));

        assertThatThrownBy(
                () ->
                        service.cancel(
                                MEMBERSHIP_ID,
                                new CancelMembershipCommand(
                                        CANCELLED_ON,
                                        "Client requested cancellation",
                                        1L),
                                ACTOR))
                .isInstanceOf(
                        MembershipVersionConflictException.class);

        verify(membershipStore, never())
                .cancel(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void shouldRejectFrozenMembershipWithoutOpenFreeze() {
        when(membershipStore.findById(
                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(
                                membership(
                                        MembershipStatus.FROZEN,
                                        1L)));

        when(freezeStore.findOpenByMembershipId(
                MEMBERSHIP_ID))
                .thenReturn(
                        Optional.empty());

        assertThatThrownBy(
                () ->
                        service.cancel(
                                MEMBERSHIP_ID,
                                new CancelMembershipCommand(
                                        CANCELLED_ON,
                                        "Client requested cancellation",
                                        1L),
                                ACTOR))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class);

        verify(membershipStore, never())
                .cancel(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    private static MembershipDetails membership(
            MembershipStatus status,
            long version) {

        return new MembershipDetails(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                status,
                currentPeriod(),
                Instant.parse(
                        "2026-09-01T14:00:00Z"),
                NOW,
                version);
    }

    private static MembershipPeriodDetails currentPeriod() {
        return new MembershipPeriodDetails(
                PERIOD_ID,
                (short) 1,
                MembershipPeriodSource.INITIAL,
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD"),
                LocalDate.of(
                        2026,
                        9,
                        1),
                LocalDate.of(
                        2026,
                        10,
                        1),
                LocalDate.of(
                        2026,
                        10,
                        1),
                Instant.parse(
                        "2026-09-01T14:00:00Z"),
                0L);
    }

    private static MembershipFreezeDetails openFreeze() {
        return new MembershipFreezeDetails(
                FREEZE_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                LocalDate.of(
                        2026,
                        9,
                        10),
                LocalDate.of(
                        2026,
                        9,
                        20),
                "Medical leave",
                null,
                ACTOR_ID,
                null,
                null,
                null,
                Instant.parse(
                        "2026-09-10T14:00:00Z"),
                Instant.parse(
                        "2026-09-10T14:00:00Z"),
                0L);
    }

    private static MembershipFreezeDetails closedFreeze() {
        return new MembershipFreezeDetails(
                FREEZE_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                LocalDate.of(
                        2026,
                        9,
                        10),
                LocalDate.of(
                        2026,
                        9,
                        20),
                "Medical leave",
                null,
                ACTOR_ID,
                null,
                CANCELLED_ON,
                ACTOR_ID,
                Instant.parse(
                        "2026-09-10T14:00:00Z"),
                NOW,
                1L);
    }
}
