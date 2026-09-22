package io.github.guillermodubon.coachgym.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class OrganizationSchemaIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Test
    void currentFlywayChainContainsCanonicalSeedAndRequiredIndexes() {
        Map<String, Object> organization = jdbcTemplate.queryForMap("""
                select id, code, legal_name, brand_name, default_timezone,
                       default_currency, status, is_canonical, version
                from gym.organizations
                where code = 'COACH_GYM'
                """);
        assertThat(organization)
                .containsEntry("id", ORGANIZATION_ID)
                .containsEntry("code", "COACH_GYM")
                .containsEntry("legal_name", "Coach Gym")
                .containsEntry("brand_name", "Coach Gym")
                .containsEntry("default_timezone", "America/El_Salvador")
                .containsEntry("default_currency", "USD")
                .containsEntry("status", "ACTIVE")
                .containsEntry("is_canonical", true)
                .containsEntry("version", 0L);

        Map<String, Object> branch = jdbcTemplate.queryForMap("""
                select id, organization_id, code, name, country_code,
                       timezone, status, is_initial_branch, version
                from gym.gym_branches
                where code = 'PRINCIPAL'
                """);
        assertThat(branch)
                .containsEntry("id", INITIAL_BRANCH_ID)
                .containsEntry("organization_id", ORGANIZATION_ID)
                .containsEntry("code", "PRINCIPAL")
                .containsEntry("name", "Coach Gym Principal")
                .containsEntry("country_code", "SV")
                .containsEntry("timezone", "America/El_Salvador")
                .containsEntry("status", "ACTIVE")
                .containsEntry("is_initial_branch", true)
                .containsEntry("version", 0L);

        Integer migrationCount = jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '32'
                  and success = true
                """, Integer.class);
        assertThat(migrationCount).isEqualTo(1);

        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'gym_branches'
                """, String.class);
        assertThat(indexes).contains(
                "uq_gym_branches_one_initial_per_organization",
                "idx_gym_branches_organization_status_code");
    }

    @Test
    void enforcesUniqueCodesInitialBranchAndRestrictiveOrganizationRelationship() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'PRINCIPAL', 'Duplicate Principal',
                        'America/El_Salvador', 'ACTIVE', 0)
                """, UUID.randomUUID(), ORGANIZATION_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status,
                     is_initial_branch, version)
                values (?, ?, 'CENTRO', 'Duplicate Initial',
                        'America/El_Salvador', 'ACTIVE', true, 0)
                """, UUID.randomUUID(), ORGANIZATION_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.organizations where id = ?", ORGANIZATION_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.gym_branches where id = ?", INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidBranchMetadataAndUnknownOrganization() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'INVALID CODE', 'Branch',
                        'America/El_Salvador', 'ACTIVE', 0)
                """, UUID.randomUUID(), ORGANIZATION_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'NORTE', ' ',
                        'America/El_Salvador', 'ACTIVE', 0)
                """, UUID.randomUUID(), ORGANIZATION_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'NORTE', 'North',
                        'America/El_Salvador', 'ACTIVE', -1)
                """, UUID.randomUUID(), ORGANIZATION_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'NORTE', 'North',
                        'America/El_Salvador', 'ACTIVE', 0)
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void preservesExistingSettingsAndDoesNotAddPrematureBranchColumns() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.gym_settings where id = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select display_name from gym.gym_settings where id = 1",
                String.class)).isEqualTo("Coach Gym");

        List<String> branchColumns = jdbcTemplate.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name in (
                      'clients', 'memberships', 'payments', 'access_records',
                      'equipment', 'incidents', 'maintenances', 'audit_entries')
                  and column_name in ('branch_id', 'organization_id')
                """, String.class);
        assertThat(branchColumns).isEmpty();
    }
}
