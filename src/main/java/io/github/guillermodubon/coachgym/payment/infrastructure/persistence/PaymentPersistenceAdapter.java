package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.application.PaymentPage;
import io.github.guillermodubon.coachgym.payment.application.PaymentSearchQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentSortDirection;
import io.github.guillermodubon.coachgym.payment.application.PaymentStore;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;


@Repository
class PaymentPersistenceAdapter
        implements PaymentStore {

    private final PaymentJpaRepository paymentRepository;
    private final PaymentStatusHistoryJpaRepository historyRepository;
    private final EntityManager entityManager;

    PaymentPersistenceAdapter(
            PaymentJpaRepository paymentRepository,
            PaymentStatusHistoryJpaRepository historyRepository,
            EntityManager entityManager) {

        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public PaymentDetails register(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String externalReference,
            Instant paidAt,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PaymentJpaEntity payment =
                paymentRepository.saveAndFlush(
                        PaymentJpaEntity.register(
                                clientId,
                                membershipId,
                                membershipPeriodId,
                                amount,
                                currency,
                                paymentMethod,
                                externalReference,
                                paidAt,
                                actor,
                                occurredAt));

        // Refresh to retrieve DB-generated payment_number and payment_code.
        entityManager.refresh(payment);

        historyRepository.saveAndFlush(
                PaymentStatusHistoryJpaEntity.initialRegistration(
                        payment.id(),
                        actor,
                        occurredAt));

        return payment.toDetails();
    }

    private static Sort toSort(PaymentSearchQuery query) {
        String primary = switch (query.sortField()) {
            case PAID_AT -> "paidAt";
            case AMOUNT -> "amount";
            case CREATED_AT -> "createdAt";
            case UPDATED_AT -> "updatedAt";
        };
        Sort.Direction dir = query.direction() == PaymentSortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        // Stable secondary sort by id ASC.
        return Sort.by(dir, primary).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentDetails> findById(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .map(PaymentJpaEntity::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentPage findAll(PaymentSearchQuery query) {
        Page<PaymentJpaEntity> page = paymentRepository.findAll(
                toSpecification(query),
                PageRequest.of(
                        query.page(),
                        query.size(),
                        toSort(query)));

        return new PaymentPage(
                page.getContent().stream()
                        .map(PaymentJpaEntity::toDetails)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByMethodAndExternalReference(
            PaymentMethod paymentMethod,
            String externalReference) {

        return paymentRepository
                .existsByPaymentMethodAndExternalReference(
                        paymentMethod,
                        externalReference);
    }

    private static Specification<PaymentJpaEntity> toSpecification(
            PaymentSearchQuery query) {

        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.clientId() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("clientId"),
                                query.clientId()));
            }

            if (query.membershipId() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("membershipId"),
                                query.membershipId()));
            }

            if (query.membershipPeriodId() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("membershipPeriodId"),
                                query.membershipPeriodId()));
            }

            if (query.status() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.<PaymentStatus>get("status"),
                                query.status()));
            }

            if (query.paymentMethod() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.<PaymentMethod>get("paymentMethod"),
                                query.paymentMethod()));
            }

            if (query.paidFrom() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("paidAt"),
                                query.paidFrom()));
            }

            if (query.paidUntil() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("paidAt"),
                                query.paidUntil()));
            }

            return criteriaBuilder.and(
                    predicates.toArray(Predicate[]::new));
        };
    }
}
