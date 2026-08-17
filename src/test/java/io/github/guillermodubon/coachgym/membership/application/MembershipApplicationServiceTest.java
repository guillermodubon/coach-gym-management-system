package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.PlanQuery;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationRequest;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationResult;
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
class MembershipApplicationServiceTest {

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "a5c30e03-af67-40e3-b455-5c9f95a48b02");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "1323ef74-1e60-47fc-a541-b2718a2418d0");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "eca2a2fe-e750-4a56-98f5-c83dd5484b7f");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "f8851b5f-00c3-4485-ac02-e30182d97782");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "c864c4de-c0a1-4b65-b601-8d5a2e263a96");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "8dee33da-f10b-4986-a68f-7d19306244cc");

    private static final LocalDate STARTS_ON =
            LocalDate.of(2026, 9, 1);

    private static final Instant NOW =
            Instant.parse("2026-08-16T20:00:00Z");

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
    void createsMembershipWithoutPromotion() {
        prepareActiveClientAndPlan();

        when(membershipStore.existsCurrentByClientId(
                CLIENT_ID))
                .thenReturn(false);

        when(membershipStore.create(
                any(MembershipCreation.class),
                eq(ACTOR),
                eq(NOW)))
                .thenAnswer(
                        invocation ->
                                membershipFrom(
                                        invocation.getArgument(0)));

        MembershipDetails result =
                service.create(
                        command(null),
                        ACTOR);

        assertThat(result.id())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(result.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        MembershipPricingSnapshot pricing =
                result.currentPeriod().pricing();

        assertThat(pricing.promotion())
                .isNull();

        assertThat(pricing.listPrice())
                .isEqualByComparingTo("25.00");

        assertThat(pricing.discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(pricing.finalPrice())
                .isEqualByComparingTo("25.00");

        assertThat(result.currentPeriod().baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 1));

        verify(promotionEvaluator, never())
                .evaluate(any());

        assertCreatedEvent(
                null,
                "0.00",
                "25.00");
    }

    @Test
    void createsMembershipWithPromotion() {
        prepareActiveClientAndPlan();

        when(membershipStore.existsCurrentByClientId(
                CLIENT_ID))
                .thenReturn(false);

        when(promotionEvaluator.evaluate(
                any(PromotionEvaluationRequest.class)))
                .thenReturn(
                        promotionEvaluation());

        when(membershipStore.create(
                any(MembershipCreation.class),
                eq(ACTOR),
                eq(NOW)))
                .thenAnswer(
                        invocation ->
                                membershipFrom(
                                        invocation.getArgument(0)));

        MembershipDetails result =
                service.create(
                        command(PROMOTION_ID),
                        ACTOR);

        MembershipPricingSnapshot pricing =
                result.currentPeriod().pricing();

        assertThat(pricing.promotion())
                .isNotNull();

        assertThat(
                pricing.promotion()
                        .promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(pricing.discountAmount())
                .isEqualByComparingTo("2.50");

        assertThat(pricing.finalPrice())
                .isEqualByComparingTo("22.50");

        ArgumentCaptor<PromotionEvaluationRequest>
                requestCaptor =
                ArgumentCaptor.forClass(
                        PromotionEvaluationRequest.class);

        verify(promotionEvaluator)
                .evaluate(requestCaptor.capture());

        PromotionEvaluationRequest request =
                requestCaptor.getValue();

        assertThat(request.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(request.membershipPlanId())
                .isEqualTo(PLAN_ID);

        assertThat(request.listPrice())
                .isEqualByComparingTo("25.00");

        assertThat(request.currency())
                .isEqualTo("USD");

        assertThat(request.applicableOn())
                .isEqualTo(STARTS_ON);

        assertCreatedEvent(
                PROMOTION_ID,
                "2.50",
                "22.50");
    }

    @Test
    void rejectsUnknownClient() {
        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        service.create(
                                command(null),
                                ACTOR))
                .isInstanceOf(
                        MembershipClientNotFoundException.class)
                .hasMessage(
                        "Client "
                                + CLIENT_ID
                                + " was not found.");

        verify(planQuery, never())
                .findActiveById(any());

        verify(membershipStore, never())
                .create(
                        any(),
                        any(),
                        any());
    }

    @Test
    void rejectsInactiveClient() {
        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(
                                client(ClientStatus.INACTIVE)));

        assertThatThrownBy(
                () ->
                        service.create(
                                command(null),
                                ACTOR))
                .isInstanceOf(
                        InactiveMembershipClientException.class)
                .hasMessage(
                        "Client "
                                + CLIENT_ID
                                + " is inactive and cannot receive "
                                + "a new membership.");

        verify(planQuery, never())
                .findActiveById(any());

        verify(membershipStore, never())
                .create(
                        any(),
                        any(),
                        any());
    }

    @Test
    void rejectsUnavailablePlan() {
        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(
                                client(ClientStatus.ACTIVE)));

        when(planQuery.findActiveById(PLAN_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        service.create(
                                command(null),
                                ACTOR))
                .isInstanceOf(
                        MembershipPlanNotAvailableException.class)
                .hasMessage(
                        "Membership plan "
                                + PLAN_ID
                                + " was not found or is inactive.");

        verify(membershipStore, never())
                .existsCurrentByClientId(any());

        verify(membershipStore, never())
                .create(
                        any(),
                        any(),
                        any());
    }

    @Test
    void rejectsClientWithCurrentMembership() {
        prepareActiveClientAndPlan();

        when(membershipStore.existsCurrentByClientId(
                CLIENT_ID))
                .thenReturn(true);

        assertThatThrownBy(
                () ->
                        service.create(
                                command(null),
                                ACTOR))
                .isInstanceOf(
                        CurrentMembershipAlreadyExistsException.class)
                .hasMessage(
                        "Client "
                                + CLIENT_ID
                                + " already has an active "
                                + "or frozen membership.");

        verify(promotionEvaluator, never())
                .evaluate(any());

        verify(membershipStore, never())
                .create(
                        any(),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsMissingCommand() {
        assertThatThrownBy(
                () ->
                        service.create(
                                null,
                                ACTOR))
                .isInstanceOf(
                        io.github.guillermodubon.coachgym
                                .membership.domain
                                .MembershipValidationException.class)
                .hasMessage(
                        "Create membership command must be provided.");

        verify(clientQuery, never())
                .findClientById(any());
    }

    private void prepareActiveClientAndPlan() {
        when(clientQuery.findClientById(CLIENT_ID))
                .thenReturn(
                        Optional.of(
                                client(ClientStatus.ACTIVE)));

        when(planQuery.findActiveById(PLAN_ID))
                .thenReturn(
                        Optional.of(plan()));
    }

    private void assertCreatedEvent(
            UUID expectedPromotionId,
            String expectedDiscount,
            String expectedFinalPrice) {

        ArgumentCaptor<MembershipCreated> eventCaptor =
                ArgumentCaptor.forClass(
                        MembershipCreated.class);

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture());

        MembershipCreated event =
                eventCaptor.getValue();

        assertThat(event.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(event.clientId())
                .isEqualTo(CLIENT_ID);

        assertThat(event.membershipPeriodId())
                .isEqualTo(PERIOD_ID);

        assertThat(event.membershipPlanId())
                .isEqualTo(PLAN_ID);

        assertThat(event.promotionId())
                .isEqualTo(expectedPromotionId);

        assertThat(event.discountAmount())
                .isEqualByComparingTo(
                        expectedDiscount);

        assertThat(event.finalPrice())
                .isEqualByComparingTo(
                        expectedFinalPrice);

        assertThat(event.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(event.actorIdentifier())
                .isEqualTo("coach-admin");

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    private static CreateMembershipCommand command(
            UUID promotionId) {

        return new CreateMembershipCommand(
                CLIENT_ID,
                PLAN_ID,
                promotionId,
                STARTS_ON);
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

    private static PromotionEvaluationResult
    promotionEvaluation() {

        return new PromotionEvaluationResult(
                PROMOTION_ID,
                "PROMO-000001",
                "September Discount",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                null,
                new BigDecimal("25.00"),
                "USD",
                new BigDecimal("2.50"),
                new BigDecimal("22.50"));
    }

    private static MembershipDetails membershipFrom(
            MembershipCreation creation) {

        MembershipPeriodDetails period =
                new MembershipPeriodDetails(
                        PERIOD_ID,
                        (short) 1,
                        MembershipPeriodSource.INITIAL,
                        creation.pricing(),
                        creation.dates().startsOn(),
                        creation.dates().baseEndsOn(),
                        creation.dates().effectiveEndsOn(),
                        NOW,
                        0);

        return new MembershipDetails(
                MEMBERSHIP_ID,
                "MEM-000001",
                creation.clientId(),
                MembershipStatus.ACTIVE,
                period,
                NOW,
                NOW,
                0);
    }
}
