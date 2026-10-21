package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, active-branch-scoped payment-attempt recovery use case. */
@Service
public class PaymentAttemptQueryApplicationService {

    private final BranchOperationContextResolver branchContextResolver;
    private final PaymentAttemptReadQuery paymentAttemptReadQuery;

    public PaymentAttemptQueryApplicationService(
            BranchOperationContextResolver branchContextResolver,
            PaymentAttemptReadQuery paymentAttemptReadQuery) {
        this.branchContextResolver = Objects.requireNonNull(branchContextResolver);
        this.paymentAttemptReadQuery = Objects.requireNonNull(paymentAttemptReadQuery);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentAttemptPage findPage(
            PaymentAttemptSearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Payment-attempt search query is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        if (actor.id() == null) {
            throw new IllegalArgumentException("Authenticated actor ID is required.");
        }
        var context = branchContextResolver.resolveOperation(actor.id());
        var branchId = BranchResourceAuthorizationPolicy.requireActiveBranch(context);
        return paymentAttemptReadQuery.findPage(query, branchId);
    }
}
