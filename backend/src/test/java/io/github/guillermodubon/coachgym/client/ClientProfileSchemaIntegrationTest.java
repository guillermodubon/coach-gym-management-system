package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientProfileSchemaIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void migratedSchemaContainsClientHistoryAndPhotoMetadata() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'gym'
                  and table_name in ('client_status_history', 'client_photos')
                order by table_name
                """, String.class);

        assertThat(tables)
                .containsExactly("client_photos", "client_status_history");
    }

    @Test
    void photoMetadataContainsNoBinaryColumn() {
        List<String> binaryColumns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'client_photos'
                  and data_type = 'bytea'
                """, String.class);

        assertThat(binaryColumns).isEmpty();
    }

    @Test
    void statusHistoryHasAppendOnlyTriggers() {
        List<String> triggers = jdbcTemplate.queryForList("""
                select trigger_name
                from information_schema.triggers
                where event_object_schema = 'gym'
                  and event_object_table = 'client_status_history'
                order by trigger_name
                """, String.class);

        assertThat(triggers)
                .contains(
                        "trg_client_status_history_reject_delete",
                        "trg_client_status_history_reject_update");
    }

    @Test
    void photoMetadataAllowsOnlyOneRowPerClient() {
        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'gym'
                  and table_name = 'client_photos'
                  and constraint_type = 'UNIQUE'
                """, String.class);

        assertThat(constraints).contains("uq_client_photos_client");
    }
}
