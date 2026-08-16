package io.github.guillermodubon.coachgym.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.PlanCatalogQuery;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.PromotionEligiblePlan;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PromotionEligibilityApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-15T20:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC);

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "c358e75e-0bba-4f43-8908-8fd33d37995f");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "71b44009-c301-404e-bf42-41f89519cfab");

    private static final UUID SECOND_PLAN_ID =
            UUID.fromString(
                    "ba2692b1-9a61-42f3-95fd-917566083ab1");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "2442f5fe-f8b4-4545-affe-3cd02466af55");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    @Mock
    private PromotionStore promotionStore;

    @Mock
    private PromotionEligibilityStore eligibilityStore;

    @Mock
    private PlanCatalogQuery planCatalogQuery;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PromotionEligibilityApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new PromotionEligibilityApplicationService(
                        promotionStore,
                        eligibilityStore,
                        planCatalogQuery,
                        eventPublisher,
                        CLOCK);
    }

    @Test
    void returnsEligiblePlansWithCurrentPromotionVersion() {
        PromotionDetails promotion =
                promotion(3);

        PlanDetails plan =
                plan(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        true);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(promotion));

        when(eligibilityStore.findEligiblePlanIds(
                PROMOTION_ID))
                .thenReturn(
                        Set.of(PLAN_ID));

        when(planCatalogQuery.findByIds(
                Set.of(PLAN_ID)))
                .thenReturn(
                        List.of(plan));

        PromotionEligibilityDetails result =
                service.findEligiblePlans(
                        PROMOTION_ID);

        assertThat(result.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(result.promotionVersion())
                .isEqualTo(3);

        assertThat(result.eligiblePlans())
                .containsExactly(
                        new PromotionEligiblePlan(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                true));

        verify(eligibilityStore)
                .findEligiblePlanIds(
                        PROMOTION_ID);

        verify(planCatalogQuery)
                .findByIds(
                        Set.of(PLAN_ID));
    }

    @Test
    void returnsEmptyEligibilityConfiguration() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(
                                promotion(0)));

        when(eligibilityStore.findEligiblePlanIds(
                PROMOTION_ID))
                .thenReturn(Set.of());

        PromotionEligibilityDetails result =
                service.findEligiblePlans(
                        PROMOTION_ID);

        assertThat(result.promotionVersion())
                .isZero();

        assertThat(result.eligiblePlans())
                .isEmpty();

        verify(planCatalogQuery, never())
                .findByIds(any());
    }

    @Test
    void replacesEligiblePlansAndPublishesEvent() {
        Set<UUID> planIds =
                Set.of(
                        PLAN_ID,
                        SECOND_PLAN_ID);

        PlanDetails monthlyPlan =
                plan(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        true);

        PlanDetails annualPlan =
                plan(
                        SECOND_PLAN_ID,
                        "PLAN-000002",
                        "Annual Access",
                        true);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(
                                promotion(2)));

        when(planCatalogQuery.findByIds(planIds))
                .thenReturn(
                        List.of(
                                monthlyPlan,
                                annualPlan));

        when(eligibilityStore.replaceEligiblePlanIds(
                PROMOTION_ID,
                planIds,
                2,
                ACTOR,
                NOW))
                .thenReturn(planIds);

        PromotionEligibilityDetails result =
                service.replaceEligiblePlans(
                        PROMOTION_ID,
                        new ReplaceEligiblePlansCommand(
                                planIds,
                                2),
                        ACTOR);

        assertThat(result.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(result.promotionVersion())
                .isEqualTo(3);

        assertThat(result.eligiblePlans())
                .extracting(
                        PromotionEligiblePlan::planName)
                .containsExactly(
                        "Annual Access",
                        "Monthly Access");

        verify(eligibilityStore)
                .replaceEligiblePlanIds(
                        PROMOTION_ID,
                        planIds,
                        2,
                        ACTOR,
                        NOW);

        ArgumentCaptor<PromotionPlanEligibilityChanged>
                eventCaptor =
                ArgumentCaptor.forClass(
                        PromotionPlanEligibilityChanged.class);

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture());

        PromotionPlanEligibilityChanged event =
                eventCaptor.getValue();

        assertThat(event.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(event.promotionCode())
                .isEqualTo("PROMO-000001");

        assertThat(event.eligiblePlanIds())
                .containsExactlyInAnyOrderElementsOf(
                        planIds);

        assertThat(event.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(event.actorIdentifier())
                .isEqualTo("coach-admin");

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void replacesEligibilityWithEmptySet() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(
                                promotion(1)));

        when(eligibilityStore.replaceEligiblePlanIds(
                PROMOTION_ID,
                Set.of(),
                1,
                ACTOR,
                NOW))
                .thenReturn(Set.of());

        PromotionEligibilityDetails result =
                service.replaceEligiblePlans(
                        PROMOTION_ID,
                        new ReplaceEligiblePlansCommand(
                                Set.of(),
                                1),
                        ACTOR);

        assertThat(result.promotionVersion())
                .isEqualTo(2);

        assertThat(result.eligiblePlans())
                .isEmpty();

        verify(planCatalogQuery, never())
                .findByIds(any());

        verify(eligibilityStore)
                .replaceEligiblePlanIds(
                        PROMOTION_ID,
                        Set.of(),
                        1,
                        ACTOR,
                        NOW);

        verify(eventPublisher)
                .publishEvent(
                        any(
                                PromotionPlanEligibilityChanged.class));
    }

    @Test
    void rejectsUnknownPlanBeforePersistence() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(
                                promotion(0)));

        when(planCatalogQuery.findByIds(
                Set.of(PLAN_ID)))
                .thenReturn(List.of());

        assertThatThrownBy(
                () ->
                        service.replaceEligiblePlans(
                                PROMOTION_ID,
                                new ReplaceEligiblePlansCommand(
                                        Set.of(
                                                PLAN_ID),
                                        0),
                                ACTOR))
                .isInstanceOf(
                        EligiblePlanNotFoundException.class)
                .hasMessage(
                        "Membership plan "
                                + PLAN_ID
                                + " was not found.");

        verify(eligibilityStore, never())
                .replaceEligiblePlanIds(
                        any(),
                        any(),
                        any(Long.class),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsInactivePlanBeforePersistence() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(
                                promotion(0)));

        when(planCatalogQuery.findByIds(
                Set.of(PLAN_ID)))
                .thenReturn(
                        List.of(
                                plan(
                                        PLAN_ID,
                                        "PLAN-000001",
                                        "Inactive Plan",
                                        false)));

        assertThatThrownBy(
                () ->
                        service.replaceEligiblePlans(
                                PROMOTION_ID,
                                new ReplaceEligiblePlansCommand(
                                        Set.of(
                                                PLAN_ID),
                                        0),
                                ACTOR))
                .isInstanceOf(
                        InactiveEligiblePlanException.class)
                .hasMessage(
                        "Membership plan "
                                + PLAN_ID
                                + " is inactive and cannot be assigned "
                                + "to a promotion.");

        verify(eligibilityStore, never())
                .replaceEligiblePlanIds(
                        any(),
                        any(),
                        any(Long.class),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsStalePromotionVersion() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(
                                promotion(4)));

        assertThatThrownBy(
                () ->
                        service.replaceEligiblePlans(
                                PROMOTION_ID,
                                new ReplaceEligiblePlansCommand(
                                        Set.of(),
                                        3),
                                ACTOR))
                .isInstanceOf(
                        PromotionVersionConflictException.class)
                .hasMessage(
                        "Promotion "
                                + PROMOTION_ID
                                + " was modified by another operation. "
                                + "Expected version 3 but found 4.");

        verify(planCatalogQuery, never())
                .findByIds(any());

        verify(eligibilityStore, never())
                .replaceEligiblePlanIds(
                        any(),
                        any(),
                        any(Long.class),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsUnknownPromotion() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () ->
                        service.findEligiblePlans(
                                PROMOTION_ID))
                .isInstanceOf(
                        PromotionNotFoundException.class)
                .hasMessage(
                        "Promotion "
                                + PROMOTION_ID
                                + " was not found.");

        verify(eligibilityStore, never())
                .findEligiblePlanIds(any());
    }

    @Test
    void rejectsMissingReplaceCommand() {
        assertThatThrownBy(
                () ->
                        service.replaceEligiblePlans(
                                PROMOTION_ID,
                                null,
                                ACTOR))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Replace eligible plans command "
                                + "must be provided.");

        verify(promotionStore, never())
                .findById(any());

        verify(eligibilityStore, never())
                .replaceEligiblePlanIds(
                        any(),
                        any(),
                        any(Long.class),
                        any(),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    private static PromotionDetails promotion(
            long version) {

        return new PromotionDetails(
                PROMOTION_ID,
                "PROMO-000001",
                "September Promotion",
                "Promotion test description.",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                true,
                NOW.minusSeconds(3_600),
                NOW,
                version);
    }

    private static PlanDetails plan(
            UUID id,
            String planCode,
            String name,
            boolean active) {

        return new PlanDetails(
                id,
                planCode,
                name,
                "Plan test description.",
                1,
                DurationUnit.MONTH,
                new BigDecimal("25.00"),
                "USD",
                active,
                NOW.minusSeconds(3_600),
                NOW,
                0);
    }
}
