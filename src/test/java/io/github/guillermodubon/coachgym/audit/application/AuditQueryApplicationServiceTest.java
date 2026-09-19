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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuditQueryApplicationServiceTest {

    private static final UUID ENTRY_ID = UUID.randomUUID();

    @Mock
    private AuditEntryQuery auditEntryQuery;

    @Test
    void nullListQueryUsesTheBoundedDefaultAndDelegatesOnce() {
        AuditEntryPage expected = AuditEntryPage.of(List.of(), 0, 25, 0);
        when(auditEntryQuery.findAll(AuditSearchQuery.defaults()))
                .thenReturn(expected);
        AuditQueryApplicationService service = service();

        assertThat(service.findAll(null)).isSameAs(expected);

        verify(auditEntryQuery).findAll(AuditSearchQuery.defaults());
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
        when(auditEntryQuery.findAll(query)).thenReturn(expected);

        assertThat(service().findAll(query)).isSameAs(expected);

        verify(auditEntryQuery).findAll(query);
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
        when(auditEntryQuery.findById(ENTRY_ID)).thenReturn(Optional.of(expected));

        assertThat(service().findById(ENTRY_ID)).isSameAs(expected);

        verify(auditEntryQuery).findById(ENTRY_ID);
        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void absentDetailBecomesSafeNotFoundException() {
        when(auditEntryQuery.findById(ENTRY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findById(ENTRY_ID))
                .isInstanceOf(AuditEntryNotFoundException.class)
                .hasMessage("Audit entry was not found.")
                .satisfies(error -> assertThat(
                        ((AuditEntryNotFoundException) error).auditEntryId())
                        .isEqualTo(ENTRY_ID));
    }

    @Test
    void nullDetailIdFailsBeforeTouchingTheQueryPort() {
        AuditQueryApplicationService service = service();

        assertThatThrownBy(() -> service.findById(null))
                .isInstanceOf(AuditQueryValidationException.class)
                .hasMessage("Audit entry id must be provided.");

        verifyNoMoreInteractions(auditEntryQuery);
    }

    @Test
    void dataAccessFailureIsPropagatedWithoutChangingItsSafeContract() {
        AuditQueryDataAccessException failure = new AuditQueryDataAccessException(
                "Audit entries could not be read.", new IllegalStateException("hidden"));
        when(auditEntryQuery.findAll(AuditSearchQuery.defaults()))
                .thenThrow(failure);

        assertThatThrownBy(() -> service().findAll(null))
                .isSameAs(failure)
                .hasMessage("Audit entries could not be read.");
    }

    @Test
    void detailDataAccessFailureIsPropagatedWithoutExposingItsCause() {
        AuditQueryDataAccessException failure = new AuditQueryDataAccessException(
                "Audit entries could not be read.", new IllegalStateException("sql"));
        when(auditEntryQuery.findById(ENTRY_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service().findById(ENTRY_ID))
                .isSameAs(failure)
                .hasMessage("Audit entries could not be read.");
    }

    @Test
    void queryMethodsAreAdminOnlyAndReadOnly() throws Exception {
        for (var method : new java.lang.reflect.Method[] {
            AuditQueryApplicationService.class.getMethod(
                    "findAll", AuditSearchQuery.class),
            AuditQueryApplicationService.class.getMethod(
                    "findById", UUID.class)
        }) {
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasRole('ADMIN')");
            assertThat(method.getAnnotation(Transactional.class).readOnly())
                    .isTrue();
        }
    }

    private AuditQueryApplicationService service() {
        return new AuditQueryApplicationService(auditEntryQuery);
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
