package io.github.guillermodubon.coachgym.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChangeType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
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
class PromotionLifecycleApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-15T19:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(NOW, ZoneOffset.UTC);

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "32f9291d-2263-4099-aac4-a51958ce82c0");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "2d903ea1-b5e6-4506-b72e-7ab905ec5fa6");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    @Mock
    private PromotionStore promotionStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PromotionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PromotionApplicationService(
                promotionStore,
                eventPublisher,
                CLOCK);
    }

    @Test
    void updatesPromotionAndPublishesUpdatedEvent() {
        UpdatePromotionCommand command =
                validUpdateCommand(0);

        PromotionDetails updatedPromotion =
                promotion(
                        "Updated September Promotion",
                        DiscountType.FIXED_AMOUNT,
                        "5.00",
                        "USD",
                        true,
                        1);

        when(promotionStore.update(
                eq(PROMOTION_ID),
                any(PromotionDefinition.class),
                eq(0L),
                eq(ACTOR),
                eq(NOW)))
                .thenReturn(updatedPromotion);

        PromotionDetails result =
                service.update(
                        PROMOTION_ID,
                        command,
                        ACTOR);

        assertThat(result)
                .isEqualTo(updatedPromotion);

        ArgumentCaptor<PromotionDefinition>
                definitionCaptor =
                ArgumentCaptor.forClass(
                        PromotionDefinition.class);

        verify(promotionStore).update(
                eq(PROMOTION_ID),
                definitionCaptor.capture(),
                eq(0L),
                eq(ACTOR),
                eq(NOW));

        PromotionDefinition definition =
                definitionCaptor.getValue();

        assertThat(definition.name())
                .isEqualTo(
                        "Updated September Promotion");

        assertThat(definition.description())
                .isEqualTo(
                        "Five dollars off selected plans.");

        assertThat(definition.discountType())
                .isEqualTo(
                        DiscountType.FIXED_AMOUNT);

        assertThat(definition.discountValue())
                .isEqualByComparingTo("5.00");

        assertThat(definition.currency())
                .isEqualTo("USD");

        assertThat(definition.validFrom())
                .isEqualTo(
                        LocalDate.of(2026, 9, 1));

        assertThat(definition.validUntil())
                .isEqualTo(
                        LocalDate.of(2026, 10, 31));

        assertPublishedEvent(
                PromotionChangeType.UPDATED);
    }

    @Test
    void rejectsInvalidUpdateBeforePersistence() {
        UpdatePromotionCommand command =
                new UpdatePromotionCommand(
                        "Invalid Percentage",
                        null,
                        DiscountType.PERCENTAGE,
                        new BigDecimal("101.00"),
                        null,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30),
                        0);

        assertThatThrownBy(
                () -> service.update(
                        PROMOTION_ID,
                        command,
                        ACTOR))
                .isInstanceOf(
                        io.github.guillermodubon.coachgym
                                .promotion.domain
                                .PromotionValidationException.class)
                .hasMessage(
                        "Percentage discount must not exceed 100.");

        verify(promotionStore, never()).update(
                any(),
                any(),
                anyLong(),
                any(),
                any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsMissingUpdateCommand() {
        assertThatThrownBy(
                () -> service.update(
                        PROMOTION_ID,
                        null,
                        ACTOR))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Update promotion command must be provided.");

        verify(promotionStore, never()).update(
                any(),
                any(),
                anyLong(),
                any(),
                any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsUpdateWithNegativeVersion() {
        UpdatePromotionCommand command =
                validUpdateCommand(-1);

        assertThatThrownBy(
                () -> service.update(
                        PROMOTION_ID,
                        command,
                        ACTOR))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Promotion version must not be negative.");

        verify(promotionStore, never()).update(
                any(),
                any(),
                anyLong(),
                any(),
                any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void deactivatesActivePromotionAndPublishesEvent() {
        PromotionDetails activePromotion =
                promotion(
                        "September Promotion",
                        DiscountType.PERCENTAGE,
                        "20.00",
                        null,
                        true,
                        2);

        PromotionDetails inactivePromotion =
                promotion(
                        "September Promotion",
                        DiscountType.PERCENTAGE,
                        "20.00",
                        null,
                        false,
                        3);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(                        Optional.of(activePromotion));

        when(promotionStore.changeActive(
                PROMOTION_ID,
                false,
                2,
                ACTOR,
                NOW))
                .thenReturn(inactivePromotion);

        PromotionDetails result =
                service.deactivate(
                        PROMOTION_ID,
                        2,
                        ACTOR);

        assertThat(result.active())
                .isFalse();

        assertThat(result.version())
                .isEqualTo(3);

        verify(promotionStore).findById(
                PROMOTION_ID);

        verify(promotionStore).changeActive(
                PROMOTION_ID,
                false,
                2,
                ACTOR,
                NOW);

        assertPublishedEvent(
                PromotionChangeType.DEACTIVATED);
    }

    @Test
    void reactivatesInactivePromotionAndPublishesEvent() {
        PromotionDetails inactivePromotion =
                promotion(
                        "September Promotion",
                        DiscountType.PERCENTAGE,
                        "20.00",
                        null,
                        false,
                        3);

        PromotionDetails activePromotion =
                promotion(
                        "September Promotion",
                        DiscountType.PERCENTAGE,
                        "20.00",
                        null,
                        true,
                        4);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(inactivePromotion));

        when(promotionStore.changeActive(
                PROMOTION_ID,
                true,
                3,
                ACTOR,
                NOW))
                .thenReturn(activePromotion);

        PromotionDetails result =
                service.activate(
                        PROMOTION_ID,
                        3,
                        ACTOR);

        assertThat(result.active())
                .isTrue();

        assertThat(result.version())
                .isEqualTo(4);

        verify(promotionStore).findById(
                PROMOTION_ID);

        verify(promotionStore).changeActive(
                PROMOTION_ID,
                true,
                3,
                ACTOR,
                NOW);

        assertPublishedEvent(
                PromotionChangeType.REACTIVATED);
    }

    @Test
    void rejectsDeactivatingAlreadyInactivePromotion() {
        PromotionDetails inactivePromotion =
                promotion(
                        "Inactive Promotion",
                        DiscountType.PERCENTAGE,
                        "10.00",
                        null,
                        false,
                        1);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(inactivePromotion));

        assertThatThrownBy(
                () -> service.deactivate(
                        PROMOTION_ID,
                        1,
                        ACTOR))
                .isInstanceOf(
                        PromotionStateConflictException.class)
                .hasMessage(
                        "Promotion is already inactive.");

        verify(promotionStore).findById(
                PROMOTION_ID);

        verify(promotionStore, never())
                .changeActive(
                        any(UUID.class),
                        anyBoolean(),
                        anyLong(),
                        any(AuthenticatedActor.class),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsActivatingAlreadyActivePromotion() {
        PromotionDetails activePromotion =
                promotion(
                        "Active Promotion",
                        DiscountType.PERCENTAGE,
                        "10.00",
                        null,
                        true,
                        1);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(activePromotion));

        assertThatThrownBy(
                () -> service.activate(
                        PROMOTION_ID,
                        1,
                        ACTOR))
                .isInstanceOf(
                        PromotionStateConflictException.class)
                .hasMessage(
                        "Promotion is already active.");

        verify(promotionStore).findById(
                PROMOTION_ID);

        verify(promotionStore, never())
                .changeActive(
                        any(UUID.class),
                        anyBoolean(),
                        anyLong(),
                        any(AuthenticatedActor.class),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsStateChangeWithStaleVersion() {
        PromotionDetails currentPromotion =
                promotion(
                        "Current Promotion",
                        DiscountType.PERCENTAGE,
                        "10.00",
                        null,
                        true,
                        5);

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(
                        Optional.of(currentPromotion));

        assertThatThrownBy(
                () -> service.deactivate(
                        PROMOTION_ID,
                        4,
                        ACTOR))
                .isInstanceOf(
                        PromotionVersionConflictException.class)
                .hasMessage(
                        "Promotion "
                                + PROMOTION_ID
                                + " was modified by another operation. "
                                + "Expected version 4 but found 5.");

        verify(promotionStore).findById(
                PROMOTION_ID);

        verify(promotionStore, never())
                .changeActive(
                        any(UUID.class),
                        anyBoolean(),
                        anyLong(),
                        any(AuthenticatedActor.class),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsStateChangeForUnknownPromotion() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.deactivate(
                        PROMOTION_ID,
                        0,
                        ACTOR))
                .isInstanceOf(
                        PromotionNotFoundException.class)
                .hasMessage(
                        "Promotion "
                                + PROMOTION_ID
                                + " was not found.");

        verify(promotionStore).findById(
                PROMOTION_ID);

        verify(promotionStore, never())
                .changeActive(
                        any(UUID.class),
                        anyBoolean(),
                        anyLong(),
                        any(AuthenticatedActor.class),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void rejectsStateChangeWithNegativeVersion() {
        assertThatThrownBy(
                () -> service.deactivate(
                        PROMOTION_ID,
                        -1,
                        ACTOR))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Promotion version must not be negative.");

        verify(promotionStore, never())
                .findById(any(UUID.class));

        verify(promotionStore, never())
                .changeActive(
                        any(UUID.class),
                        anyBoolean(),
                        anyLong(),
                        any(AuthenticatedActor.class),
                        any(Instant.class));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    private void assertPublishedEvent(
            PromotionChangeType expectedChangeType) {

        ArgumentCaptor<PromotionChanged> eventCaptor =
                ArgumentCaptor.forClass(
                        PromotionChanged.class);

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture());

        PromotionChanged event =
                eventCaptor.getValue();

        assertThat(event.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(event.promotionCode())
                .isEqualTo("PROMO-000001");

        assertThat(event.changeType())
                .isEqualTo(expectedChangeType);

        assertThat(event.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(event.actorIdentifier())
                .isEqualTo("coach-admin");

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    private static UpdatePromotionCommand
    validUpdateCommand(long version) {

        return new UpdatePromotionCommand(
                " Updated September Promotion ",
                " Five dollars off selected plans. ",
                DiscountType.FIXED_AMOUNT,
                new BigDecimal("5"),
                " usd ",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                version);
    }

    private static PromotionDetails promotion(
            String name,
            DiscountType discountType,
            String discountValue,
            String currency,
            boolean active,
            long version) {

        return new PromotionDetails(
                PROMOTION_ID,
                "PROMO-000001",
                name,
                "Promotion test description.",
                discountType,
                new BigDecimal(discountValue),
                currency,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 31),
                active,
                NOW.minusSeconds(3_600),
                NOW,
                version);
    }
}

