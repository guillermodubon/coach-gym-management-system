package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StaffInvitationSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V38__add_staff_invitations_and_password_recovery.sql");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void migrationCreatesFingerprintOnlyInvitationAndRecoveryTables() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.staff_invitations")
                .contains("create table gym.staff_invitation_branches")
                .contains("create table gym.staff_password_recovery_tokens")
                .contains("token_fingerprint varchar(64) not null")
                .contains("token_scheme varchar(64) not null")
                .doesNotContain("raw_token", "token_value", "token varchar", "token text", "plaintext_token");
    }

    @Test
    void schemaEnforcesApprovedRolesScopesLifecyclesAndOwnership() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("ck_staff_invitations_role_scope")
                .contains("proposed_role = 'admin' and proposed_scope in ('organization', 'branch')")
                .contains("proposed_role = 'receptionist' and proposed_scope = 'branch'")
                .contains("uq_staff_invitations_pending_email")
                .contains("uq_staff_invitations_token_fingerprint")
                .contains("uq_staff_password_recovery_pending_user")
                .contains("uq_staff_password_recovery_token_fingerprint")
                .contains("idx_staff_invitations_organization_created_at")
                .contains("idx_staff_invitations_pending_expiration")
                .contains("idx_staff_invitation_branches_branch_invitation")
                .contains("idx_staff_password_recovery_pending_expiration")
                .contains("on delete restrict")
                .contains("trg_staff_invitations_validate_mutation")
                .contains("accepted_at >= last_sent_at")
                .contains("expired_at >= expires_at")
                .contains("revoked_at >= last_sent_at")
                .contains("trg_staff_invitation_branches_validate_mutation")
                .contains("used_at >= requested_at and used_at < expires_at")
                .contains("trg_staff_password_recovery_validate_mutation")
                .contains("trg_staff_invitations_branch_cardinality")
                .contains("trg_staff_invitation_branches_cardinality")
                .contains("trg_users_validate_staff_status_transition");
    }

    @Test
    void invitationAbuseControlsPersistOnlyMinimalAttemptMetadata() throws Exception {
        String sql = Files.readString(Path.of(
                        "src/main/resources/db/migration/"
                                + "V39__add_staff_invitation_delivery_and_reauthentication_limits.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();

        assertThat(sql)
                .contains("create table gym.staff_invitation_delivery_attempts")
                .contains("invitation_version bigint not null")
                .contains("outcome in ('reserved', 'sent', 'failed', 'ambiguous')")
                .contains("unique (invitation_id, invitation_version)")
                .contains("fk_staff_invitation_delivery_attempt_invitation")
                .contains("on delete restrict")
                .contains("idx_staff_invitations_inviter_created_at")
                .contains("create table gym.staff_admin_reauthentication_failures")
                .contains("idx_staff_admin_reauthentication_failures_user_attempted")
                .doesNotContain("recipient varchar", "email_body", "raw_token", "token_value", "password_hash");
    }

    @Test
    void activationDeliveryOutboxStoresOnlyBoundedRetryMetadata() throws Exception {
        String sql = Files.readString(Path.of(
                        "src/main/resources/db/migration/"
                                + "V40__add_staff_account_activation_delivery_outbox.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();

        assertThat(sql)
                .contains("create table gym.staff_account_activation_deliveries")
                .contains("unique (invitation_id)")
                .contains("attempt_count between 0 and 5")
                .contains("status in ('pending', 'sending', 'sent', 'failed', 'ambiguous', 'exhausted')")
                .contains("next_attempt_at")
                .contains("lease_expires_at")
                .contains("fk_staff_account_activation_delivery_invitation")
                .contains("on delete restrict")
                .contains("idx_staff_account_activation_delivery_retry")
                .contains("trg_staff_account_activation_delivery_validate_mutation")
                .doesNotContain(
                        "email_body", "message_body", "mime_payload", "attachment_bytes",
                        "password_hash", "raw_token", "token_fingerprint", "token_hash",
                        "tokenized_url", "oauth_credential", "provider_response_body",
                        "session_id");
    }

    @Test
    void migrationChainIsUniqueAndContiguousThroughV43() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .map(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(37, 38, 39, 40, 41, 42, 43);
            assertThat(versions)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 43)
                            .boxed().toList());
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
