package io.github.guillermodubon.coachgym.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChangeType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionValidationException;
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
class PromotionApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-15T04:00:00Z");

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
    void createsPromotionAndPublishesCreatedEvent() {
        CreatePromotionCommand command =
                validPercentageCommand();

        PromotionDetails persistedPromotion =
                percentagePromotionDetails();

        ArgumentCaptor<PromotionDefinition> definitionCaptor =
                ArgumentCaptor.forClass(PromotionDefinition.class);

        when(promotionStore.create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(ACTOR),
                org.mockito.ArgumentMatchers.eq(NOW)))
                .thenReturn(persistedPromotion);

        PromotionDetails result =
                service.create(command, ACTOR);

        assertThat(result).isEqualTo(persistedPromotion);

        verify(promotionStore).create(
                definitionCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(ACTOR),
                org.mockito.ArgumentMatchers.eq(NOW));

        PromotionDefinition capturedDefinition =
                definitionCaptor.getValue();

        assertThat(capturedDefinition.name())
                .isEqualTo("September Discount");

        assertThat(capturedDefinition.discountType())
                .isEqualTo(DiscountType.PERCENTAGE);

        assertThat(capturedDefinition.discountValue())
                .isEqualByComparingTo("20.00");

        assertThat(capturedDefinition.currency()).isNull();

        ArgumentCaptor<PromotionChanged> eventCaptor =
                ArgumentCaptor.forClass(PromotionChanged.class);

        verify(eventPublisher)
                .publishEvent(eventCaptor.capture());

        PromotionChanged event = eventCaptor.getValue();

        assertThat(event.promotionId()).isEqualTo(PROMOTION_ID);
        assertThat(event.promotionCode())
                .isEqualTo("PROMO-000001");
        assertThat(event.changeType())
                .isEqualTo(PromotionChangeType.CREATED);
        assertThat(event.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.actorIdentifier())
                .isEqualTo("coach-admin");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidPromotionBeforeUsingPersistence() {
        CreatePromotionCommand command =
                new CreatePromotionCommand(
                        "Invalid Promotion",
                        null,
                        DiscountType.PERCENTAGE,
                        new BigDecimal("101.00"),
                        null,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> service.create(command, ACTOR))
                .isInstanceOf(PromotionValidationException.class)
                .hasMessage(
                        "Percentage discount must not exceed 100.");

        verify(promotionStore, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        verify(eventPublisher, never())
                .publishEvent(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsPromotionById() {
        PromotionDetails promotion =
                percentagePromotionDetails();

        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(Optional.of(promotion));

        PromotionDetails result =
                service.findById(PROMOTION_ID);

        assertThat(result).isEqualTo(promotion);

        verify(promotionStore).findById(PROMOTION_ID);
    }

    @Test
    void rejectsUnknownPromotionId() {
        when(promotionStore.findById(PROMOTION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.findById(PROMOTION_ID))
                .isInstanceOf(PromotionNotFoundException.class)
                .hasMessage(
                        "Promotion "
                                + PROMOTION_ID
                                + " was not found.");
    }

    @Test
    void rejectsMissingCreateCommand() {
        assertThatThrownBy(() ->
                service.create(null, ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Create promotion command must be provided.");

        verify(promotionStore, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        verify(eventPublisher, never())
                .publishEvent(
                        org.mockito.ArgumentMatchers.any());
    }

    private static CreatePromotionCommand
    validPercentageCommand() {

        return new CreatePromotionCommand(
                " September Discount ",
                " Twenty percent off selected plans. ",
                DiscountType.PERCENTAGE,
                new BigDecimal("20"),
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30));
    }

    private static PromotionDetails
    percentagePromotionDetails() {

        return new PromotionDetails(
                PROMOTION_ID,
                "PROMO-000001",
                "September Discount",
                "Twenty percent off selected plans.",
                DiscountType.PERCENTAGE,
                new BigDecimal("20.00"),
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                true,
                NOW,
                NOW,
                0);
    }

    @Test
    void returnsPaginatedPromotions() {
        PromotionSearchQuery query =
                PromotionSearchQuery.from(
                        true,
                        "September",
                        DiscountType.PERCENTAGE,
                        LocalDate.of(2026, 9, 15),
                        0,
                        25,
                        "name",
                        "asc");

        PromotionPage expectedPage =
                new PromotionPage(
                        java.util.List.of(
                                percentagePromotionDetails()),
                        0,
                        25,
                        1,
                        1);

        when(promotionStore.findAll(query))
                .thenReturn(expectedPage);

        PromotionPage result =
                service.findAll(query);

        assertThat(result).isEqualTo(expectedPage);

        verify(promotionStore).findAll(query);
    }
}
