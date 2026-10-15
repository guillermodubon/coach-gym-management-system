package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentCorrectionSchemaIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void finalSchemaContainsCorrectionConstraintsAndTriggers() {
        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'gym'
                  and table_name in (
                      'payment_status_history',
                      'payment_refunds')
                """, String.class);

        assertThat(constraints)
                .contains(
                        "ck_payment_status_history_allowed_transition",
                        "ck_payment_status_history_reason_required",
                        "uq_payment_refunds_payment_id",
                        "ck_payment_refunds_amount_positive",
                        "ck_payment_refunds_currency_format",
                        "ck_payment_refunds_reason_not_blank");

        List<String> triggers = jdbcTemplate.queryForList("""
                select trigger_name
                from information_schema.triggers
                where trigger_schema = 'gym'
                  and event_object_table in (
                      'payment_status_history',
                      'payment_refunds')
                """, String.class);

        assertThat(triggers)
                .contains(
                        "trg_payment_status_history_append_only",
                        "trg_payment_refunds_validate_full_refund",
                        "trg_payment_refunds_immutable");
    }

    @Test
    void correctionActorIsRequiredAndForeignKeysAreRestrictive() {
        Map<String, Object> actorColumn = jdbcTemplate.queryForMap("""
                select is_nullable
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'payment_status_history'
                  and column_name = 'changed_by_user_id'
                """);

        assertThat(actorColumn.get("is_nullable")).isEqualTo("NO");

        Integer restrictiveForeignKeys = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.referential_constraints
                where constraint_schema = 'gym'
                  and constraint_name in (
                      'fk_payment_status_history_payment',
                      'fk_payment_status_history_changed_by_user',
                      'fk_payment_refunds_payment',
                      'fk_payment_refunds_refunded_by_user')
                  and delete_rule = 'RESTRICT'
                """, Integer.class);

        assertThat(restrictiveForeignKeys).isEqualTo(4);
    }

    @Test
    void existingOperationalIndexesRemainAvailable() {
        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'gym'
                  and tablename in (
                      'payment_status_history',
                      'payment_refunds')
                """, String.class);

        assertThat(indexes)
                .contains(
                        "idx_payment_status_history_payment_occurred_at",
                        "idx_payment_refunds_refunded_at");
    }
}
