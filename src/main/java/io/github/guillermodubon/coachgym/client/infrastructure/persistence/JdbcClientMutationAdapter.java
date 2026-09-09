package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.application.ClientMutationStore;
import io.github.guillermodubon.coachgym.client.application.ClientStateConflictException;
import io.github.guillermodubon.coachgym.client.application.ClientValidationException;
import io.github.guillermodubon.coachgym.client.application.UpdateClientCommand;
import io.github.guillermodubon.coachgym.client.application.UpdateEmergencyContactCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientMutationAdapter implements ClientMutationStore {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcClientMutationAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void update(
            UUID clientId,
            UpdateClientCommand command,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MapSqlParameterSource parameters = common(clientId, actor, occurredAt)
                .addValue("firstName", command.firstName())
                .addValue("lastName", command.lastName())
                .addValue("email", command.email())
                .addValue("phone", command.phone())
                .addValue("dateOfBirth", command.dateOfBirth())
                .addValue("expectedVersion", command.expectedVersion());
        try {
            int updated = jdbc.update("""
                    update gym.clients
                    set first_name = :firstName,
                        last_name = :lastName,
                        email = :email,
                        phone = :phone,
                        date_of_birth = :dateOfBirth,
                        updated_by_user_id = :actorId,
                        updated_at = :occurredAt,
                        version = version + 1
                    where id = :clientId
                      and version = :expectedVersion
                    """, parameters);
            requireUpdated(clientId, updated);
            replaceEmergencyContact(clientId, command.emergencyContact(), occurredAt);
        } catch (DataIntegrityViolationException exception) {
            throw new ClientValidationException(
                    "Client profile conflicts with existing or invalid data.");
        }
    }

    @Override
    public void changeStatus(
            UUID clientId,
            ClientStatus expectedCurrentStatus,
            ClientStatus requestedStatus,
            String reason,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MapSqlParameterSource parameters = common(clientId, actor, occurredAt)
                .addValue("expectedStatus", expectedCurrentStatus.name())
                .addValue("newStatus", requestedStatus.name())
                .addValue("reason", reason)
                .addValue("expectedVersion", expectedVersion)
                .addValue("historyId", UUID.randomUUID());

        int updated = jdbc.update("""
                update gym.clients
                set status = :newStatus,
                    deactivated_at = case when :newStatus = 'INACTIVE'
                        then :occurredAt else null end,
                    deactivated_by_user_id = case when :newStatus = 'INACTIVE'
                        then :actorId else null end,
                    deactivation_reason = case when :newStatus = 'INACTIVE'
                        then :reason else null end,
                    updated_by_user_id = :actorId,
                    updated_at = :occurredAt,
                    version = version + 1
                where id = :clientId
                  and status = :expectedStatus
                  and version = :expectedVersion
                """, parameters);
        requireUpdated(clientId, updated);

        jdbc.update("""
                insert into gym.client_status_history
                    (id, client_id, previous_status, new_status,
                     reason, occurred_at, changed_by_user_id)
                values
                    (:historyId, :clientId, :expectedStatus, :newStatus,
                     :reason, :occurredAt, :actorId)
                """, parameters);
    }

    private void replaceEmergencyContact(
            UUID clientId,
            UpdateEmergencyContactCommand contact,
            Instant occurredAt) {
        jdbc.update(
                "delete from gym.emergency_contacts where client_id = :clientId",
                Map.of("clientId", clientId));
        if (contact == null) {
            return;
        }
        jdbc.update("""
                insert into gym.emergency_contacts
                    (id, client_id, full_name, relationship, phone, is_primary,
                     created_at, updated_at, version)
                values
                    (:id, :clientId, :fullName, :relationship, :phone, true,
                     :occurredAt, :occurredAt, 0)
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("clientId", clientId)
                        .addValue("fullName", contact.fullName())
                        .addValue("relationship", contact.relationship())
                        .addValue("phone", contact.phone())
                        .addValue("occurredAt", timestamp(occurredAt)));
    }

    private static MapSqlParameterSource common(
            UUID clientId,
            AuthenticatedActor actor,
            Instant occurredAt) {
        return new MapSqlParameterSource()
                .addValue("clientId", clientId)
                .addValue("actorId", actor.id())
                .addValue("occurredAt", timestamp(occurredAt));
    }

    private static OffsetDateTime timestamp(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static void requireUpdated(UUID clientId, int updated) {
        if (updated != 1) {
            throw new ClientStateConflictException(
                    clientId,
                    null,
                    null);
        }
    }
}
