package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrganizationApplicationServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "a31031de-d7c7-4887-bcae-749afea5b94c");
    private static final Instant NOW = Instant.parse("2026-09-20T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    @Mock
    private OrganizationQuery organizationQuery;

    @Mock
    private OrganizationStore organizationStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrganizationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationApplicationService(
                organizationQuery, organizationStore, eventPublisher, CLOCK);
    }

    @Test
    void updateUsesServerClockAndPublishesOnePrivacySafeEvent() {
        OrganizationDetails current = organization(0, "Coach Gym", "Coach Gym");
        OrganizationDetails updated = organization(1, "Coach Gym Legal", "Coach Gym Brand");
        when(organizationQuery.findCanonical()).thenReturn(Optional.of(current));
        when(organizationStore.update(any(UpdateOrganizationCommand.class), any()))
                .thenReturn(updated);

        OrganizationDetails result = service.update(
                new UpdateOrganizationCommand(
                        "Coach Gym Legal",
                        "Coach Gym Brand",
                        null,
                        null,
                        "America/El_Salvador",
                        "USD",
                        0),
                ACTOR);

        assertThat(result).isEqualTo(updated);
        ArgumentCaptor<OrganizationUpdated> event =
                ArgumentCaptor.forClass(OrganizationUpdated.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(event.getValue().organizationCode()).isEqualTo("COACH_GYM");
        assertThat(event.getValue().changedFields())
                .containsExactlyInAnyOrder("legalName", "brandName");
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().toString())
                .doesNotContain("Coach Gym Legal", "support@example", "secret");
    }

    @Test
    void noOpAndStaleUpdatesDoNotWriteOrPublish() {
        OrganizationDetails current = organization(4, "Coach Gym", "Coach Gym");
        when(organizationQuery.findCanonical()).thenReturn(Optional.of(current));
        UpdateOrganizationCommand same = new UpdateOrganizationCommand(
                current.legalName(),
                current.brandName(),
                current.supportEmail(),
                current.supportPhone(),
                current.defaultTimezone(),
                current.defaultCurrency(),
                4);

        assertThat(service.update(same, ACTOR)).isEqualTo(current);
        verify(organizationStore, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());

        assertThatThrownBy(() -> service.update(
                new UpdateOrganizationCommand(
                        "Changed", current.brandName(), null, null,
                        current.defaultTimezone(), current.defaultCurrency(), 3),
                ACTOR))
                .isInstanceOf(OrganizationVersionConflictException.class);
        verify(organizationStore, never()).update(any(), any());
    }

    private static OrganizationDetails organization(
            long version, String legalName, String brandName) {
        return new OrganizationDetails(
                ORGANIZATION_ID,
                "COACH_GYM",
                legalName,
                brandName,
                null,
                null,
                "America/El_Salvador",
                "USD",
                OrganizationStatus.ACTIVE,
                NOW.minusSeconds(3_600),
                NOW,
                version);
    }
}
