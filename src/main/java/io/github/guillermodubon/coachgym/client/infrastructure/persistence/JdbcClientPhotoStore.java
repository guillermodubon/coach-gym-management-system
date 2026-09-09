package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoNotFoundException;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoRecord;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStore;
import io.github.guillermodubon.coachgym.client.application.ClientProfileDataAccessException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientPhotoStore implements ClientPhotoStore {

    private final JdbcClient jdbcClient;

    JdbcClientPhotoStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean clientExists(UUID clientId) {
        return Boolean.TRUE.equals(jdbcClient.sql(
                        "select exists(select 1 from gym.clients where id = :clientId)")
                .param("clientId", clientId)
                .query(Boolean.class)
                .single());
    }

    @Override
    public Optional<ClientPhotoRecord> findByClientId(UUID clientId) {
        try {
            return jdbcClient.sql("""
                            select id, storage_key, content_type, size_bytes,
                                   checksum_sha256, updated_at, version
                            from gym.client_photos
                            where client_id = :clientId
                            """)
                    .param("clientId", clientId)
                    .query((rs, row) -> new ClientPhotoRecord(
                            new ClientPhotoDetails(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("content_type"),
                                    rs.getLong("size_bytes"),
                                    rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                                    rs.getLong("version")),
                            rs.getString("storage_key"),
                            rs.getString("checksum_sha256")))
                    .optional();
        } catch (DataAccessException exception) {
            throw new ClientProfileDataAccessException(
                    "Client photo metadata could not be read.", exception);
        }
    }

    @Override
    public ClientPhotoDetails save(
            UUID clientId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            AuthenticatedActor actor,
            Instant occurredAt) {
        UUID id = UUID.randomUUID();
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        try {
            jdbcClient.sql("""
                            insert into gym.client_photos
                                (id, client_id, storage_key, content_type, size_bytes,
                                 checksum_sha256, created_at, updated_at,
                                 created_by_user_id, updated_by_user_id, version)
                            values
                                (:id, :clientId, :storageKey, :contentType, :sizeBytes,
                                 :checksum, :timestamp, :timestamp,
                                 :actorId, :actorId, 0)
                            on conflict (client_id) do update set
                                storage_key = excluded.storage_key,
                                content_type = excluded.content_type,
                                size_bytes = excluded.size_bytes,
                                checksum_sha256 = excluded.checksum_sha256,
                                updated_at = excluded.updated_at,
                                updated_by_user_id = excluded.updated_by_user_id,
                                version = gym.client_photos.version + 1
                            returning id, content_type, size_bytes, updated_at, version
                            """)
                    .param("id", id)
                    .param("clientId", clientId)
                    .param("storageKey", storageKey)
                    .param("contentType", contentType)
                    .param("sizeBytes", sizeBytes)
                    .param("checksum", checksumSha256)
                    .param("timestamp", timestamp)
                    .param("actorId", actor.id())
                    .query((rs, row) -> new ClientPhotoDetails(
                            rs.getObject("id", UUID.class),
                            rs.getString("content_type"),
                            rs.getLong("size_bytes"),
                            rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                            rs.getLong("version")))
                    .single();
            return findByClientId(clientId).orElseThrow().details();
        } catch (DataAccessException exception) {
            throw new ClientProfileDataAccessException(
                    "Client photo metadata could not be saved.", exception);
        }
    }

    @Override
    public String delete(UUID clientId, long expectedVersion) {
        ClientPhotoRecord current = findByClientId(clientId)
                .orElseThrow(() -> new ClientPhotoNotFoundException(
                        "Client photo was not found."));
        int affected = jdbcClient.sql("""
                        delete from gym.client_photos
                        where client_id = :clientId
                          and version = :expectedVersion
                        """)
                .param("clientId", clientId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (affected != 1) {
            throw new ClientProfileDataAccessException(
                    "Client photo version conflict.", null);
        }
        return current.storageKey();
    }
}
