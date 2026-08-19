package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipRenewal;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.PlanQuery;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationRequest;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluator;
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
class MembershipRenewalApplicationServiceTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "df531ab7-8515-4586-9872-dce8de5174f4");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "cc84991b-ff4d-494e-a995-eafb8a7ed5b5");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "7504bccd-a489-47ed-948d-d38fdeb799e8");

    private static final UUID INITIAL_PERIOD_ID =
            UUID.fromString(
                    "ad32c0a8-d633-49a8-86eb-9ea6604b944c");

    private static final UUID RENEWAL_PERIOD_ID =
            UUID.fromString(
                    "3d9da966-79cd-47ed-94c3-345667fc12f7");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "a316a34d-ba9a-4483-956f-f88e77e39ac7");

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-01T14:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC);

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    @Mock
    private MembershipStore membershipStore;

    @Mock
    private ClientQuery clientQuery;

    @Mock
    private PlanQuery planQuery;

    @Mock
    private PromotionEvaluator promotionEvaluator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MembershipApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new MembershipApplicationService(
                        membershipStore,
                        clientQuery,
                        planQuery,
                        promotionEvaluator,
                        eventPublisher,
                        CLOCK);
    }

    @Test
    void renewsActiveMembershipFromCurrentEndDate() {
        MembershipDetails current =
                membership(
                        MembershipStatus.ACTIVE,
                        INITIAL_PERIOD_ID,
                        (short) 1,
                        MembershipPeriodSource.INITIAL,
                        LocalDate.of(2026, 9, 15),
                        LocalDate.of(2026, 10, 15),
                        2);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(current));

        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(
                                activeClient()));

        when(planQuery.findActiveById(PLAN_ID))
                .thenReturn(
                        Optional.of(
                                plan()));

        when(membershipStore.renew(
                eq(MEMBERSHIP_ID),
                any(MembershipRenewal.class),
                eq(2L),
                eq(ACTOR),
                eq(NOW)))
                .thenAnswer(
                        invocation ->
                                renewedMembership(
                                        invocation.getArgument(1),
                                        3));

        MembershipDetails result =
                service.renew(
                        MEMBERSHIP_ID,
                        new RenewMembershipCommand(
                                PLAN_ID,
                                null,
                                LocalDate.of(2026, 12, 1),
                                2),
                        ACTOR);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(result.version())
                .isEqualTo(3);

        assertThat(
                result.currentPeriod()
                        .periodNumber())
                .isEqualTo((short) 2);

        assertThat(
                result.currentPeriod()
                        .source())
                .isEqualTo(
                        MembershipPeriodSource.RENEWAL);

        assertThat(
                result.currentPeriod()
                        .startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 15));

        assertThat(
                result.currentPeriod()
                        .baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 15));

        assertThat(
                result.currentPeriod()
                        .effectiveEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 15));

        assertThat(
                result.currentPeriod()
                        .pricing()
                        .discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(
                result.currentPeriod()
                        .pricing()
                        .finalPrice())
                .isEqualByComparingTo("25.00");

        verify(promotionEvaluator, never())
                .evaluate(
                        any(
                                PromotionEvaluationRequest.class));

        ArgumentCaptor<MembershipRenewal>
                renewalCaptor =
                ArgumentCaptor.forClass(
                        MembershipRenewal.class);

        verify(membershipStore)
                .renew(
                        eq(MEMBERSHIP_ID),
                        renewalCaptor.capture(),
                        eq(2L),
                        eq(ACTOR),
                        eq(NOW));

        MembershipRenewal renewal =
                renewalCaptor.getValue();

        assertThat(renewal.periodNumber())
                .isEqualTo((short) 2);

        assertThat(renewal.previousStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(renewal.resultingStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(renewal.changesMembershipStatus())
                .isFalse();

        assertThat(renewal.dates().startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 15));

        assertRenewedEvent(
                MembershipStatus.ACTIVE,
                MembershipStatus.ACTIVE,
                (short) 2,
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 15));
    }

    @Test
    void renewsExpiredMembershipFromRequestedDate() {
        MembershipDetails current =
                membership(
                        MembershipStatus.EXPIRED,
                        INITIAL_PERIOD_ID,
                        (short) 1,
                        MembershipPeriodSource.INITIAL,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 9, 1),
                        1);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(current));

        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(
                                activeClient()));

        when(planQuery.findActiveById(PLAN_ID))
                .thenReturn(
                        Optional.of(
                                plan()));

        when(membershipStore.renew(
                eq(MEMBERSHIP_ID),
                any(MembershipRenewal.class),
                eq(1L),
                eq(ACTOR),
                eq(NOW)))
                .thenAnswer(
                        invocation ->
                                renewedMembership(
                                        invocation.getArgument(1),
                                        2));

        MembershipDetails result =
                service.renew(
                        MEMBERSHIP_ID,
                        new RenewMembershipCommand(
                                PLAN_ID,
                                null,
                                LocalDate.of(2026, 10, 5),
                                1),
                        ACTOR);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(result.version())
                .isEqualTo(2);

        assertThat(
                result.currentPeriod()
                        .periodNumber())
                .isEqualTo((short) 2);

        assertThat(
                result.currentPeriod()
                        .source())
                .isEqualTo(
                        MembershipPeriodSource.RENEWAL);

        assertThat(
                result.currentPeriod()
                        .startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 5));

        assertThat(
                result.currentPeriod()
                        .baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 5));

        assertThat(
                result.currentPeriod()
                        .effectiveEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 5));

        assertRenewedEvent(
                MembershipStatus.EXPIRED,
                MembershipStatus.ACTIVE,
                (short) 2,
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 11, 5));
    }

    @Test
    void rejectsStaleVersionBeforeExternalQueries() {
        MembershipDetails current =
                membership(
                        MembershipStatus.ACTIVE,
                        INITIAL_PERIOD_ID,
                        (short) 1,
                        MembershipPeriodSource.INITIAL,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        4);

        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(
                        Optional.of(current));

        assertThatThrownBy(
                () ->
                        service.renew(
                                MEMBERSHIP_ID,
                                new RenewMembershipCommand(
                                        PLAN_ID,
                                        null,
                                        null,
                                        3),
                                ACTOR))
                .isInstanceOf(
                        MembershipVersionConflictException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " was modified by another operation. "
                                + "Expected version 3 but found 4.");

        verify(clientQuery, never())
                .findClientById(any());

        verify(planQuery, never())
                .findActiveById(any());

        verify(promotionEvaluator, never())
                .evaluate(any());

        verify(membershipStore, never())
                .renew(
                        any(),
                        any(),
                        anyLong(),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsMissingMembershipIdentifier() {
        assertThatThrownBy(
                () ->
                        service.renew(
                                null,
                                new RenewMembershipCommand(
                                        PLAN_ID,
                                        null,
                                        null,
                                        0),
                                ACTOR))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership identifier must be provided.");

        verify(membershipStore, never())
                .findById(any());
    }

    @Test
    void rejectsMissingRenewalCommand() {
        assertThatThrownBy(
                () ->
                        service.renew(
                                MEMBERSHIP_ID,
                                null,
                                ACTOR))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Renew membership command must be provided.");

        verify(membershipStore, never())
                .findById(any());
    }

    @Test
    void rejectsUnknownMembership() {
        when(membershipStore.findById(MEMBERSHIP_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        service.renew(
                                MEMBERSHIP_ID,
                                new RenewMembershipCommand(
                                        PLAN_ID,
                                        null,
                                        null,
                                        0),
                                ACTOR))
                .isInstanceOf(
                        MembershipNotFoundException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " was not found.");

        verify(clientQuery, never())
                .findClientById(any());

        verify(planQuery, never())
                .findActiveById(any());

        verify(membershipStore, never())
                .renew(
                        any(),
                        any(),
                        anyLong(),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    private static ClientDetails activeClient() {
        return new ClientDetails(
                CLIENT_ID,
                "CLI-000001",
                "Ana",
                "Martinez",
                "ana@example.com",
                "+50370000000",
                LocalDate.of(1995, 4, 12),
                ClientStatus.ACTIVE,
                NOW.minusSeconds(3_600),
                NOW,
                null);
    }

    private static PlanDetails plan() {
        return new PlanDetails(
                PLAN_ID,
                "PLAN-000001",
                "Monthly Access",
                "Monthly gym access.",
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "USD",
                true,
                NOW.minusSeconds(3_600),
                NOW,
                0);
    }

    private static MembershipDetails membership(
            MembershipStatus status,
            UUID periodId,
            short periodNumber,
            MembershipPeriodSource source,
            LocalDate startsOn,
            LocalDate effectiveEndsOn,
            long version) {

        MembershipPricingSnapshot pricing =
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD");

        MembershipPeriodDetails period =
                new MembershipPeriodDetails(
                        periodId,
                        periodNumber,
                        source,
                        pricing,
                        startsOn,
                        effectiveEndsOn,
                        effectiveEndsOn,
                        NOW.minusSeconds(3_600),
                        0);

        return new MembershipDetails(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                status,
                period,
                NOW.minusSeconds(7_200),
                NOW,
                version);
    }

    private static MembershipDetails renewedMembership(
            MembershipRenewal renewal,
            long resultingVersion) {

        MembershipPeriodDetails period =
                new MembershipPeriodDetails(
                        RENEWAL_PERIOD_ID,
                        renewal.periodNumber(),
                        MembershipPeriodSource.RENEWAL,
                        renewal.pricing(),
                        renewal.dates().startsOn(),
                        renewal.dates().baseEndsOn(),
                        renewal.dates().effectiveEndsOn(),
                        NOW,
                        0);

        return new MembershipDetails(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                renewal.resultingStatus(),
                period,
                NOW.minusSeconds(7_200),
                NOW,
                resultingVersion);
    }

    private void assertRenewedEvent(
            MembershipStatus expectedPreviousStatus,
            MembershipStatus expectedResultingStatus,
            short expectedPeriodNumber,
            LocalDate expectedStartsOn,
            LocalDate expectedEffectiveEndsOn) {

        ArgumentCaptor<MembershipRenewed> eventCaptor =
                ArgumentCaptor.forClass(
                        MembershipRenewed.class);

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture());

        MembershipRenewed event =
                eventCaptor.getValue();

        assertThat(event.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(event.membershipCode())
                .isEqualTo("MEM-000001");

        assertThat(event.clientId())
                .isEqualTo(CLIENT_ID);

        assertThat(event.membershipPeriodId())
                .isEqualTo(RENEWAL_PERIOD_ID);

        assertThat(event.periodNumber())
                .isEqualTo(expectedPeriodNumber);

        assertThat(event.membershipPlanId())
                .isEqualTo(PLAN_ID);

        assertThat(event.promotionId())
                .isNull();

        assertThat(event.listPrice())
                .isEqualByComparingTo("25.00");

        assertThat(event.discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(event.finalPrice())
                .isEqualByComparingTo("25.00");

        assertThat(event.currency())
                .isEqualTo("USD");

        assertThat(event.startsOn())
                .isEqualTo(expectedStartsOn);

        assertThat(event.effectiveEndsOn())
                .isEqualTo(expectedEffectiveEndsOn);

        assertThat(event.previousStatus())
                .isEqualTo(expectedPreviousStatus);

        assertThat(event.resultingStatus())
                .isEqualTo(expectedResultingStatus);

        assertThat(event.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(event.actorIdentifier())
                .isEqualTo("coach-admin");

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }
}