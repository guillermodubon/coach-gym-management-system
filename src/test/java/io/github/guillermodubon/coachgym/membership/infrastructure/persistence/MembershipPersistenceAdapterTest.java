package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPeriodDates;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipRenewal;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
class MembershipPersistenceAdapterTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "b8af55d6-d48d-45e4-9187-d1815d561da8");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "4140756d-4026-42c9-b18a-9d12c4c1b07a");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "de1c98dc-79d4-4cd2-bb73-1222855ff309");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "e48b165a-65f7-4a92-ae39-727e3c3e084d");

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-01T14:00:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    @Mock
    private MembershipJpaRepository membershipRepository;

    @Mock
    private MembershipPeriodJpaRepository periodRepository;

    @Mock
    private MembershipStatusHistoryJpaRepository
            statusHistoryRepository;

    @Mock
    private EntityManager entityManager;

    private MembershipPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter =
                new MembershipPersistenceAdapter(
                        membershipRepository,
                        periodRepository,
                        statusHistoryRepository,
                        entityManager);
    }

    @Test
    void persistsRenewalWithoutStatusHistoryWhenStatusDoesNotChange() {
        MembershipJpaEntity membership =
                MembershipJpaEntity.create(
                        CLIENT_ID,
                        ACTOR,
                        NOW.minusSeconds(3_600));

        MembershipPeriodJpaEntity currentPeriod =
                period(
                        (short) 1);

        MembershipRenewal renewal =
                renewal(
                        MembershipStatus.ACTIVE,
                        MembershipStatus.ACTIVE);

        when(membershipRepository.findByIdForUpdate(
                MEMBERSHIP_ID))
                .thenReturn(Optional.of(membership));

        when(periodRepository
                .findFirstByMembershipIdOrderByPeriodNumberDesc(
                        MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(currentPeriod));

        when(membershipRepository.saveAndFlush(
                membership))
                .thenReturn(membership);

        when(periodRepository.saveAndFlush(any()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0));

        MembershipDetails result =
                adapter.renew(
                        MEMBERSHIP_ID,
                        renewal,
                        0,
                        ACTOR,
                        NOW);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(result.currentPeriod().periodNumber())
                .isEqualTo((short) 2);

        verify(statusHistoryRepository, never())
                .saveAndFlush(any());
    }

    private static MembershipRenewal renewal(
            MembershipStatus previousStatus,
            MembershipStatus resultingStatus) {

        return new MembershipRenewal(
                (short) 2,
                previousStatus,
                resultingStatus,
                new MembershipPeriodDates(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 1)),
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD"));
    }

    private static MembershipPeriodJpaEntity period(
            short periodNumber) {

        MembershipPeriodJpaEntity period =
                MembershipPeriodJpaEntity.initial(
                        MEMBERSHIP_ID,
                        new io.github.guillermodubon.coachgym.membership.domain
                                .MembershipCreation(
                                CLIENT_ID,
                                new MembershipPeriodDates(
                                        LocalDate.of(2026, 9, 1),
                                        LocalDate.of(2026, 10, 1),
                                        LocalDate.of(2026, 10, 1)),
                                MembershipPricingSnapshot.withoutPromotion(
                                        PLAN_ID,
                                        "PLAN-000001",
                                        "Monthly Access",
                                        1,
                                        DurationUnit.MONTH,
                                        new BigDecimal("25.00"),
                                        "USD")),
                        ACTOR,
                        NOW.minusSeconds(3_600));

        org.springframework.test.util.ReflectionTestUtils.setField(
                period,
                "periodNumber",
                periodNumber);

        return period;
    }

    @Test
    void shouldPersistFrozenStatusAndHistory() {
        MembershipJpaEntity membership =
                membershipEntity(
                        MembershipStatus.ACTIVE);

        MembershipPeriodJpaEntity period =
                period((short) 1);

        when(membershipRepository.findByIdForUpdate(
                MEMBERSHIP_ID))
                .thenReturn(Optional.of(membership));

        when(
                periodRepository
                        .findFirstByMembershipIdOrderByPeriodNumberDesc(
                                MEMBERSHIP_ID))
                .thenReturn(Optional.of(period));

        when(membershipRepository.saveAndFlush(
                membership))
                .thenReturn(membership);

        MembershipDetails result =
                adapter.freeze(
                        MEMBERSHIP_ID,
                        period.id(),
                        0L,
                        ACTOR,
                        NOW);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.FROZEN);

        assertThat(result.currentPeriod().id())
                .isEqualTo(period.id());

        verify(statusHistoryRepository)
                .saveAndFlush(
                        any(
                                MembershipStatusHistoryJpaEntity.class));

        verify(periodRepository, never())
                .saveAndFlush(
                        any(MembershipPeriodJpaEntity.class));
    }

    @Test
    void shouldPersistReactivatedStatusAndHistory() {
        MembershipJpaEntity membership =
                membershipEntity(
                        MembershipStatus.FROZEN);

        MembershipPeriodJpaEntity period =
                period((short) 1);

        when(membershipRepository.findByIdForUpdate(
                MEMBERSHIP_ID))
                .thenReturn(Optional.of(membership));

        when(
                periodRepository
                        .findFirstByMembershipIdOrderByPeriodNumberDesc(
                                MEMBERSHIP_ID))
                .thenReturn(Optional.of(period));

        when(membershipRepository.saveAndFlush(
                membership))
                .thenReturn(membership);

        MembershipDetails result =
                adapter.reactivate(
                        MEMBERSHIP_ID,
                        period.id(),
                        0L,
                        ACTOR,
                        NOW);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(result.currentPeriod().id())
                .isEqualTo(period.id());

        verify(statusHistoryRepository)
                .saveAndFlush(
                        any(
                                MembershipStatusHistoryJpaEntity.class));

        verify(periodRepository, never())
                .saveAndFlush(
                        any(MembershipPeriodJpaEntity.class));
    }

    private static MembershipJpaEntity membershipEntity(
            MembershipStatus status) {

        MembershipJpaEntity membership =
                MembershipJpaEntity.create(
                        CLIENT_ID,
                        ACTOR,
                        NOW.minusSeconds(3_600));

        org.springframework.test.util.ReflectionTestUtils.setField(
                membership,
                "id",
                MEMBERSHIP_ID);

        org.springframework.test.util.ReflectionTestUtils.setField(
                membership,
                "status",
                status);

        return membership;
    }

}