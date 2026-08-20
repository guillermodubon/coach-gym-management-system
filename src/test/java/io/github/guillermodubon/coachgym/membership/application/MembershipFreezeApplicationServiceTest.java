package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.*;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MembershipFreezeApplicationServiceTest {

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

    private static final LocalDate PERIOD_STARTS_ON =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PERIOD_ENDS_ON =
            LocalDate.of(2026, 10, 1);

    private static final LocalDate FREEZE_STARTS_ON =
            LocalDate.of(2026, 9, 10);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(2026, 9, 20);

    private static final Instant NOW =
            Instant.parse("2026-09-10T14:00:00Z");

    @Mock
    private MembershipStore membershipStore;

    @Mock
    private MembershipFreezeStore freezeStore;

    @Mock
    private ClientQuery clientQuery;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MembershipFreezeApplicationService service;

    private final AuthenticatedActor actor =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "front-desk");

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC);

        service =
                new MembershipFreezeApplicationService(
                        membershipStore,
                        freezeStore,
                        clientQuery,
                        eventPublisher,
                        clock);
    }

    @Test
    void shouldFreezeAnActiveMembership() {
        MembershipDetails activeMembership =
                membership(
                        MembershipStatus.ACTIVE,
                        0L);

        MembershipDetails frozenMembership =
                membership(
                        MembershipStatus.FROZEN,
                        1L);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(activeMembership));

        when(freezeStore.hasOpenFreeze(MEMBERSHIP_ID))
                .thenReturn(false);

        when(
                freezeStore.create(
                        any(MembershipFreeze.class),
                        any(AuthenticatedActor.class),
                        any(Instant.class)))
                .thenReturn(openFreeze());

        when(
                membershipStore.freeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        0L,
                        actor,
                        NOW))
                .thenReturn(frozenMembership);

        MembershipDetails result =
                service.freeze(
                        MEMBERSHIP_ID,
                        new FreezeMembershipCommand(
                                FREEZE_STARTS_ON,
                                PLANNED_ENDS_ON,
                                "Medical leave",
                                0L),
                        actor);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.FROZEN);

        assertThat(result.version())
                .isEqualTo(1L);

        verify(freezeStore)
                .create(
                        any(MembershipFreeze.class),
                        any(AuthenticatedActor.class),
                        any(Instant.class));

        verify(membershipStore)
                .freeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        0L,
                        actor,
                        NOW);

        verify(eventPublisher)
                .publishEvent(
                        any(MembershipFrozen.class));
    }

    @Test
    void shouldRejectAStaleVersionBeforePersistingFreeze() {
        MembershipDetails activeMembership =
                membership(
                        MembershipStatus.ACTIVE,
                        2L);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(activeMembership));

        assertThatThrownBy(
                () -> service.freeze(
                        MEMBERSHIP_ID,
                        new FreezeMembershipCommand(
                                FREEZE_STARTS_ON,
                                PLANNED_ENDS_ON,
                                "Medical leave",
                                1L),
                        actor))
                .isInstanceOf(
                        MembershipVersionConflictException.class);

        verify(freezeStore, never())
                .create(
                        any(),
                        any(),
                        any());
    }

    @Test
    void shouldRejectAnAlreadyFrozenMembership() {
        MembershipDetails frozenMembership =
                membership(
                        MembershipStatus.FROZEN,
                        1L);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(frozenMembership));

        when(freezeStore.hasOpenFreeze(MEMBERSHIP_ID))
                .thenReturn(true);

        assertThatThrownBy(
                () -> service.freeze(
                        MEMBERSHIP_ID,
                        new FreezeMembershipCommand(
                                FREEZE_STARTS_ON,
                                PLANNED_ENDS_ON,
                                "Medical leave",
                                1L),
                        actor))
                .isInstanceOf(
                        MembershipAlreadyFrozenException.class);

        verify(membershipStore, never())
                .freeze(
                        any(),
                        any(),
                        anyLong(),
                        any(),
                        any());
    }

    @Test
    void shouldReactivateAFrozenMembership() {
        MembershipDetails frozenMembership =
                membership(
                        MembershipStatus.FROZEN,
                        1L);

        MembershipDetails activeMembership =
                membership(
                        MembershipStatus.ACTIVE,
                        2L);

        MembershipFreezeDetails openFreeze =
                openFreeze();

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(frozenMembership));

        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(activeClient()));

        when(
                freezeStore.findOpenByMembershipId(
                        MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(openFreeze));

        when(
                freezeStore.reactivate(
                        MEMBERSHIP_ID,
                        FREEZE_ID,
                        LocalDate.of(2026, 9, 15),
                        0L,
                        actor,
                        NOW))
                .thenReturn(
                        closedFreeze());

        when(
                membershipStore.reactivate(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        1L,
                        actor,
                        NOW))
                .thenReturn(activeMembership);

        MembershipDetails result =
                service.reactivate(
                        MEMBERSHIP_ID,
                        new ReactivateMembershipCommand(
                                LocalDate.of(2026, 9, 15),
                                1L),
                        actor);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(result.version())
                .isEqualTo(2L);

        verify(freezeStore)
                .reactivate(
                        MEMBERSHIP_ID,
                        FREEZE_ID,
                        LocalDate.of(2026, 9, 15),
                        0L,
                        actor,
                        NOW);

        verify(membershipStore)
                .reactivate(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        1L,
                        actor,
                        NOW);
    }

    @Test
    void shouldRejectReactivationForAnInactiveClient() {
        MembershipDetails frozenMembership =
                membership(
                        MembershipStatus.FROZEN,
                        1L);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(frozenMembership));

        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(inactiveClient()));

        assertThatThrownBy(
                () -> service.reactivate(
                        MEMBERSHIP_ID,
                        new ReactivateMembershipCommand(
                                LocalDate.of(2026, 9, 15),
                                1L),
                        actor))
                .isInstanceOf(
                        InactiveMembershipClientException.class)
                .hasMessageContaining(
                        "cannot be reactivated");

        verify(freezeStore, never())
                .reactivate(
                        any(),
                        any(),
                        any(),
                        anyLong(),
                        any(),
                        any());
    }

    @Test
    void shouldRejectReactivationWithoutAnOpenFreeze() {
        MembershipDetails frozenMembership =
                membership(
                        MembershipStatus.FROZEN,
                        1L);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(frozenMembership));

        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(activeClient()));

        when(
                freezeStore.findOpenByMembershipId(
                        MEMBERSHIP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.reactivate(
                        MEMBERSHIP_ID,
                        new ReactivateMembershipCommand(
                                LocalDate.of(2026, 9, 15),
                                1L),
                        actor))
                .isInstanceOf(
                        MembershipFreezeNotFoundException.class);
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
                NOW,
                NOW,
                version);
    }

    private static MembershipPeriodDetails currentPeriod() {
        return new MembershipPeriodDetails(
                PERIOD_ID,
                (short) 1,
                MembershipPeriodSource.INITIAL,
                pricing(),
                PERIOD_STARTS_ON,
                PERIOD_ENDS_ON,
                PERIOD_ENDS_ON,
                NOW,
                0L);
    }

    private static MembershipPricingSnapshot pricing() {
        return MembershipPricingSnapshot.withoutPromotion(
                PLAN_ID,
                "PLAN-000001",
                "Monthly access",
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "USD");
    }

    private static MembershipFreezeDetails openFreeze() {
        return new MembershipFreezeDetails(
                FREEZE_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                FREEZE_STARTS_ON,
                PLANNED_ENDS_ON,
                "Medical leave",
                null,
                ACTOR_ID,
                null,
                NOW,
                NOW,
                0L);
    }

    private static MembershipFreezeDetails closedFreeze() {
        return new MembershipFreezeDetails(
                FREEZE_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                FREEZE_STARTS_ON,
                PLANNED_ENDS_ON,
                "Medical leave",
                LocalDate.of(2026, 9, 15),
                ACTOR_ID,
                ACTOR_ID,
                NOW,
                NOW,
                1L);
    }

    private static ClientDetails activeClient() {
        return client(ClientStatus.ACTIVE);
    }

    private static ClientDetails inactiveClient() {
        return client(ClientStatus.INACTIVE);
    }

    private static ClientDetails client(
            ClientStatus status) {

        return new ClientDetails(
                CLIENT_ID,
                "CLI-000001",
                "Ana",
                "Martinez",
                "ana@example.com",
                "+50370000000",
                LocalDate.of(1995, 4, 12),
                status,
                NOW,
                NOW,
                null);
    }
}
