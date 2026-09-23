package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PaymentBranchOwnershipSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V34__add_payment_and_delivery_branch_ownership.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void addsImmutableOwnershipSnapshotsAndRestrictiveReferences() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("alter table gym.payments")
                .contains("add column registered_at_branch_id uuid")
                .contains("add column initiated_at_branch_id uuid")
                .contains("add column branch_id uuid")
                .contains("alter column registered_at_branch_id set not null")
                .contains("alter column initiated_at_branch_id set not null")
                .contains("alter column branch_id set not null")
                .contains("fk_payments_registered_at_branch")
                .contains("fk_payment_attempts_initiated_at_branch")
                .contains("fk_payment_receipts_branch")
                .contains("fk_email_deliveries_branch")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade");
    }

    @Test
    void backfillsThroughAuthoritativeRelationshipsWithoutRewritingHistory()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("organization.is_canonical")
                .contains("branch.is_initial_branch")
                .contains("period.registered_at_branch_id")
                .contains("membership.registered_at_branch_id")
                .contains("client.home_branch_id")
                .contains("join gym.payment_receipts as receipt")
                .contains("receipt.id = delivery.source_resource_id")
                .contains("drop trigger trg_payment_receipts_immutable on gym.payment_receipts")
                .contains("create trigger trg_payment_receipts_immutable")
                .contains("drop trigger trg_email_deliveries_validate_mutation on gym.email_deliveries")
                .contains("create trigger trg_email_deliveries_validate_mutation")
                .contains("drop trigger trg_email_deliveries_set_updated_at on gym.email_deliveries")
                .contains("create trigger trg_email_deliveries_set_updated_at")
                .contains("exactly one active canonical initial branch")
                .contains("payment and delivery ownership backfill left records without a branch")
                .doesNotContain("delete from gym.payments")
                .doesNotContain("delete from gym.payment_attempts")
                .doesNotContain("delete from gym.payment_receipts")
                .doesNotContain("delete from gym.email_deliveries");
    }

    @Test
    void providesScopedIndexesAndRejectsOwnershipMutation() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("idx_payments_branch_status_paid_at")
                .contains("idx_payment_attempts_branch_status_created_at")
                 .contains("idx_payment_receipts_branch_payment")
                 .contains("idx_email_deliveries_branch_status_updated_at")
                 .contains("trg_payments_reject_branch_mutation")
                 .contains("trg_payment_attempts_reject_branch_mutation")
                 .contains("trg_email_deliveries_reject_branch_mutation")
                .contains("immutable branch where the payment registration occurred")
                .contains("immutable branch snapshot inherited from the source payment")
                .contains("immutable branch snapshot inherited from the delivery source");
    }

    @Test
    void migrationIsTheUniqueNextVersion() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(
                            path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(32, 33, 34, 35, 36);
            assertThat(versions).isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(versions.get(versions.size() - 1)).isEqualTo(36);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
