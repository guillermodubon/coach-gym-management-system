package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessCheckInContext;
import io.github.guillermodubon.coachgym.access.domain.AccessDenialPrecedence;
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
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodBranchCoverageQuery;
import io.github.guillermodubon.coachgym.payment.ConfirmedPaymentForAccessQuery;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
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
 *   <li>Evaluate membership lifecycle, immutable period branch coverage,
 *       anti-passback, and the current payment requirement in deterministic
 *       precedence order.</li>
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
    private final MembershipPeriodBranchCoverageQuery periodBranchCoverageQuery;
    private final BranchAccessPolicyQuery branchAccessPolicyQuery;

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
                null,
                null,
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
                null,
                null,
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
                null,
                null,
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
            ConfirmedPaymentForAccessQuery confirmedPaymentForAccessQuery,
            BranchOperationContextResolver branchContextResolver,
            MembershipPeriodBranchCoverageQuery periodBranchCoverageQuery) {

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
                branchContextResolver,
                periodBranchCoverageQuery,
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
            BranchOperationContextResolver branchContextResolver,
            MembershipPeriodBranchCoverageQuery periodBranchCoverageQuery,
            BranchAccessPolicyQuery branchAccessPolicyQuery) {

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
        this.periodBranchCoverageQuery = periodBranchCoverageQuery;
        this.branchAccessPolicyQuery = branchAccessPolicyQuery;
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordDetails checkIn(
            CheckInCommand command,
            AuthenticatedActor actor) {

        validateCommand(command);
        validateActor(actor);
        BranchOperationContext branchContext = branchOperationContext(actor);
        UUID branchId = branchContext == null
                ? null
                : BranchResourceAuthorizationPolicy.requireActiveBranch(branchContext);

        // Step 1 & 2: single instant capture; operational date from gym zone.
        Instant occurredAt = clock.instant();
        LocalDate operationalDate = LocalDate.now(clock);

        // Step 3: normalise identifier (throws AccessValidationException if blank).
        AccessIdentifier identifier = AccessIdentifier.of(command.rawIdentifier());

        // Steps 4 & 5: resolve and cross-check.
        AccessCheckInContext context = resolve(identifier, operationalDate);

        // Existing lifecycle, branch entitlement, anti-passback, and payment
        // rules are composed in the ADR-approved order.
        AccessEvaluation evaluation = evaluateCheckIn(
                AccessPolicy.evaluate(context),
                context,
                branchId,
                branchContext == null ? null : branchContext.organizationId(),
                occurredAt,
                null);

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
        BranchOperationContext branchContext = branchOperationContext(actor);
        UUID branchId = branchContext == null
                ? null
                : BranchResourceAuthorizationPolicy.requireActiveBranch(branchContext);

        Instant evaluatedAt = clock.instant();
        LocalDate operationalDate = LocalDate.now(clock);

        ResolvedAccessCredential credential = accessCredentialResolver
                .resolve(command.payload())
                .orElseThrow(QrAccessCredentialUnavailableException::new);

        AccessIdentifier identifier = AccessIdentifier.qrCredential();
        AccessCheckInContext context = resolveQrClient(
                credential.clientId(), identifier, operationalDate);
        AccessEvaluation evaluation = evaluateWithPaymentPolicy(
                evaluateBranchCoverage(AccessPolicy.evaluate(context), context, branchId),
                context,
                branchContext == null ? null : branchContext.organizationId(),
                branchId);

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
        BranchOperationContext branchContext = branchOperationContext(actor);
        UUID branchId = branchContext == null
                ? null
                : BranchResourceAuthorizationPolicy.requireActiveBranch(branchContext);

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
        AccessEvaluation evaluation = evaluateCheckIn(
                AccessPolicy.evaluate(context),
                context,
                branchId,
                branchContext == null ? null : branchContext.organizationId(),
                occurredAt,
                credential.credentialId());

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
     * Composes the non-financial check-in decisions in the precedence fixed by
     * ADR-010. Duplicate detection is performed before coverage so a prior
     * successful entry wins over a current lifecycle or entitlement denial.
     */
    private AccessEvaluation evaluateCheckIn(
            AccessEvaluation membershipEvaluation,
            AccessCheckInContext context,
            UUID branchId,
            UUID organizationId,
            Instant occurredAt,
            UUID qrCredentialId) {

        if (isDuplicateCheckIn(context, branchId, occurredAt, qrCredentialId)) {
            java.util.List<AccessReasonCode> candidates = new java.util.ArrayList<>();
            candidates.add(AccessReasonCode.DUPLICATE_CHECK_IN);
            if (membershipEvaluation.result() == AccessResult.DENIED) {
                candidates.add(membershipEvaluation.reasonCode());
            }
            AccessReasonCode selectedReason = AccessDenialPrecedence
                    .selectHighestPriority(candidates);
            return denialFor(selectedReason);
        }

        AccessEvaluation branchEvaluation = evaluateBranchCoverage(
                membershipEvaluation, context, branchId);
        return evaluateWithPaymentPolicy(
                branchEvaluation, context, organizationId, branchId);
    }

    private boolean isDuplicateCheckIn(
            AccessCheckInContext context,
            UUID branchId,
            Instant occurredAt,
            UUID qrCredentialId) {

        boolean hasResolvedClient = context.clientId() != null;
        if (branchId != null && hasResolvedClient) {
            DuplicateScanPolicy policy = duplicateScanPolicy.orElseThrow(
                    AccessDuplicateScanPolicyUnavailableException::new);
            accessRecordStore.lockClientAccess(context.clientId());

            Optional<AccessRecordDetails> previousAtAnotherBranch =
                    accessRecordStore.findMostRecentAllowedAttemptAtDifferentBranch(
                            context.clientId(),
                            branchId,
                            occurredAt.minus(policy.window()));
            if (previousAtAnotherBranch.isPresent()
                    && policy.evaluateAcrossBranches(
                            context.clientId(),
                            branchId,
                            occurredAt,
                            previousAtAnotherBranch.get().clientId(),
                            previousAtAnotherBranch.get().branchId(),
                            previousAtAnotherBranch.get().result(),
                            previousAtAnotherBranch.get().checkedInAt())
                            == DuplicateScanResult.DUPLICATE) {
                return true;
            }
        }

        if (qrCredentialId == null) {
            return false;
        }

        DuplicateScanPolicy policy = duplicateScanPolicy.orElseThrow(
                AccessDuplicateScanPolicyUnavailableException::new);
        Instant occurredAtFromInclusive = occurredAt.minus(policy.window());
        Optional<AccessRecordDetails> previousAtBranch = branchId == null
                ? accessRecordStore.findMostRecentAllowedQrAttempt(
                        qrCredentialId, occurredAtFromInclusive)
                : accessRecordStore.findMostRecentAllowedQrAttempt(
                        qrCredentialId, occurredAtFromInclusive, branchId);

        return policy.evaluate(
                occurredAt,
                previousAtBranch.map(AccessRecordDetails::checkedInAt).orElse(null))
                == DuplicateScanResult.DUPLICATE;
    }

    private AccessEvaluation evaluateBranchCoverage(
            AccessEvaluation membershipEvaluation,
            AccessCheckInContext context,
            UUID branchId) {

        if (membershipEvaluation.result() != AccessResult.ALLOWED
                || branchId == null) {
            return membershipEvaluation;
        }
        if (context.membershipPeriodId() == null) {
            throw new IllegalStateException(
                    "An allowed membership evaluation must identify its current period.");
        }
        if (periodBranchCoverageQuery == null) {
            throw new AccessMembershipCoverageEvaluationException(
                    "Membership branch entitlement could not be evaluated.");
        }

        final boolean covered;
        try {
            covered = periodBranchCoverageQuery.coversBranch(
                    context.membershipPeriodId(), branchId);
        } catch (RuntimeException exception) {
            throw new AccessMembershipCoverageEvaluationException(
                    "Membership branch entitlement could not be evaluated.", exception);
        }
        if (!covered) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH,
                    "The membership is not valid at this branch.");
        }
        return membershipEvaluation;
    }

    private static AccessEvaluation denialFor(AccessReasonCode reasonCode) {
        return switch (reasonCode) {
            case DUPLICATE_CHECK_IN -> AccessEvaluation.denied(
                    reasonCode,
                    "A recent access entry was already recorded.");
            case ACCESS_CREDENTIAL_INVALID -> AccessEvaluation.denied(
                    reasonCode,
                    "The QR access credential is unavailable.");
            case IDENTIFIER_NOT_FOUND -> AccessEvaluation.denied(
                    reasonCode,
                    "The presented identifier could not be resolved.");
            case CLIENT_INACTIVE -> AccessEvaluation.denied(
                    reasonCode,
                    "The client account is inactive.");
            case MEMBERSHIP_NOT_FOUND -> AccessEvaluation.denied(
                    reasonCode,
                    "No current membership was found for this client.");
            case MEMBERSHIP_CANCELLED -> AccessEvaluation.denied(
                    reasonCode,
                    "The membership has been cancelled.");
            case MEMBERSHIP_FROZEN -> AccessEvaluation.denied(
                    reasonCode,
                    "The membership is currently frozen.");
            case MEMBERSHIP_EXPIRED -> AccessEvaluation.denied(
                    reasonCode,
                    "The membership has expired.");
            case MEMBERSHIP_PERIOD_EXPIRED -> AccessEvaluation.denied(
                    reasonCode,
                    "The current membership period has expired.");
            case MEMBERSHIP_NOT_STARTED -> AccessEvaluation.denied(
                    reasonCode,
                    "The current membership period has not started yet.");
            case MEMBERSHIP_NOT_VALID_AT_BRANCH -> AccessEvaluation.denied(
                    reasonCode,
                    "The membership is not valid at this branch.");
            case PAYMENT_REQUIRED -> AccessEvaluation.denied(
                    reasonCode,
                    "A confirmed payment is required for access.");
            case ACCESS_ALLOWED -> throw new IllegalArgumentException(
                    "ACCESS_ALLOWED is not a denial reason.");
        };
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
            AccessCheckInContext context,
            UUID organizationId,
            UUID branchId) {

        if (baseEvaluation.result() != AccessResult.ALLOWED) {
            return baseEvaluation;
        }

        AccessPaymentPolicy policy;
        if (branchId != null) {
            try {
                if (branchAccessPolicyQuery == null || organizationId == null) {
                    throw new IllegalStateException(
                            "Branch access policy query is unavailable.");
                }
                EffectiveBranchAccessPolicy effective = branchAccessPolicyQuery
                        .findForBranch(organizationId, branchId);
                if (effective == null) {
                    throw new IllegalStateException(
                            "Branch access policy query returned no policy.");
                }
                policy = new AccessPaymentPolicy(
                        effective.requireConfirmedPaymentForAccess());
            } catch (RuntimeException exception) {
                throw new AccessPaymentPolicyEvaluationException(
                        "Access payment policy could not be evaluated.", exception);
            }
        } else {
            AccessPaymentPolicyDetails details;
            try {
                details = accessPaymentPolicyQuery.findCurrent();
            } catch (RuntimeException exception) {
                throw new AccessPaymentPolicyEvaluationException(
                        "Access payment policy could not be evaluated.", exception);
            }
            if (details == null) {
                throw new AccessPaymentPolicyEvaluationException(
                        "Access payment policy query returned no policy.");
            }
            policy = details.policy();
        }

        // A disabled requirement must preserve the pre-branch workflow and
        // avoid touching payment persistence altogether.
        if (!policy.requireConfirmedPaymentForAccess()) {
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
                policy,
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
        BranchOperationContext context = branchOperationContext(actor);
        if (context == null) {
            return null;
        }
        return BranchResourceAuthorizationPolicy.requireActiveBranch(context);
    }

    private BranchOperationContext branchOperationContext(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        if (context == null || !actor.id().equals(context.userId())) {
            throw new ActiveBranchContextUnavailableException();
        }
        return context;
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
