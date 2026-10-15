package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StaffAccountSecuritySchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V41__add_staff_security_version_and_bootstrap_password_change.sql");

    @Test
    void separatesSecurityFreshnessFromProfileVersionAndDefaultsExistingAccountsSafely()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("add column security_version bigint not null default 0")
                .contains("add column password_change_required boolean not null default false")
                .contains("ck_users_security_version_non_negative")
                .contains("staff security version must stay fixed or advance once")
                .contains("security-sensitive staff changes must advance the security version")
                .contains("before update on gym.users")
                .doesNotContain("drop table", "truncate", "update gym.users set password_hash");
    }

    @Test
    void makesAccountLifecycleTerminalAndAllowsOnlyApprovedReactivation()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("old.status = 'deactivated' and new.status <> 'deactivated'")
                .contains("old.status = 'active'")
                .contains("new.status not in ('active', 'suspended', 'deactivated')")
                .contains("old.status = 'suspended'")
                .contains("new.status not in ('suspended', 'active', 'deactivated')")
                .contains("drop trigger if exists trg_users_validate_staff_status_transition on gym.users")
                .contains("create trigger trg_users_validate_staff_status_transition");
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
