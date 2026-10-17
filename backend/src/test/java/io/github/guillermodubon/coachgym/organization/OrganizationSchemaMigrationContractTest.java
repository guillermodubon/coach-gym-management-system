package io.github.guillermodubon.coachgym.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OrganizationSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V31__create_organizations_and_branches.sql");

    @Test
    void migrationCreatesCanonicalOrganizationAndBranchSchema() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.organizations")
                .contains("create table gym.gym_branches")
                .contains("uq_organizations_code unique (code)")
                .contains("uq_gym_branches_organization_code")
                .contains("uq_organizations_one_canonical")
                .contains("uq_gym_branches_one_initial_per_organization")
                .contains("fk_gym_branches_organization")
                .contains("on delete restrict")
                .contains("ck_organizations_status")
                .contains("ck_gym_branches_status")
                .contains("ck_organizations_currency_format")
                .contains("ck_organizations_version_non_negative")
                .contains("ck_gym_branches_version_non_negative");
    }

    @Test
    void migrationSeedsDeterministicCanonicalRecordsWithoutBroadScoping() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("'7b0bf7d5-5184-43d2-8f9a-200000000001'")
                .contains("'7b0bf7d5-5184-43d2-8f9a-200000000002'")
                .contains("'coach_gym'")
                .contains("'principal'")
                .contains("'america/el_salvador'")
                .contains("'usd'")
                .contains("'sv'")
                .doesNotContain("alter table gym.gym_settings")
                .doesNotContain("update gym.gym_settings")
                .doesNotContain("branch_id")
                .doesNotContain("tenant_id");
    }

    @Test
    void migrationProtectsLifecycleDeletionAndDoesNotStoreBusinessPayloads() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("reject_organization_branch_delete()")
                .contains("trg_organizations_reject_delete")
                .contains("trg_gym_branches_reject_delete")
                .doesNotContain("bytea")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("token");
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
