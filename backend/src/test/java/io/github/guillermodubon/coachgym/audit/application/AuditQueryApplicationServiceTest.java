package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditEntryQuery;
import io.github.guillermodubon.coachgym.audit.AuditEntrySummary;
import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.AuditVisibilityScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuditQueryApplicationServiceTest {

    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Mock
    private AuditEntryQuery auditEntryQuery;

    @Mock
    private AuditQueryAuthorization authorization;

    @Test
    void nullListQueryUsesTheBoundedDefaultAndDelegatesOnce() {
        AuditEntryPage expected = AuditEntryPage.of(List.of(), 0, 25, 0);
        when(auditEntryQuery.findAll(
                AuditSearchQuery.defaults(), AuditVisibilityScope.organization()))
                .thenReturn(expected);
        AuditQueryApplicationService service = service();

        assertThat(service.findAll(null, ACTOR_ID)).isSameAs(expected);

        verify(auditEntryQuery).findAll(
                AuditSearchQuery.defaults(), AuditVisibilityScope.organization());
        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void validatedListQueryIsDelegatedWithoutChangingItsFilters() {
        AuditSearchQuery query = AuditSearchQuery.from(
                null,
                " ADMIN@EXAMPLE.TEST ",
                "CLIENT_REGISTERED",
                "CLIENT",
                null,
                null,
                null,
                null,
                null,
                1,
                10,
                "OCCURRED_AT",
                "ASC");
        AuditEntryPage expected = AuditEntryPage.of(List.of(), 1, 10, 0);
        when(auditEntryQuery.findAll(
                query, AuditVisibilityScope.organization())).thenReturn(expected);

        assertThat(service().findAll(query, ACTOR_ID)).isSameAs(expected);

        verify(auditEntryQuery).findAll(query, AuditVisibilityScope.organization());
        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void unauthorizedBranchFilterIsRejectedBeforeTheAuditQueryPortRuns() {
        UUID requestedBranch = UUID.randomUUID();
        AuditSearchQuery query = AuditSearchQuery.from(
                null, null, null, null, null, null, null, null,
                Set.of(requestedBranch), null, null, 0, 25, null, null);
        when(authorization.authorizeQuery(ACTOR_ID, Set.of(requestedBranch)))
                .thenThrow(new AccessDeniedException("Audit history is not available."));

        assertThatThrownBy(() -> service().findAll(query, ACTOR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void foundDetailIsReturnedAsTheAlreadySanitizedProjection() {
        AuditEntrySummary summary = summary();
        AuditEntryDetails expected = new AuditEntryDetails(
                summary,
                new AuditMetadataProjection(
                        java.util.Map.of("clientId", UUID.randomUUID()),
                        true));
        when(auditEntryQuery.findById(
                ENTRY_ID, AuditVisibilityScope.organization()))
                .thenReturn(Optional.of(expected));

        assertThat(service().findById(ENTRY_ID, ACTOR_ID)).isSameAs(expected);

        verify(auditEntryQuery).findById(ENTRY_ID, AuditVisibilityScope.organization());
        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void absentDetailBecomesSafeNotFoundException() {
        when(auditEntryQuery.findById(
                ENTRY_ID, AuditVisibilityScope.organization())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findById(ENTRY_ID, ACTOR_ID))
                .isInstanceOf(AuditEntryNotFoundException.class)
                .hasMessage("Audit entry was not found.")
                .satisfies(error -> assertThat(
                        ((AuditEntryNotFoundException) error).auditEntryId())
                        .isEqualTo(ENTRY_ID));
    }

    @Test
    void nullDetailIdFailsBeforeTouchingTheQueryPort() {
        AuditQueryApplicationService service = service();

        assertThatThrownBy(() -> service.findById(null, ACTOR_ID))
                .isInstanceOf(AuditQueryValidationException.class)
                .hasMessage("Audit entry id must be provided.");

        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void dataAccessFailureIsPropagatedWithoutChangingItsSafeContract() {
        AuditQueryDataAccessException failure = new AuditQueryDataAccessException(
                "Audit entries could not be read.", new IllegalStateException("hidden"));
        when(auditEntryQuery.findAll(
                AuditSearchQuery.defaults(), AuditVisibilityScope.organization()))
                .thenThrow(failure);

        assertThatThrownBy(() -> service().findAll(null, ACTOR_ID))
                .isSameAs(failure)
                .hasMessage("Audit entries could not be read.");
    }

    @Test
    void detailDataAccessFailureIsPropagatedWithoutExposingItsCause() {
        AuditQueryDataAccessException failure = new AuditQueryDataAccessException(
                "Audit entries could not be read.", new IllegalStateException("sql"));
        when(auditEntryQuery.findById(
                ENTRY_ID, AuditVisibilityScope.organization())).thenThrow(failure);

        assertThatThrownBy(() -> service().findById(ENTRY_ID, ACTOR_ID))
                .isSameAs(failure)
                .hasMessage("Audit entries could not be read.");
    }

    @Test
    void queryMethodsAreAdminOnlyAndReadOnly() throws Exception {
        for (var method : new java.lang.reflect.Method[] {
            AuditQueryApplicationService.class.getMethod(
                    "findAll", AuditSearchQuery.class, UUID.class),
            AuditQueryApplicationService.class.getMethod(
                    "findById", UUID.class, UUID.class)
        }) {
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasRole('ADMIN')");
            assertThat(method.getAnnotation(Transactional.class).readOnly())
                    .isTrue();
        }
    }

    private AuditQueryApplicationService service() {
        Mockito.lenient().when(authorization.authorizeQuery(ACTOR_ID, Set.of()))
                .thenReturn(AuditVisibilityScope.organization());
        Mockito.lenient().when(authorization.authorizeDetail(ACTOR_ID))
                .thenReturn(AuditVisibilityScope.organization());
        return new AuditQueryApplicationService(auditEntryQuery, authorization);
    }

    private static AuditEntrySummary summary() {
        return new AuditEntrySummary(
                ENTRY_ID,
                "CLIENT_REGISTERED",
                "CLIENT",
                UUID.randomUUID(),
                "CLI-001",
                UUID.randomUUID(),
                "admin@example.test",
                "Client registered",
                Instant.parse("2026-09-16T12:00:00Z"),
                UUID.randomUUID());
    }
}
