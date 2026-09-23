package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessCheckInContext;
import io.github.guillermodubon.coachgym.access.domain.AccessEvaluation;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifier;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifierType;
import io.github.guillermodubon.coachgym.access.domain.AccessPaymentPolicyEvaluator;
import io.github.guillermodubon.coachgym.access.domain.AccessPolicy;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import io.github.guillermodubon.coachgym.access.domain.DuplicateScanPolicy;
import io.github.guillermodubon.coachgym.access.domain.DuplicateScanResult;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialResolver;
import io.github.guillermodubon.coachgym.accesscredential.ResolvedAccessCredential;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.payment.ConfirmedPaymentForAccessQuery;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the access check-in use case and supporting queries.
 *
 * <h2>Check-in sequencing</h2>
 * <ol>
 *   <li>Capture one {@code Instant} from the injected {@code Clock}.</li>
 *   <li>Derive the operational {@code LocalDate} from the same clock
 *       (uses the configured gym time zone, never bare UTC).</li>
 *   <li>Normalise the identifier via {@link AccessIdentifier#of(String)}.</li>
 *   <li>Resolve the client and membership through public module boundaries.</li>
 *   <li>Perform the ownership cross-check; throw {@link IllegalStateException}
 *       on mismatch (no record is written).</li>
 *   <li>Evaluate the deterministic policy.</li>
 *   <li>Persist the record ({@code saveAndFlush}).</li>
 *   <li>Publish {@link AccessAttemptRecorded} after durable persistence.</li>
 *   <li>Return the persisted projection.</li>
 * </ol>
 *
 * <p>The audit listener (Block 6) fires synchronously inside the same
 * transaction via {@code @EventListener}; it handles denied-only audit
 * entries. The service does not call the audit store directly.</p>
 */
@Service
public class AccessApplicationService {

    private static final AccessPaymentPolicyQuery DISABLED_POLICY_QUERY =
            () -> new AccessPaymentPolicyDetails(false, 0);

    private static final ConfirmedPaymentForAccessQuery NO_PAYMENT_QUERY =
            (clientId, membershipId, membershipPeriodId) -> false;

    private final AccessRecordStore accessRecordStore;
    private final ClientAccessQuery clientAccessQuery;
    private final MembershipAccessQuery membershipAccessQuery;
    private final AccessCredentialResolver accessCredentialResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Optional<DuplicateScanPolicy> duplicateScanPolicy;
    private final AccessPaymentPolicyQuery accessPaymentPolicyQuery;
    private final ConfirmedPaymentForAccessQuery confirmedPaymentForAccessQuery;
    private final BranchOperationContextResolver branchContextResolver;

    /** Constructor retained for focused unit tests and non-QR callers. */
    public AccessApplicationService(
            AccessRecordStore accessRecordStore,
            ClientAccessQuery clientAccessQuery,
            MembershipAccessQuery membershipAccessQuery,
            AccessCredentialResolver accessCredentialResolver,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this(
                accessRecordStore,
                clientAccessQuery,
                membershipAccessQuery,
                accessCredentialResolver,
                eventPublisher,
                clock,
                Optional.empty(),
                DISABLED_POLICY_QUERY,
                NO_PAYMENT_QUERY,
                null);
    }

    public AccessApplicationService(
            AccessRecordStore accessRecordStore,
            ClientAccessQuery clientAccessQuery,
            MembershipAccessQuery membershipAccessQuery,
            AccessCredentialResolver accessCredentialResolver,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            Optional<DuplicateScanPolicy> duplicateScanPolicy) {

        this(
                accessRecordStore,
                clientAccessQuery,
                membershipAccessQuery,
                accessCredentialResolver,
                eventPublisher,
                clock,
                duplicateScanPolicy,
                DISABLED_POLICY_QUERY,
                NO_PAYMENT_QUERY,
                null);
    }

    public AccessApplicationService(
            AccessRecordStore accessRecordStore,
            ClientAccessQuery clientAccessQuery,
            MembershipAccessQuery membershipAccessQuery,
            AccessCredentialResolver accessCredentialResolver,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            Optional<DuplicateScanPolicy> duplicateScanPolicy,
            AccessPaymentPolicyQuery accessPaymentPolicyQuery,
            ConfirmedPaymentForAccessQuery confirmedPaymentForAccessQuery) {

        this(
                accessRecordStore,
                clientAccessQuery,
                membershipAccessQuery,
                accessCredentialResolver,
                eventPublisher,
                clock,
                duplicateScanPolicy,
                accessPaymentPolicyQuery,
                confirmedPaymentForAccessQuery,
                null);
    }

    @Autowired
    public AccessApplicationService(
            AccessRecordStore accessRecordStore,
            ClientAccessQuery clientAccessQuery,
            MembershipAccessQuery membershipAccessQuery,
            AccessCredentialResolver accessCredentialResolver,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            Optional<DuplicateScanPolicy> duplicateScanPolicy,
            AccessPaymentPolicyQuery accessPaymentPolicyQuery,
            ConfirmedPaymentForAccessQuery confirmedPaymentForAccessQuery,
            BranchOperationContextResolver branchContextResolver) {

        this.accessRecordStore = accessRecordStore;
        this.clientAccessQuery = clientAccessQuery;
        this.membershipAccessQuery = membershipAccessQuery;
        this.accessCredentialResolver = accessCredentialResolver;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.duplicateScanPolicy = duplicateScanPolicy == null
                ? Optional.empty()
                : duplicateScanPolicy;
        this.accessPaymentPolicyQuery = Objects.requireNonNull(
                accessPaymentPolicyQuery);
        this.confirmedPaymentForAccessQuery = Objects.requireNonNull(
                confirmedPaymentForAccessQuery);
        this.branchContextResolver = branchContextResolver;
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordDetails checkIn(
            CheckInCommand command,
            AuthenticatedActor actor) {

        validateCommand(command);
        validateActor(actor);
        UUID branchId = branchId(actor);

        // Step 1 & 2: single instant capture; operational date from gym zone.
        Instant occurredAt = clock.instant();
        LocalDate operationalDate = LocalDate.now(clock);

        // Step 3: normalise identifier (throws AccessValidationException if blank).
        AccessIdentifier identifier = AccessIdentifier.of(command.rawIdentifier());

        // Steps 4 & 5: resolve and cross-check.
        AccessCheckInContext context = resolve(identifier, operationalDate);
        authorizeClientBranch(context.clientId(), branchId, actor);

        // Step 6: evaluate existing rules, then the optional payment policy.
        AccessEvaluation evaluation = evaluateWithPaymentPolicy(
                AccessPolicy.evaluate(context), context);

        // Step 7: persist.
        AccessRecordDetails record = branchContextResolver == null
                ? accessRecordStore.persist(
                        identifier.value(),
                        context.clientId(),
                        context.clientCode(),
                        context.membershipId(),
                        context.membershipCode(),
                        context.membershipPeriodId(),
                        evaluation.result(),
                        evaluation.reasonCode(),
                        evaluation.reason(),
                        occurredAt,
                        actor.id())
                : accessRecordStore.persist(
                        identifier.value(),
                        context.clientId(),
                        context.clientCode(),
                        context.membershipId(),
                        context.membershipCode(),
                        context.membershipPeriodId(),
                        evaluation.result(),
                        evaluation.reasonCode(),
                        evaluation.reason(),
                        occurredAt,
                        actor.id(),
                        branchId);

        eventPublisher.publishEvent(
                new AccessAttemptRecorded(
                        record.id(),
                        record.presentedIdentifier(),
                        identifier.type().name(),
                        record.clientId(),
                        record.clientCode(),
                        record.membershipId(),
                        record.membershipCode(),
                        record.result(),
                        record.reasonCode(),
                        record.checkedInAt(),
                        actor.id(),
                        actor.username(),
                        occurredAt,
                        branchId));
        return record;
    }

    /**
     * Evaluates a scanned QR credential through the same client, membership,
     * period, freeze, and cancellation policy used by manual check-in.
     *
     * <p>This read-only operation is retained as an internal preview contract.
     * The state-changing QR check-in path is {@link #checkInQr}, which adds
     * duplicate protection, durable persistence, and event publication.
     * Unknown or inactive credentials are rejected before client resolution
     * because no authoritative identity is available; the constant exception
     * message never reveals token or lookup details.</p>
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public QrAccessCheckInResult evaluateQr(
            QrAccessCheckInCommand command,
            AuthenticatedActor actor) {

        validateQrCommand(command);
        validateActor(actor);
        UUID branchId = branchId(actor);

        Instant evaluatedAt = clock.instant();
        LocalDate operationalDate = LocalDate.now(clock);

        ResolvedAccessCredential credential = accessCredentialResolver
                .resolve(command.payload())
                .orElseThrow(QrAccessCredentialUnavailableException::new);

        AccessIdentifier identifier = AccessIdentifier.qrCredential();
        AccessCheckInContext context = resolveQrClient(
                credential.clientId(), identifier, operationalDate);
        authorizeClientBranch(context.clientId(), branchId, actor);
        AccessEvaluation evaluation = evaluateWithPaymentPolicy(
                AccessPolicy.evaluate(context), context);

        return new QrAccessCheckInResult(
                credential.credentialId(),
                identifier.type(),
                context.clientId(),
                context.clientCode(),
                context.membershipId(),
                context.membershipCode(),
                context.membershipPeriodId(),
                evaluation.result(),
                evaluation.reasonCode(),
                evaluation.reason(),
                evaluatedAt);
    }

    /**
     * Processes and records one QR check-in in a single transaction.
     *
     * <p>The credential resolver locks the active credential row, which
     * serializes duplicate decisions for that credential only. The duplicate
     * query then observes the authoritative committed access history under the
     * same transaction before the attempt is persisted.</p>
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordDetails checkInQr(
            QrAccessCheckInCommand command,
            AuthenticatedActor actor) {

        validateQrCommand(command);
        validateActor(actor);
        UUID branchId = branchId(actor);

        DuplicateScanPolicy policy = duplicateScanPolicy.orElseThrow(
                AccessDuplicateScanPolicyUnavailableException::new);
        Instant occurredAt = clock.instant();
        LocalDate operationalDate = LocalDate.now(clock);

        ResolvedAccessCredential credential = accessCredentialResolver
                .resolveAndLock(command.payload())
                .orElseThrow(QrAccessCredentialUnavailableException::new);

        AccessIdentifier identifier = AccessIdentifier.qrCredential();
        AccessCheckInContext context = resolveQrClient(
                credential.clientId(), identifier, operationalDate);
        authorizeClientBranch(context.clientId(), branchId, actor);
        AccessEvaluation evaluation = AccessPolicy.evaluate(context);

        Optional<AccessRecordDetails> previous = branchContextResolver == null
                ? accessRecordStore.findMostRecentAllowedQrAttempt(
                        credential.credentialId(),
                        occurredAt.minus(policy.window()))
                : accessRecordStore.findMostRecentAllowedQrAttempt(
                        credential.credentialId(),
                        occurredAt.minus(policy.window()),
                        branchId);

        if (policy.evaluate(
                occurredAt,
                previous.map(AccessRecordDetails::checkedInAt).orElse(null))
                == DuplicateScanResult.DUPLICATE) {
            evaluation = AccessEvaluation.denied(
                    AccessReasonCode.DUPLICATE_CHECK_IN,
                    "A recent QR check-in was already recorded.");
        } else {
            // Duplicate decisions remain ahead of the payment requirement so
            // no financial lookup is made for an already-duplicate scan.
            evaluation = evaluateWithPaymentPolicy(evaluation, context);
        }

        AccessRecordDetails record = branchContextResolver == null
                ? accessRecordStore.persistQr(
                        identifier.value(),
                        credential.credentialId(),
                        context.clientId(),
                        context.clientCode(),
                        context.membershipId(),
                        context.membershipCode(),
                        context.membershipPeriodId(),
                        evaluation.result(),
                        evaluation.reasonCode(),
                        evaluation.reason(),
                        occurredAt,
                        actor.id())
                : accessRecordStore.persistQr(
                        identifier.value(),
                        credential.credentialId(),
                        context.clientId(),
                        context.clientCode(),
                        context.membershipId(),
                        context.membershipCode(),
                        context.membershipPeriodId(),
                        evaluation.result(),
                        evaluation.reasonCode(),
                        evaluation.reason(),
                        occurredAt,
                        actor.id(),
                        branchId);

        eventPublisher.publishEvent(
                new AccessAttemptRecorded(
                        record.id(),
                        record.presentedIdentifier(),
                        identifier.type().name(),
                        credential.credentialId(),
                        record.clientId(),
                        record.clientCode(),
                        record.membershipId(),
                        record.membershipCode(),
                        record.result(),
                        record.reasonCode(),
                        record.checkedInAt(),
                        actor.id(),
                        actor.username(),
                        occurredAt,
                        branchId));
        return record;
    }

    /**
     * Applies the persisted payment requirement only after the established
     * non-financial access rules have allowed the request.
     *
     * <p>Payment query failures deliberately propagate as safe system errors;
     * they are never converted into a financial denial.</p>
     */
    private AccessEvaluation evaluateWithPaymentPolicy(
            AccessEvaluation baseEvaluation,
            AccessCheckInContext context) {

        if (baseEvaluation.result() != AccessResult.ALLOWED) {
            return baseEvaluation;
        }

        AccessPaymentPolicyDetails details;
        try {
            details = accessPaymentPolicyQuery.findCurrent();
        } catch (RuntimeException exception) {
            throw new AccessPaymentPolicyEvaluationException(
                    "Access payment policy could not be evaluated.",
                    exception);
        }
        if (details == null) {
            throw new AccessPaymentPolicyEvaluationException(
                    "Access payment policy query returned no policy.");
        }

        // A disabled requirement must preserve the pre-branch workflow and
        // avoid touching payment persistence altogether.
        if (!details.policy().requireConfirmedPaymentForAccess()) {
            return baseEvaluation;
        }

        boolean hasConfirmedPayment;
        try {
            hasConfirmedPayment = confirmedPaymentForAccessQuery
                    .hasConfirmedPaymentForPeriod(
                            context.clientId(),
                            context.membershipId(),
                            context.membershipPeriodId());
        } catch (RuntimeException exception) {
            throw new AccessPaymentPolicyEvaluationException(
                    "Access payment policy could not be evaluated.",
                    exception);
        }

        return AccessPaymentPolicyEvaluator.evaluate(
                baseEvaluation,
                details.policy(),
                hasConfirmedPayment);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordDetails findById(UUID id) {
        if (id == null) {
            throw new AccessValidationException(
                    "Access record identifier must be provided.");
        }
        return accessRecordStore.findById(id)
                .orElseThrow(() -> new AccessRecordNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordDetails findById(
            UUID id,
            AuthenticatedActor actor) {
        if (id == null) {
            throw new AccessValidationException(
                    "Access record identifier must be provided.");
        }
        validateActor(actor);
        UUID branchId = branchId(actor);
        return (branchId == null
                ? accessRecordStore.findById(id)
                : accessRecordStore.findById(id, branchId))
                .orElseThrow(() -> new AccessRecordNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordPage findAll(AccessRecordSearchQuery query) {
        if (query == null) {
            throw new AccessValidationException(
                    "Access record search query must be provided.");
        }
        return accessRecordStore.findAll(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordPage findAll(
            AccessRecordSearchQuery query,
            AuthenticatedActor actor) {
        return findAll(query, actor, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordPage findAll(
            AccessRecordSearchQuery query,
            AuthenticatedActor actor,
            UUID requestedBranchId) {
        if (query == null) {
            throw new AccessValidationException(
                    "Access record search query must be provided.");
        }
        validateActor(actor);
        if (branchContextResolver == null) {
            throw new ActiveBranchContextUnavailableException();
        }
        UUID branchId = BranchResourceAuthorizationPolicy.requireListBranch(
                branchContextResolver.resolveOperation(actor.id()), requestedBranchId);
        return accessRecordStore.findAll(query, branchId);
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves the client and membership through public boundaries and
     * assembles the policy context.
     *
     * <p>Resolution strategy:</p>
     * <ul>
     *   <li>{@code MEMBERSHIP_CODE}: look up membership by code; derive the
     *       client from the membership's {@code clientId}; then confirm the
     *       client record exists (for status evaluation).</li>
     *   <li>{@code CLIENT_CODE}: look up the client by code; then find the
     *       current (ACTIVE or FROZEN) membership for that client.</li>
     *   <li>{@code UNKNOWN}: try membership code first, then client code;
     *       if neither resolves, context has null clientId and membershipId.</li>
     * </ul>
     *
     * <p>Ownership cross-check: if a membership is resolved and a client is
     * also resolved, their IDs must agree. A mismatch is an internal invariant
     * violation and throws {@link IllegalStateException} without persisting
     * any record.</p>
     */
    private AccessCheckInContext resolve(
            AccessIdentifier identifier,
            LocalDate operationalDate) {

        AccessCheckInContext.Builder ctx =
                AccessCheckInContext.builder(identifier, operationalDate);

        if (identifier.type() == AccessIdentifierType.MEMBERSHIP_CODE) {
            resolveByMembershipCode(identifier.value(), ctx);

        } else if (identifier.type() == AccessIdentifierType.CLIENT_CODE) {
            resolveByClientCode(identifier.value(), ctx);

        } else {
            // UNKNOWN prefix: try both paths; first match wins.
            boolean resolved = tryResolveByMembershipCode(identifier.value(), ctx);
            if (!resolved) {
                tryResolveByClientCode(identifier.value(), ctx);
            }
        }

        return ctx.build();
    }

    private void resolveByMembershipCode(
            String normalizedCode,
            AccessCheckInContext.Builder ctx) {

        membershipAccessQuery.findByCode(normalizedCode).ifPresent(mem -> {
            populateMembership(mem, ctx);

            // Derive client from membership; load for status check.
            resolveClientById(mem, ctx);
        });
    }

    private UUID branchId(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        return BranchResourceAuthorizationPolicy.requireActiveBranch(context);
    }

    private void authorizeClientBranch(
            UUID clientId,
            UUID activeBranchId,
            AuthenticatedActor actor) {
        if (branchContextResolver == null || clientId == null || activeBranchId == null) {
            return;
        }
        clientAccessQuery.findById(clientId).ifPresent(client -> {
            if (client.homeBranchId() != null
                    && !activeBranchId.equals(client.homeBranchId())) {
                throw new BranchResourceAuthorizationException();
            }
        });
    }

    private boolean tryResolveByMembershipCode(
            String normalizedCode,
            AccessCheckInContext.Builder ctx) {

        return membershipAccessQuery.findByCode(normalizedCode)
                .map(mem -> {
                    populateMembership(mem, ctx);
                    resolveClientById(mem, ctx);
                    return true;
                })
                .orElse(false);
    }

    private void resolveClientById(
            MembershipAccessDetails mem,
            AccessCheckInContext.Builder ctx) {

        clientAccessQuery.findById(mem.clientId()).ifPresentOrElse(
                client -> {
                    ctx.clientId(client.id())
                       .clientCode(client.clientCode())
                       .clientStatus(client.status().name());
                    // Ownership cross-check (always passes when loaded by UUID).
                    enforceOwnership(mem.clientId(), client.id(), mem.membershipId());
                },
                () -> {
                    // Membership references a client that cannot be loaded —
                    // set clientId so resolution is marked as partial.
                    ctx.clientId(mem.clientId());
                });
    }

    private void resolveByClientCode(
            String normalizedCode,
            AccessCheckInContext.Builder ctx) {

        clientAccessQuery.findByCode(normalizedCode).ifPresent(client -> {
            resolveClientAndCurrentMembership(client, ctx);
        });
    }

    private boolean tryResolveByClientCode(
            String normalizedCode,
            AccessCheckInContext.Builder ctx) {

        return clientAccessQuery.findByCode(normalizedCode)
                .map(client -> {
                    resolveClientAndCurrentMembership(client, ctx);
                    return true;
                })
                .orElse(false);
    }

    private AccessCheckInContext resolveQrClient(
            UUID clientId,
            AccessIdentifier identifier,
            LocalDate operationalDate) {

        ClientAccessDetails client = clientAccessQuery.findById(clientId)
                .orElseThrow(QrAccessCredentialUnavailableException::new);

        AccessCheckInContext.Builder ctx =
                AccessCheckInContext.builder(identifier, operationalDate);
        resolveClientAndCurrentMembership(client, ctx);
        return ctx.build();
    }

    private void resolveClientAndCurrentMembership(
            ClientAccessDetails client,
            AccessCheckInContext.Builder ctx) {

        ctx.clientId(client.id())
           .clientCode(client.clientCode())
           .clientStatus(client.status().name());

        membershipAccessQuery.findCurrentByClientId(client.id())
                .ifPresent(mem -> {
                    populateMembership(mem, ctx);
                    enforceOwnership(client.id(), mem.clientId(), mem.membershipId());
                });
    }

    private static void populateMembership(
            MembershipAccessDetails membership,
            AccessCheckInContext.Builder ctx) {

        ctx.membershipId(membership.membershipId())
           .membershipCode(membership.membershipCode())
           .membershipStatus(membership.status().name())
           .membershipPeriodId(membership.currentPeriodId())
           .periodStartsOn(membership.periodStartsOn())
           .periodEffectiveEndsOn(membership.periodEffectiveEndsOn())
           .freezeStartsOn(membership.freezeStartsOn())
           .freezePlannedEndsOn(membership.freezePlannedEndsOn());
    }

    /**
     * Verifies that the membership belongs to the resolved client.
     * This is an internal invariant — if it fails, no record is written.
     */
    private static void enforceOwnership(
            UUID expectedClientId,
            UUID membershipClientId,
            UUID membershipId) {

        if (!expectedClientId.equals(membershipClientId)) {
            throw new IllegalStateException(
                    "Membership " + membershipId
                            + " belongs to client " + membershipClientId
                            + " but was resolved for client " + expectedClientId
                            + ". Internal data inconsistency.");
        }
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    private static void validateCommand(CheckInCommand command) {
        if (command == null) {
            throw new AccessValidationException(
                    "Check-in command must be provided.");
        }
    }

    private static void validateQrCommand(QrAccessCheckInCommand command) {
        if (command == null) {
            throw new AccessValidationException(
                    "QR access check-in command must be provided.");
        }
    }

    private static void validateActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new AccessValidationException(
                    "Authenticated actor must be provided.");
        }
    }
}
