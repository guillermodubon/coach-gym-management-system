package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.audit.application.AuditEntryStore;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.membership.MembershipCancelled;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipFrozen;
import io.github.guillermodubon.coachgym.membership.MembershipReactivated;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

interface AuditEntryJpaRepository
        extends JpaRepository<
        AuditEntryJpaEntity,
        UUID> {
}

@Repository
class AuditEntryPersistenceAdapter
        implements AuditEntryStore {

    private final AuditEntryJpaRepository repository;

    AuditEntryPersistenceAdapter(
            AuditEntryJpaRepository repository) {

        this.repository =
                repository;
    }

    @Override
    @Transactional
    public void recordClientRegistered(
            ClientRegistered event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPlanChanged(            PlanChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPromotionChanged(
            PromotionChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPromotionPlanEligibilityChanged(
            PromotionPlanEligibilityChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipCreated(
            MembershipCreated event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipRenewed(
            MembershipRenewed event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipFrozen(
            MembershipFrozen event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipReactivated(
            MembershipReactivated event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipCancelled(
            MembershipCancelled event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }
}