package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.ActiveBranchContextManager;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextResolver;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchSummary;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.SelectActiveBranchCommand;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.github.guillermodubon.coachgym.user.StaffBranchContextPolicy;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Resolves and manages the authenticated user's server-side branch preference. */
@Service
public class ActiveBranchContextApplicationService
        implements ActiveBranchContextResolver, ActiveBranchContextManager,
        BranchOperationContextResolver {

    private static final Comparator<AuthorizedBranchSummary> BRANCH_ORDER =
            Comparator.comparing(AuthorizedBranchSummary::code)
                    .thenComparing(AuthorizedBranchSummary::id);

    private final StaffScopeQuery scopeQuery;
    private final AuthorizedBranchQuery authorizedBranchQuery;
    private final ActiveBranchSessionStore sessionStore;

    public ActiveBranchContextApplicationService(
            StaffScopeQuery scopeQuery,
            AuthorizedBranchQuery authorizedBranchQuery,
            ActiveBranchSessionStore sessionStore) {
        this.scopeQuery = Objects.requireNonNull(scopeQuery);
        this.authorizedBranchQuery = Objects.requireNonNull(authorizedBranchQuery);
        this.sessionStore = Objects.requireNonNull(sessionStore);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffBranchContext resolve(UUID userId) {
        StaffAuthorizationContext actor = requireActiveScope(userId);
        List<AuthorizedBranchSummary> availableBranches = availableBranches(userId);
        UUID organizationId = authorizedBranchQuery.findAuthorizedOrganizationId(userId)
                .orElseGet(() -> availableBranches.stream()
                        .map(AuthorizedBranchSummary::organizationId)
                        .findFirst()
                        .orElseThrow(ActiveBranchContextUnavailableException::new));

        UUID selectedBranchId = sessionStore.selectedBranchId()
                .orElseGet(() -> availableBranches.size() == 1
                        ? availableBranches.get(0).id()
                        : null);
        if (selectedBranchId != null
                && availableBranches.stream().noneMatch(branch -> branch.id().equals(selectedBranchId))) {
            sessionStore.clear();
            throw new ActiveBranchContextUnavailableException();
        }
        if (actor.scopeType() == io.github.guillermodubon.coachgym.user.StaffScopeType.BRANCH
                && availableBranches.isEmpty()) {
            sessionStore.clear();
            throw new ActiveBranchContextUnavailableException();
        }
        return new StaffBranchContext(
                organizationId,
                actor.scopeType(),
                selectedBranchId,
                availableBranches);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchOperationContext resolveOperation(UUID userId) {
        StaffAuthorizationContext actor = requireActiveScope(userId);
        StaffBranchContext context = resolve(userId);
        if (context.activeBranchId() != null
                && TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && !authorizedBranchQuery.lockAuthorizedActiveBranchForOperation(
                        userId, context.activeBranchId(), actor.scopeType())) {
            sessionStore.clear();
            throw new ActiveBranchContextUnavailableException();
        }
        Set<UUID> authorizedBranchIds = context.availableBranches().stream()
                .map(AuthorizedBranchSummary::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new BranchOperationContext(
                actor.userId(),
                context.organizationId(),
                actor.scopeType(),
                context.activeBranchId(),
                authorizedBranchIds);
    }

    @Override
    @Transactional
    public StaffBranchContext select(UUID userId, SelectActiveBranchCommand command) {
        Objects.requireNonNull(command, "Active branch command is required.");
        StaffAuthorizationContext actor = requireActiveScope(userId);
        List<AuthorizedBranchSummary> availableBranches = availableBranches(userId);
        Set<UUID> activeBranchIds = availableBranches.stream()
                .map(AuthorizedBranchSummary::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        StaffBranchContextPolicy.requireSelectionAllowed(actor, command, activeBranchIds);
        sessionStore.select(command.branchId());
        return resolve(userId);
    }

    @Override
    @Transactional
    public void clear(UUID userId) {
        requireActiveScope(userId);
        sessionStore.clear();
    }

    private StaffAuthorizationContext requireActiveScope(UUID userId) {
        Objects.requireNonNull(userId, "userId is required");
        StaffAuthorizationContext context = scopeQuery.findAuthorizationContext(userId)
                .orElseThrow(ActiveBranchContextUnavailableException::new);
        if (context.accountStatus() != StaffAccountStatus.ACTIVE) {
            sessionStore.clear();
            throw new ActiveBranchContextUnavailableException();
        }
        return context;
    }

    private List<AuthorizedBranchSummary> availableBranches(UUID userId) {
        return authorizedBranchQuery.findAuthorizedActiveBranches(userId).stream()
                .sorted(BRANCH_ORDER)
                .toList();
    }
}
