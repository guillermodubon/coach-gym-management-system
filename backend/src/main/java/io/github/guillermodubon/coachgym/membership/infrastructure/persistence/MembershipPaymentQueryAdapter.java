package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipPaymentDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentQuery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class MembershipPaymentQueryAdapter
        implements MembershipPaymentQuery {

    private final MembershipJpaRepository membershipRepository;
    private final MembershipPeriodJpaRepository periodRepository;

    MembershipPaymentQueryAdapter(
            MembershipJpaRepository membershipRepository,
            MembershipPeriodJpaRepository periodRepository) {

        this.membershipRepository = membershipRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipPaymentDetails> findMembershipForPayment(
            UUID membershipId) {

        return membershipRepository
                .findById(membershipId)
                .map(entity ->
                        new MembershipPaymentDetails(
                                entity.id(),
                                entity.clientId(),
                                entity.status()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipPaymentPeriodDetails> findPeriodForPayment(
            UUID periodId) {

        return periodRepository
                .findById(periodId)
                .map(entity ->
                        new MembershipPaymentPeriodDetails(
                                entity.id(),
                                entity.membershipId(),
                                entity.finalPrice(),
                                entity.currency()));
    }
}
