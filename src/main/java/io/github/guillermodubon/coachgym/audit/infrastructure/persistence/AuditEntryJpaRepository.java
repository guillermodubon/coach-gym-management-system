package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.audit.application.AuditEntryStore;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import java.util.UUID;

import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface AuditEntryJpaRepository
        extends JpaRepository<AuditEntryJpaEntity, UUID> {
}

@Repository
class AuditEntryPersistenceAdapter
        implements AuditEntryStore {

    private final AuditEntryJpaRepository repository;

    AuditEntryPersistenceAdapter(
            AuditEntryJpaRepository repository) {

        this.repository = repository;
    }

    @Override
    public void recordClientRegistered(
            ClientRegistered event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    public void recordPlanChanged(
            PlanChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    public void recordPromotionChanged(
            PromotionChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    public void recordPromotionPlanEligibilityChanged(
            PromotionPlanEligibilityChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    public void recordMembershipCreated(
            MembershipCreated event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    public void recordMembershipRenewed(
            MembershipRenewed event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }
}
