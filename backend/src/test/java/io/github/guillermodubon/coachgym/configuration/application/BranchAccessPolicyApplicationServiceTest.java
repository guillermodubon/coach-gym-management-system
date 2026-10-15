package io.github.guillermodubon.coachgym.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorization;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyOverrideChanged;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import io.github.guillermodubon.coachgym.configuration.UpdateBranchAccessPolicyCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BranchAccessPolicyApplicationServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final AccessPaymentPolicyActor ACTOR =
            new AccessPaymentPolicyActor(ACTOR_ID, "org-admin");

    @Mock private BranchAccessPolicyQuery policyQuery;
    @Mock private BranchAccessPolicyStore policyStore;
    @Mock private AccessPaymentPolicyAuthorization authorization;
    @Mock private ApplicationEventPublisher eventPublisher;

    private BranchAccessPolicyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new BranchAccessPolicyApplicationService(
                policyQuery,
                policyStore,
                authorization,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void organizationAdministratorCanSetOverrideAndPublishesSafeVersionedEvent() {
        allowOrganizationAdministrator();
        when(policyQuery.findForBranch(ORGANIZATION_ID, BRANCH_ID))
                .thenReturn(policy(false, BranchAccessPaymentPolicyMode.INHERIT, 0));
        when(policyStore.update(
                ORGANIZATION_ID,
                BRANCH_ID,
                BranchAccessPaymentPolicyMode.REQUIRED,
                0,
                ACTOR_ID,
                NOW))
                .thenReturn(policy(false, BranchAccessPaymentPolicyMode.REQUIRED, 1));

        EffectiveBranchAccessPolicy updated = service.updateOverride(
                command(BranchAccessPaymentPolicyMode.REQUIRED, 0), ACTOR);

        assertThat(updated.requireConfirmedPaymentForAccess()).isTrue();
        assertThat(updated.version()).isEqualTo(1);
        ArgumentCaptor<BranchAccessPolicyOverrideChanged> event =
                ArgumentCaptor.forClass(BranchAccessPolicyOverrideChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().previousMode())
                .isEqualTo(BranchAccessPaymentPolicyMode.INHERIT);
        assertThat(event.getValue().newMode())
                .isEqualTo(BranchAccessPaymentPolicyMode.REQUIRED);
        assertThat(event.getValue().actorIdentifier()).isEqualTo("org-admin");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().toString())
                .doesNotContain("payment details", "card", "token", "secret");
    }

    @Test
    void inheritCommandClearsOverrideAsVersionedChange() {
        allowOrganizationAdministrator();
        when(policyQuery.findForBranch(ORGANIZATION_ID, BRANCH_ID))
                .thenReturn(policy(false, BranchAccessPaymentPolicyMode.NOT_REQUIRED, 4));
        when(policyStore.update(
                ORGANIZATION_ID,
                BRANCH_ID,
                BranchAccessPaymentPolicyMode.INHERIT,
                4,
                ACTOR_ID,
                NOW))
                .thenReturn(policy(true, BranchAccessPaymentPolicyMode.INHERIT, 5));

        EffectiveBranchAccessPolicy cleared = service.updateOverride(
                command(BranchAccessPaymentPolicyMode.INHERIT, 4), ACTOR);

        assertThat(cleared.branchMode()).isEqualTo(BranchAccessPaymentPolicyMode.INHERIT);
        assertThat(cleared.requireConfirmedPaymentForAccess()).isTrue();
        assertThat(cleared.version()).isEqualTo(5);
        verify(eventPublisher).publishEvent(any(BranchAccessPolicyOverrideChanged.class));
    }

    @Test
    void rejectsStaleVersionBeforeWriteOrEvent() {
        allowOrganizationAdministrator();
        when(policyQuery.findForBranch(ORGANIZATION_ID, BRANCH_ID))
                .thenReturn(policy(false, BranchAccessPaymentPolicyMode.INHERIT, 3));

        assertThatThrownBy(() -> service.updateOverride(
                command(BranchAccessPaymentPolicyMode.REQUIRED, 2), ACTOR))
                .isInstanceOf(BranchAccessPolicyVersionConflictException.class);

        verify(policyStore, never()).update(
                any(), any(), any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unchangedModeIsIdempotentAndDoesNotIncrementVersion() {
        allowOrganizationAdministrator();
        EffectiveBranchAccessPolicy current = policy(
                false, BranchAccessPaymentPolicyMode.INHERIT, 7);
        when(policyQuery.findForBranch(ORGANIZATION_ID, BRANCH_ID)).thenReturn(current);

        assertThat(service.updateOverride(
                command(BranchAccessPaymentPolicyMode.INHERIT, 7), ACTOR))
                .isEqualTo(current);

        verify(policyStore, never()).update(
                any(), any(), any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsBranchNotFoundWithinOrganizationBeforeWritingOrPublishing() {
        when(authorization.requireOrganizationAdministrator(ACTOR))
                .thenReturn(ORGANIZATION_ID);
        when(policyQuery.findForBranch(ORGANIZATION_ID, BRANCH_ID))
                .thenThrow(new BranchAccessPolicyNotFoundException());

        assertThatThrownBy(() -> service.updateOverride(
                command(BranchAccessPaymentPolicyMode.REQUIRED, 0), ACTOR))
                .isInstanceOf(BranchAccessPolicyNotFoundException.class);

        verify(policyQuery).findForBranch(ORGANIZATION_ID, BRANCH_ID);
        verify(policyStore, never()).update(
                any(), any(), any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static UpdateBranchAccessPolicyCommand command(
            BranchAccessPaymentPolicyMode mode,
            long version) {
        return new UpdateBranchAccessPolicyCommand(BRANCH_ID, mode, version);
    }

    private void allowOrganizationAdministrator() {
        when(authorization.requireOrganizationAdministrator(ACTOR))
                .thenReturn(ORGANIZATION_ID);
    }

    private static EffectiveBranchAccessPolicy policy(
            boolean defaultRequiresPayment,
            BranchAccessPaymentPolicyMode mode,
            long version) {
        return new EffectiveBranchAccessPolicy(
                ORGANIZATION_ID, BRANCH_ID, defaultRequiresPayment, mode, version);
    }
}
