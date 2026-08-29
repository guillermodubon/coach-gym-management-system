package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPeriodDates;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipAccessQueryAdapterTest {

    // ── Fixed IDs ─────────────────────────────────────────────────────────────

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final UUID PLAN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-08-28T18:00:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "access-admin");

    // ── Date fixtures ─────────────────────────────────────────────────────────

    private static final LocalDate PERIOD_STARTS =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PERIOD_ENDS =
            LocalDate.of(2026, 9, 30);

    private static final LocalDate FREEZE_STARTS =
            LocalDate.of(2026, 9, 10);

    private static final LocalDate FREEZE_ENDS =
            LocalDate.of(2026, 9, 20);

    // ── Collaborators ─────────────────────────────────────────────────────────

    private MembershipJpaRepository membershipRepository;
    private MembershipPeriodJpaRepository periodRepository;
    private MembershipFreezeJpaRepository freezeRepository;
    private MembershipAccessQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        membershipRepository = mock(MembershipJpaRepository.class);
        periodRepository = mock(MembershipPeriodJpaRepository.class);
        freezeRepository = mock(MembershipFreezeJpaRepository.class);
        adapter = new MembershipAccessQueryAdapter(
                membershipRepository, periodRepository, freezeRepository);
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Test
    void returnsMembershipDetailsForActiveByCode() {
        MembershipJpaEntity membership = activeMembershipEntity();
        MembershipPeriodJpaEntity period = periodEntity(MEMBERSHIP_ID);

        given(membershipRepository.findByMembershipCodeIgnoreCase("MEM-000001"))
                .willReturn(Optional.of(membership));
        given(periodRepository.findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id()))
                .willReturn(Optional.of(period));
        given(freezeRepository.findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                membership.id()))
                .willReturn(Optional.empty());

        Optional<MembershipAccessDetails> result = adapter.findByCode("MEM-000001");

        assertThat(result).isPresent();
        MembershipAccessDetails details = result.get();
        assertThat(details.clientId()).isEqualTo(CLIENT_ID);
        assertThat(details.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(details.periodStartsOn()).isEqualTo(PERIOD_STARTS);
        assertThat(details.periodEffectiveEndsOn()).isEqualTo(PERIOD_ENDS);
        assertThat(details.freezeStartsOn()).isNull();
        assertThat(details.freezePlannedEndsOn()).isNull();
    }

    @Test
    void returnsMembershipDetailsWithOpenFreezeByCode() {
        MembershipJpaEntity membership = frozenMembershipEntity();
        MembershipPeriodJpaEntity period = periodEntity(MEMBERSHIP_ID);
        MembershipFreezeJpaEntity freeze = openFreezeEntity(MEMBERSHIP_ID);

        given(membershipRepository.findByMembershipCodeIgnoreCase("MEM-000001"))
                .willReturn(Optional.of(membership));
        given(periodRepository.findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id()))
                .willReturn(Optional.of(period));
        given(freezeRepository.findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                membership.id()))
                .willReturn(Optional.of(freeze));

        Optional<MembershipAccessDetails> result = adapter.findByCode("MEM-000001");

        assertThat(result).isPresent();
        MembershipAccessDetails details = result.get();
        assertThat(details.status()).isEqualTo(MembershipStatus.FROZEN);
        assertThat(details.freezeStartsOn()).isEqualTo(FREEZE_STARTS);
        assertThat(details.freezePlannedEndsOn()).isEqualTo(FREEZE_ENDS);
    }

    @Test
    void returnsEmptyWhenMembershipCodeNotFound() {
        given(membershipRepository.findByMembershipCodeIgnoreCase("MEM-999999"))
                .willReturn(Optional.empty());

        Optional<MembershipAccessDetails> result = adapter.findByCode("MEM-999999");

        assertThat(result).isEmpty();
        verify(periodRepository, never())
                .findFirstByMembershipIdOrderByPeriodNumberDesc(MEMBERSHIP_ID);
    }

    @Test
    void returnsEmptyWhenMembershipHasNoPeriod() {
        // No period → empty Optional (application service will surface as ISE)
        MembershipJpaEntity membership = activeMembershipEntity();

        given(membershipRepository.findByMembershipCodeIgnoreCase("MEM-000001"))
                .willReturn(Optional.of(membership));
        given(periodRepository.findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id()))
                .willReturn(Optional.empty());

        Optional<MembershipAccessDetails> result = adapter.findByCode("MEM-000001");

        assertThat(result).isEmpty();
    }

    // ── findCurrentByClientId ─────────────────────────────────────────────────

    @Test
    void returnsActiveMembershipByClientId() {
        MembershipJpaEntity membership = activeMembershipEntity();
        MembershipPeriodJpaEntity period = periodEntity(MEMBERSHIP_ID);

        given(membershipRepository.findFirstByClientIdAndStatusIn(
                CLIENT_ID, EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN)))
                .willReturn(Optional.of(membership));
        given(periodRepository.findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id()))
                .willReturn(Optional.of(period));
        given(freezeRepository.findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                membership.id()))
                .willReturn(Optional.empty());

        Optional<MembershipAccessDetails> result =
                adapter.findCurrentByClientId(CLIENT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().clientId()).isEqualTo(CLIENT_ID);
        assertThat(result.get().status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void returnsFrozenMembershipByClientId() {
        MembershipJpaEntity membership = frozenMembershipEntity();
        MembershipPeriodJpaEntity period = periodEntity(MEMBERSHIP_ID);
        MembershipFreezeJpaEntity freeze = openFreezeEntity(MEMBERSHIP_ID);

        given(membershipRepository.findFirstByClientIdAndStatusIn(
                CLIENT_ID, EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN)))
                .willReturn(Optional.of(membership));
        given(periodRepository.findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id()))
                .willReturn(Optional.of(period));
        given(freezeRepository.findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                membership.id()))
                .willReturn(Optional.of(freeze));

        Optional<MembershipAccessDetails> result =
                adapter.findCurrentByClientId(CLIENT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MembershipStatus.FROZEN);
        assertThat(result.get().freezeStartsOn()).isEqualTo(FREEZE_STARTS);
    }

    @Test
    void returnsEmptyWhenNoCurrentMembershipForClient() {
        given(membershipRepository.findFirstByClientIdAndStatusIn(
                CLIENT_ID, EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN)))
                .willReturn(Optional.empty());

        Optional<MembershipAccessDetails> result =
                adapter.findCurrentByClientId(CLIENT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotQueryFreezeWhenMembershipNotFound() {
        given(membershipRepository.findFirstByClientIdAndStatusIn(
                CLIENT_ID, EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN)))
                .willReturn(Optional.empty());

        adapter.findCurrentByClientId(CLIENT_ID);

        verify(freezeRepository, never())
                .findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(MEMBERSHIP_ID);
    }

    @Test
    void exposesCurrentPeriodId() {
        MembershipJpaEntity membership = activeMembershipEntity();
        MembershipPeriodJpaEntity period = periodEntity(MEMBERSHIP_ID);

        given(membershipRepository.findByMembershipCodeIgnoreCase("MEM-000001"))
                .willReturn(Optional.of(membership));
        given(periodRepository.findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id()))
                .willReturn(Optional.of(period));
        given(freezeRepository.findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                membership.id()))
                .willReturn(Optional.empty());

        MembershipAccessDetails details =
                adapter.findByCode("MEM-000001").orElseThrow();

        assertThat(details.currentPeriodId()).isNotNull();
        assertThat(details.periodStartsOn()).isEqualTo(PERIOD_STARTS);
        assertThat(details.periodEffectiveEndsOn()).isEqualTo(PERIOD_ENDS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MembershipJpaEntity activeMembershipEntity() {
        return MembershipJpaEntity.create(CLIENT_ID, ACTOR, NOW);
    }

    private static MembershipJpaEntity frozenMembershipEntity() {
        MembershipJpaEntity entity = MembershipJpaEntity.create(CLIENT_ID, ACTOR, NOW);
        entity.changeStatus(
                MembershipStatus.ACTIVE,
                MembershipStatus.FROZEN,
                ACTOR, NOW);
        return entity;
    }

    private static MembershipPeriodJpaEntity periodEntity(UUID membershipId) {
        MembershipPricingSnapshot pricing =
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("50.00"),
                        "USD");

        MembershipPeriodDates dates = new MembershipPeriodDates(
                PERIOD_STARTS,
                PERIOD_ENDS,
                PERIOD_ENDS);

        MembershipCreation creation = new MembershipCreation(CLIENT_ID, dates, pricing);

        return MembershipPeriodJpaEntity.initial(membershipId, creation, ACTOR, NOW);
    }

    private static MembershipFreezeJpaEntity openFreezeEntity(UUID membershipId) {
        UUID periodId = UUID.fromString("30000000-0000-0000-0000-000000000001");

        io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze freeze =
                new io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze(
                        membershipId,
                        periodId,
                        FREEZE_STARTS,
                        FREEZE_ENDS,
                        "Injury recovery");

        return MembershipFreezeJpaEntity.create(freeze, ACTOR, NOW);
    }
}
