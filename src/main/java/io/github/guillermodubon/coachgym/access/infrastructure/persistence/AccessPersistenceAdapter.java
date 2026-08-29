package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.application.AccessRecordPage;
import io.github.guillermodubon.coachgym.access.application.AccessRecordSearchQuery;
import io.github.guillermodubon.coachgym.access.application.AccessRecordStore;
import io.github.guillermodubon.coachgym.access.application.AccessSortDirection;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AccessPersistenceAdapter implements AccessRecordStore {

    private final AccessRecordJpaRepository accessRecordRepository;

    AccessPersistenceAdapter(
            AccessRecordJpaRepository accessRecordRepository) {
        this.accessRecordRepository = accessRecordRepository;
    }

    @Override
    @Transactional
    public AccessRecordDetails persist(
            String presentedIdentifier,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID membershipPeriodId,
            AccessResult result,
            AccessReasonCode reasonCode,
            String reason,
            Instant occurredAt,
            UUID actorId) {

        AccessRecordJpaEntity entity =
                AccessRecordJpaEntity.create(
                        presentedIdentifier,
                        clientId,
                        clientCode,
                        membershipId,
                        membershipCode,
                        membershipPeriodId,
                        result,
                        reasonCode,
                        reason,
                        occurredAt,
                        actorId);

        return accessRecordRepository
                .saveAndFlush(entity)
                .toDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessRecordDetails> findById(UUID id) {
        return accessRecordRepository
                .findById(id)
                .map(AccessRecordJpaEntity::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public AccessRecordPage findAll(
            AccessRecordSearchQuery query) {

        Page<AccessRecordJpaEntity> page =
                accessRecordRepository.findAll(
                        toSpecification(query),
                        PageRequest.of(
                                query.page(),
                                query.size(),
                                toSort(query)));

        return new AccessRecordPage(
                page.getContent().stream()
                        .map(AccessRecordJpaEntity::toDetails)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private static Specification<AccessRecordJpaEntity> toSpecification(
            AccessRecordSearchQuery query) {

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

            if (query.result() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.<AccessResult>get("result"),
                                query.result()));
            }

            if (query.reasonCode() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.<AccessReasonCode>get("reasonCode"),
                                query.reasonCode()));
            }

            if (query.checkedInFrom() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.<Instant>get("checkedInAt"),
                                query.checkedInFrom()));
            }

            if (query.checkedInUntil() != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.<Instant>get("checkedInAt"),
                                query.checkedInUntil()));
            }

            if (query.processedByUserId() != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("processedByUserId"),
                                query.processedByUserId()));
            }

            return criteriaBuilder.and(
                    predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort toSort(
            AccessRecordSearchQuery query) {

        String primary = switch (query.sortField()) {
            case CHECKED_IN_AT -> "checkedInAt";
        };

        Sort.Direction direction =
                query.direction() == AccessSortDirection.ASC
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        return Sort.by(direction, primary)
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
