package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.DuplicateScanPolicy;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialResolver;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodBranchCoverageQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.ConfirmedPaymentForAccessQuery;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BranchAwareAccessDecisionTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID RECORD_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final UUID HOME_BRANCH_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVE_BRANCH_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");
    private static final ZoneId GYM_ZONE = ZoneId.of("America/El_Salvador");
    private static final LocalDate OPERATIONAL_DATE = LocalDate.ofInstant(NOW, GYM_ZONE);

    @Test
    void allowsAccessAtCoveredPhysicalBranchDespiteDifferentClientHomeBranch() {
        AccessRecordStore records = mock(AccessRecordStore.class);
        MembershipPeriodBranchCoverageQuery coverage = mock(
                MembershipPeriodBranchCoverageQuery.class);
        given(coverage.coversBranch(PERIOD_ID, ACTIVE_BRANCH_ID)).willReturn(true);
        given(records.findMostRecentAllowedAttemptAtDifferentBranch(
                CLIENT_ID, ACTIVE_BRANCH_ID, NOW.minusSeconds(30)))
                .willReturn(Optional.empty());
        given(records.persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID), eq(ACTIVE_BRANCH_ID)))
                .willReturn(record(AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED));

        AccessRecordDetails result = service(records, coverage)
                .checkIn(new CheckInCommand("MEM-000001"), actor());

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        verify(records).lockClientAccess(CLIENT_ID);
        verify(coverage).coversBranch(PERIOD_ID, ACTIVE_BRANCH_ID);
    }

    @Test
    void deniesUncoveredBranchWithoutDisclosingEntitlementDetails() {
        AccessRecordStore records = mock(AccessRecordStore.class);
        MembershipPeriodBranchCoverageQuery coverage = mock(
                MembershipPeriodBranchCoverageQuery.class);
        given(coverage.coversBranch(PERIOD_ID, ACTIVE_BRANCH_ID)).willReturn(false);
        given(records.findMostRecentAllowedAttemptAtDifferentBranch(
                CLIENT_ID, ACTIVE_BRANCH_ID, NOW.minusSeconds(30)))
                .willReturn(Optional.empty());
        given(records.persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH),
                any(), eq(NOW), eq(ACTOR_ID), eq(ACTIVE_BRANCH_ID)))
                .willReturn(record(
                        AccessResult.DENIED,
                        AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH));

        AccessRecordDetails result = service(records, coverage)
                .checkIn(new CheckInCommand("MEM-000001"), actor());

        assertThat(result.reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH);
        assertThat(result.reason())
                .doesNotContain(HOME_BRANCH_ID.toString(), ACTIVE_BRANCH_ID.toString());
        verify(records).persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH),
                any(), eq(NOW), eq(ACTOR_ID), eq(ACTIVE_BRANCH_ID));
    }

    @Test
    void entitlementQueryFailureFailsClosedWithoutPersistingOrPublishing() {
        AccessRecordStore records = mock(AccessRecordStore.class);
        MembershipPeriodBranchCoverageQuery coverage = mock(
                MembershipPeriodBranchCoverageQuery.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        given(coverage.coversBranch(PERIOD_ID, ACTIVE_BRANCH_ID))
                .willThrow(new IllegalStateException("database details"));
        given(records.findMostRecentAllowedAttemptAtDifferentBranch(
                CLIENT_ID, ACTIVE_BRANCH_ID, NOW.minusSeconds(30)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service(records, coverage, events)
                .checkIn(new CheckInCommand("MEM-000001"), actor()))
                .isInstanceOf(AccessMembershipCoverageEvaluationException.class)
                .hasMessage("Membership branch entitlement could not be evaluated.")
                .hasMessageNotContaining("database details");

        verify(records, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void branchPolicyQueryFailureIsNotConvertedToPaymentDenialOrAllow() {
        AccessRecordStore records = mock(AccessRecordStore.class);
        MembershipPeriodBranchCoverageQuery coverage = mock(
                MembershipPeriodBranchCoverageQuery.class);
        BranchAccessPolicyQuery branchPolicies = mock(BranchAccessPolicyQuery.class);
        ConfirmedPaymentForAccessQuery payments = mock(ConfirmedPaymentForAccessQuery.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        given(coverage.coversBranch(PERIOD_ID, ACTIVE_BRANCH_ID)).willReturn(true);
        given(records.findMostRecentAllowedAttemptAtDifferentBranch(
                CLIENT_ID, ACTIVE_BRANCH_ID, NOW.minusSeconds(30)))
                .willReturn(Optional.empty());
        given(branchPolicies.findForBranch(ORGANIZATION_ID, ACTIVE_BRANCH_ID))
                .willThrow(new IllegalStateException("database details"));

        assertThatThrownBy(() -> service(
                records, coverage, events, branchPolicies, payments)
                .checkIn(new CheckInCommand("MEM-000001"), actor()))
                .isInstanceOf(AccessPaymentPolicyEvaluationException.class)
                .hasMessage("Access payment policy could not be evaluated.")
                .hasMessageNotContaining("database details");

        verify(payments, never()).hasConfirmedPaymentForPeriod(any(), any(), any());
        verify(records, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    private static AccessApplicationService service(
            AccessRecordStore records,
            MembershipPeriodBranchCoverageQuery coverage) {
        return service(records, coverage, mock(ApplicationEventPublisher.class));
    }

    private static AccessApplicationService service(
            AccessRecordStore records,
            MembershipPeriodBranchCoverageQuery coverage,
            ApplicationEventPublisher events) {
        return service(
                records,
                coverage,
                events,
                (organizationId, branchId) -> new EffectiveBranchAccessPolicy(
                        organizationId,
                        branchId,
                        false,
                        BranchAccessPaymentPolicyMode.INHERIT),
                (clientId, membershipId, periodId) -> false);
    }

    private static AccessApplicationService service(
            AccessRecordStore records,
            MembershipPeriodBranchCoverageQuery coverage,
            ApplicationEventPublisher events,
            BranchAccessPolicyQuery branchPolicies,
            ConfirmedPaymentForAccessQuery payments) {
        ClientAccessQuery clients = mock(ClientAccessQuery.class);
        MembershipAccessQuery memberships = mock(MembershipAccessQuery.class);
        AccessCredentialResolver credentials = mock(AccessCredentialResolver.class);
        BranchOperationContextResolver branches = mock(BranchOperationContextResolver.class);

        given(memberships.findByCode("MEM-000001"))
                .willReturn(Optional.of(new MembershipAccessDetails(
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        MembershipStatus.ACTIVE,
                        PERIOD_ID,
                        OPERATIONAL_DATE.minusDays(2),
                        OPERATIONAL_DATE.plusDays(2),
                        null,
                        null)));
        given(clients.findById(CLIENT_ID)).willReturn(Optional.of(
                new ClientAccessDetails(
                        CLIENT_ID,
                        "CLI-000001",
                        ClientStatus.ACTIVE,
                        HOME_BRANCH_ID)));
        given(branches.resolveOperation(ACTOR_ID)).willReturn(
                new BranchOperationContext(
                        ACTOR_ID,
                        ORGANIZATION_ID,
                        StaffScopeType.BRANCH,
                        ACTIVE_BRANCH_ID,
                        Set.of(ACTIVE_BRANCH_ID)));

        return new AccessApplicationService(
                records,
                clients,
                memberships,
                credentials,
                events,
                Clock.fixed(NOW, GYM_ZONE),
                Optional.of(new DuplicateScanPolicy(Duration.ofSeconds(30))),
                () -> new AccessPaymentPolicyDetails(false, 0),
                payments,
                branches,
                coverage,
                branchPolicies);
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(ACTOR_ID, "branch-receptionist");
    }

    private static AccessRecordDetails record(
            AccessResult result,
            AccessReasonCode reasonCode) {
        return new AccessRecordDetails(
                RECORD_ID,
                "MEM-000001",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                result,
                reasonCode,
                reasonCode == AccessReasonCode.ACCESS_ALLOWED
                        ? "Membership is active and its current period is valid."
                        : "The membership is not valid at this branch.",
                NOW,
                ACTOR_ID,
                ACTIVE_BRANCH_ID);
    }
}
