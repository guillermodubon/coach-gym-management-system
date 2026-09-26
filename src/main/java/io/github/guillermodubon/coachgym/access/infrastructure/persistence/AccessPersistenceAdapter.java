package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifierType;
import io.github.guillermodubon.coachgym.access.application.AccessRecordPage;
import io.github.guillermodubon.coachgym.access.application.AccessRecordSearchQuery;
import io.github.guillermodubon.coachgym.access.application.AccessRecordStore;
import io.github.guillermodubon.coachgym.access.application.AccessRecordDataAccessException;
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
import org.springframework.dao.DataAccessException;
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
            UUID actorId,
            UUID branchId) {

        return accessRecordRepository
                .saveAndFlush(AccessRecordJpaEntity.create(
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
                        actorId,
                        branchId))
                .toDetails();
    }

    @Override
    @Transactional
    public AccessRecordDetails persistQr(
            String safeIdentifier,
            UUID credentialId,
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

        try {
            return accessRecordRepository
                    .saveAndFlush(AccessRecordJpaEntity.createQr(
                            safeIdentifier,
                            credentialId,
                            clientId,
                            clientCode,
                            membershipId,
                            membershipCode,
                            membershipPeriodId,
                            result,
                            reasonCode,
                            reason,
                            occurredAt,
                            actorId))
                    .toDetails();
        } catch (DataAccessException exception) {
            throw new AccessRecordDataAccessException(
                    "QR access attempt could not be persisted.", exception);
        }
    }

    @Override
    @Transactional
    public AccessRecordDetails persistQr(
            String safeIdentifier,
            UUID credentialId,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID membershipPeriodId,
            AccessResult result,
            AccessReasonCode reasonCode,
            String reason,
            Instant occurredAt,
            UUID actorId,
            UUID branchId) {

        try {
            return accessRecordRepository
                    .saveAndFlush(AccessRecordJpaEntity.createQr(
                            safeIdentifier,
                            credentialId,
                            clientId,
                            clientCode,
                            membershipId,
                            membershipCode,
                            membershipPeriodId,
                            result,
                            reasonCode,
                            reason,
                            occurredAt,
                            actorId,
                            branchId))
                    .toDetails();
        } catch (DataAccessException exception) {
            throw new AccessRecordDataAccessException(
                    "QR access attempt could not be persisted.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessRecordDetails> findMostRecentAllowedQrAttempt(
            UUID credentialId,
            Instant occurredAtFromInclusive) {
        if (credentialId == null || occurredAtFromInclusive == null) {
            throw new IllegalArgumentException(
                    "Credential and duplicate boundary are required.");
        }
        try {
            return accessRecordRepository
                    .findFirstByAccessCredentialIdAndIdentificationSourceAndResultAndCheckedInAtGreaterThanEqualOrderByCheckedInAtDescIdAsc(
                            credentialId,
                            AccessIdentifierType.QR_CREDENTIAL,
                            AccessResult.ALLOWED,
                            occurredAtFromInclusive)
                    .map(AccessRecordJpaEntity::toDetails);
        } catch (DataAccessException exception) {
            throw new AccessRecordDataAccessException(
                    "Recent QR access attempts could not be read.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessRecordDetails> findMostRecentAllowedQrAttempt(
            UUID credentialId,
            Instant occurredAtFromInclusive,
            UUID branchId) {
        if (credentialId == null || occurredAtFromInclusive == null || branchId == null) {
            throw new IllegalArgumentException(
                    "Credential, branch and duplicate boundary are required.");
        }
        try {
            return accessRecordRepository
                    .findFirstByAccessCredentialIdAndBranchIdAndIdentificationSourceAndResultAndCheckedInAtGreaterThanEqualOrderByCheckedInAtDescIdAsc(
                            credentialId,
                            branchId,
                            AccessIdentifierType.QR_CREDENTIAL,
                            AccessResult.ALLOWED,
                            occurredAtFromInclusive)
                    .map(AccessRecordJpaEntity::toDetails);
        } catch (DataAccessException exception) {
            throw new AccessRecordDataAccessException(
                    "Recent QR access attempts could not be read.", exception);
        }
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
    public Optional<AccessRecordDetails> findById(UUID id, UUID branchId) {
        if (id == null || branchId == null) {
            throw new IllegalArgumentException("Access record and branch are required.");
        }
        return accessRecordRepository
                .findByIdAndBranchId(id, branchId)
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

    @Override
    @Transactional(readOnly = true)
    public AccessRecordPage findAll(
            AccessRecordSearchQuery query,
            UUID branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("Access record branch is required.");
        }
        Page<AccessRecordJpaEntity> page = accessRecordRepository.findAll(
                toSpecification(query, branchId),
                PageRequest.of(query.page(), query.size(), toSort(query)));
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

        return toSpecification(query, null);
    }

    private static Specification<AccessRecordJpaEntity> toSpecification(
            AccessRecordSearchQuery query,
            UUID branchId) {

        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (branchId != null) {
                predicates.add(criteriaBuilder.equal(root.get("branchId"), branchId));
            }

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
