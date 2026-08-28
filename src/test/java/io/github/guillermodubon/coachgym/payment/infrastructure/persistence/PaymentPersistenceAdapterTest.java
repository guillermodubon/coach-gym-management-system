package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentPage;
import io.github.guillermodubon.coachgym.payment.application.PaymentSortDirection;
import io.github.guillermodubon.coachgym.payment.application.PaymentSortField;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.github.guillermodubon.coachgym.payment.application.PaymentSearchQuery;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;


@ExtendWith(MockitoExtension.class)
class PaymentPersistenceAdapterTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final String CURRENCY = "USD";

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    @Mock
    private PaymentJpaRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryJpaRepository historyRepository;

    @Mock
    private EntityManager entityManager;

    private PaymentPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PaymentPersistenceAdapter(
                paymentRepository, historyRepository, entityManager);
    }

    @Test
    void registersPaymentAndReturnsDetails() {
        PaymentJpaEntity entity = PaymentJpaEntity.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);

        given(paymentRepository.saveAndFlush(any()))
                .willReturn(entity);
        // entityManager.refresh is void — no stubbing needed

        PaymentDetails result = adapter.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);

        assertThat(result.clientId()).isEqualTo(CLIENT_ID);
        assertThat(result.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(result.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(result.amount()).isEqualByComparingTo("25.00");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.externalReference()).isNull();
        assertThat(result.paidAt()).isEqualTo(PAID_AT);
        assertThat(result.registeredByUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void refreshesEntityAfterSaveToRetrieveGeneratedPaymentCode() {
        PaymentJpaEntity entity = PaymentJpaEntity.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);

        given(paymentRepository.saveAndFlush(any()))
                .willReturn(entity);

        adapter.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);

        // entityManager.refresh must be called with the persisted entity
        // so that the DB-generated payment_code is populated.
        verify(entityManager).refresh(entity);
    }

    @Test
    void savesInitialStatusHistoryAfterRefresh() {
        PaymentJpaEntity entity = PaymentJpaEntity.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);

        given(paymentRepository.saveAndFlush(any()))
                .willReturn(entity);

        adapter.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);

        ArgumentCaptor<PaymentStatusHistoryJpaEntity> captor =
                ArgumentCaptor.forClass(PaymentStatusHistoryJpaEntity.class);
        verify(historyRepository).saveAndFlush(captor.capture());

        PaymentStatusHistoryJpaEntity history = captor.getValue();
        assertThat(history.paymentId()).isEqualTo(entity.id());
        assertThat(history.previousStatus()).isNull();
        assertThat(history.newStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(history.reason()).isEqualTo("Payment registered.");
        assertThat(history.changedByUserId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void setsStatusToPaid() {
        PaymentJpaEntity entity = PaymentJpaEntity.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CARD, "REF-001", PAID_AT,
                ACTOR, NOW);

        given(paymentRepository.saveAndFlush(any()))
                .willReturn(entity);

        PaymentDetails result = adapter.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CARD, "REF-001", PAID_AT,
                ACTOR, NOW);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.externalReference()).isEqualTo("REF-001");
    }


    @Test
    void returnsPaymentDetailsWhenFound() {
        PaymentJpaEntity entity = PaymentJpaEntity.register(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT,
                ACTOR, NOW);
        UUID id = entity.id();

        given(paymentRepository.findById(id))
                .willReturn(Optional.of(entity));

        Optional<PaymentDetails> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void returnsEmptyWhenPaymentNotFound() {
        UUID unknownId = UUID.randomUUID();
        given(paymentRepository.findById(unknownId))
                .willReturn(Optional.empty());

        Optional<PaymentDetails> result = adapter.findById(unknownId);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsTrueWhenDuplicateReferenceExists() {
        given(paymentRepository.existsByPaymentMethodAndExternalReference(
                PaymentMethod.CARD, "REF-001"))
                .willReturn(true);

        boolean result = adapter.existsByMethodAndExternalReference(
                PaymentMethod.CARD, "REF-001");

        assertThat(result).isTrue();
    }

    @Test
    void returnsFalseWhenNoMatchingReference() {
        given(paymentRepository.existsByPaymentMethodAndExternalReference(
                PaymentMethod.CASH, "REF-NEW"))
                .willReturn(false);

        boolean result = adapter.existsByMethodAndExternalReference(
                PaymentMethod.CASH, "REF-NEW");

        assertThat(result).isFalse();
    }

    @Test
    void queriesPaymentsUsingDynamicSpecification() {
        PaymentJpaEntity entity = PaymentJpaEntity.register(
                CLIENT_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                AMOUNT,
                CURRENCY,
                PaymentMethod.CASH,
                null,
                PAID_AT,
                ACTOR,
                NOW);

        PaymentSearchQuery query = new PaymentSearchQuery(
                CLIENT_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                10,
                PaymentSortField.PAID_AT,
                PaymentSortDirection.DESC);

        given(paymentRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<PaymentJpaEntity>>any(),
                org.mockito.ArgumentMatchers
                        .<Pageable>any()))
                .willReturn(new PageImpl<>(
                        List.of(entity),
                        PageRequest.of(0, 10),
                        1));

        PaymentPage result = adapter.findAll(query);

        assertThat(result.items()).hasSize(1);

        PaymentDetails payment = result.items().getFirst();

        assertThat(payment.clientId()).isEqualTo(CLIENT_ID);
        assertThat(payment.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(payment.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(payment.amount()).isEqualByComparingTo("25.00");
        assertThat(payment.currency()).isEqualTo(CURRENCY);
        assertThat(payment.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);

        verify(paymentRepository).findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<PaymentJpaEntity>>any(),
                org.mockito.ArgumentMatchers
                        .<Pageable>any());
    }

}
