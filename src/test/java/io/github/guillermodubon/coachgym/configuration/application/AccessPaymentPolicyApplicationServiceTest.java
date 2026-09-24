package io.github.guillermodubon.coachgym.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorization;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyChanged;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorizationException;
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
class AccessPaymentPolicyApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final AccessPaymentPolicyActor ACTOR =
            new AccessPaymentPolicyActor(ACTOR_ID, "policy-admin");

    @Mock
    private AccessPaymentPolicyQuery policyQuery;

    @Mock
    private AccessPaymentPolicyStore policyStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AccessPaymentPolicyAuthorization authorization;

    private AccessPaymentPolicyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccessPaymentPolicyApplicationService(
                policyQuery, policyStore, authorization, eventPublisher, CLOCK);
    }

    @Test
    void retrievesTheCurrentPersistedPolicy() {
        AccessPaymentPolicyDetails current = details(false, 3);
        when(policyQuery.findCurrent()).thenReturn(current);

        assertThat(service.findCurrent(ACTOR)).isEqualTo(current);

        verify(policyQuery).findCurrent();
    }

    @Test
    void updatesThePolicyWithTheExpectedVersionAndPublishesOneSafeEvent() {
        AccessPaymentPolicyDetails current = details(false, 3);
        AccessPaymentPolicyDetails updated = details(true, 4);
        when(policyQuery.findCurrent()).thenReturn(current);
        when(policyStore.update(AccessPaymentPolicy.enabled(), 3, ACTOR_ID, NOW))
                .thenReturn(updated);

        AccessPaymentPolicyDetails result = service.update(
                new UpdateAccessPaymentPolicyCommand(true, 3), ACTOR);

        assertThat(result).isEqualTo(updated);
        verify(policyStore).update(AccessPaymentPolicy.enabled(), 3, ACTOR_ID, NOW);

        ArgumentCaptor<AccessPaymentPolicyChanged> eventCaptor =
                ArgumentCaptor.forClass(AccessPaymentPolicyChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().previousValue()).isFalse();
        assertThat(eventCaptor.getValue().newValue()).isTrue();
        assertThat(eventCaptor.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(eventCaptor.getValue().actorIdentifier()).isEqualTo("policy-admin");
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAStaleVersionBeforeWritingOrPublishing() {
        when(policyQuery.findCurrent()).thenReturn(details(false, 4));

        assertThatThrownBy(() -> service.update(
                new UpdateAccessPaymentPolicyCommand(true, 3), ACTOR))
                .isInstanceOf(AccessPaymentPolicyVersionConflictException.class);

        verify(policyStore, never()).update(any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void returnsNoOpWithoutChangingVersionOrPublishing() {
        AccessPaymentPolicyDetails current = details(false, 3);
        when(policyQuery.findCurrent()).thenReturn(current);

        assertThat(service.update(
                new UpdateAccessPaymentPolicyCommand(false, 3), ACTOR))
                .isEqualTo(current);

        verify(policyStore, never()).update(any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doesNotPublishWhenPersistenceFails() {
        when(policyQuery.findCurrent()).thenReturn(details(false, 3));
        when(policyStore.update(AccessPaymentPolicy.enabled(), 3, ACTOR_ID, NOW))
                .thenThrow(new AccessPaymentPolicyDataAccessException(
                        "Access payment policy could not be updated.", null));

        assertThatThrownBy(() -> service.update(
                new UpdateAccessPaymentPolicyCommand(true, 3), ACTOR))
                .isInstanceOf(AccessPaymentPolicyDataAccessException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requiresAnAuthenticatedActor() {
        doThrow(new AccessPaymentPolicyAuthorizationException())
                .when(authorization).requireOrganizationAdministrator(null);

        assertThatThrownBy(() -> service.update(
                new UpdateAccessPaymentPolicyCommand(true, 3), null))
                .isInstanceOf(AccessPaymentPolicyAuthorizationException.class);

        verify(policyStore, never()).update(any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static AccessPaymentPolicyDetails details(boolean enabled, long version) {
        return new AccessPaymentPolicyDetails(enabled, version, NOW, ACTOR_ID);
    }
}
