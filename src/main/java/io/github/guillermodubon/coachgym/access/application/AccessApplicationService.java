package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.domain.AccessCheckInContext;
import io.github.guillermodubon.coachgym.access.domain.AccessEvaluation;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifier;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifierType;
import io.github.guillermodubon.coachgym.access.domain.AccessPolicy;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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

    private final AccessRecordStore accessRecordStore;
    private final ClientAccessQuery clientAccessQuery;
    private final MembershipAccessQuery membershipAccessQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AccessApplicationService(
            AccessRecordStore accessRecordStore,
            ClientAccessQuery clientAccessQuery,
            MembershipAccessQuery membershipAccessQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.accessRecordStore = accessRecordStore;
        this.clientAccessQuery = clientAccessQuery;
        this.membershipAccessQuery = membershipAccessQuery;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessRecordDetails checkIn(
            CheckInCommand command,
            AuthenticatedActor actor) {

        validateCommand(command);
        validateActor(actor);

        // Step 1 & 2: single instant capture; operational date from gym zone.
        Instant occurredAt = clock.instant();
        LocalDate operationalDate = LocalDate.now(clock);

        // Step 3: normalise identifier (throws AccessValidationException if blank).
        AccessIdentifier identifier = AccessIdentifier.of(command.rawIdentifier());

        // Steps 4 & 5: resolve and cross-check.
        AccessCheckInContext context = resolve(identifier, operationalDate);

        // Step 6: evaluate policy.
        AccessEvaluation evaluation = AccessPolicy.evaluate(context);

        // Step 7: persist.
        AccessRecordDetails record = accessRecordStore.persist(
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
                actor.id());

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
                        occurredAt));
        return record;
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
    public AccessRecordPage findAll(AccessRecordSearchQuery query) {
        if (query == null) {
            throw new AccessValidationException(
                    "Access record search query must be provided.");
        }
        return accessRecordStore.findAll(query);
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
            ctx.membershipId(mem.membershipId())
               .membershipCode(mem.membershipCode())
               .membershipStatus(mem.status().name())
               .membershipPeriodId(mem.currentPeriodId())
               .periodStartsOn(mem.periodStartsOn())
               .periodEffectiveEndsOn(mem.periodEffectiveEndsOn())
               .freezeStartsOn(mem.freezeStartsOn())
               .freezePlannedEndsOn(mem.freezePlannedEndsOn());

            // Derive client from membership; load for status check.
            resolveClientById(mem, ctx);
        });
    }

    private boolean tryResolveByMembershipCode(
            String normalizedCode,
            AccessCheckInContext.Builder ctx) {

        return membershipAccessQuery.findByCode(normalizedCode)
                .map(mem -> {
                    ctx.membershipId(mem.membershipId())
                       .membershipCode(mem.membershipCode())
                       .membershipStatus(mem.status().name())
                       .membershipPeriodId(mem.currentPeriodId())
                       .periodStartsOn(mem.periodStartsOn())
                       .periodEffectiveEndsOn(mem.periodEffectiveEndsOn())
                       .freezeStartsOn(mem.freezeStartsOn())
                       .freezePlannedEndsOn(mem.freezePlannedEndsOn());
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
            ctx.clientId(client.id())
               .clientCode(client.clientCode())
               .clientStatus(client.status().name());

            // Look for current membership.
            membershipAccessQuery.findCurrentByClientId(client.id())
                    .ifPresent(mem -> {
                        ctx.membershipId(mem.membershipId())
                           .membershipCode(mem.membershipCode())
                           .membershipStatus(mem.status().name())
                           .membershipPeriodId(mem.currentPeriodId())
                           .periodStartsOn(mem.periodStartsOn())
                           .periodEffectiveEndsOn(mem.periodEffectiveEndsOn())
                           .freezeStartsOn(mem.freezeStartsOn())
                           .freezePlannedEndsOn(mem.freezePlannedEndsOn());
                        // Ownership cross-check.
                        enforceOwnership(client.id(), mem.clientId(), mem.membershipId());
                    });
        });
    }

    private boolean tryResolveByClientCode(
            String normalizedCode,
            AccessCheckInContext.Builder ctx) {

        return clientAccessQuery.findByCode(normalizedCode)
                .map(client -> {
                    ctx.clientId(client.id())
                       .clientCode(client.clientCode())
                       .clientStatus(client.status().name());

                    membershipAccessQuery.findCurrentByClientId(client.id())
                            .ifPresent(mem -> {
                                ctx.membershipId(mem.membershipId())
                                   .membershipCode(mem.membershipCode())
                                   .membershipStatus(mem.status().name())
                                   .membershipPeriodId(mem.currentPeriodId())
                                   .periodStartsOn(mem.periodStartsOn())
                                   .periodEffectiveEndsOn(mem.periodEffectiveEndsOn())
                                   .freezeStartsOn(mem.freezeStartsOn())
                                   .freezePlannedEndsOn(mem.freezePlannedEndsOn());
                                enforceOwnership(client.id(), mem.clientId(), mem.membershipId());
                            });
                    return true;
                })
                .orElse(false);
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

    private static void validateActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new AccessValidationException(
                    "Authenticated actor must be provided.");
        }
    }
}
