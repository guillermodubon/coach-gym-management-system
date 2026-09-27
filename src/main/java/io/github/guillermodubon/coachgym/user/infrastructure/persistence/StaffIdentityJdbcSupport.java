package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataAccessException;

final class StaffIdentityJdbcSupport {

    private StaffIdentityJdbcSupport() {
    }

    static OffsetDateTime databaseTime(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Lifecycle time is required.");
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    static StaffIdentityDataAccessException safeDataAccess(DataAccessException ignored) {
        return new StaffIdentityDataAccessException(
                "Staff identity data could not be accessed.");
    }
}
