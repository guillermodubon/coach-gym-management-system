package io.github.guillermodubon.coachgym.audit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed, privacy-reviewed CSV column order for audit exports. */
public enum AuditExportColumn {
    ENTRY_ID("entry_id"),
    OCCURRED_AT("occurred_at"),
    ACTOR_USER_ID("actor_user_id"),
    ACTOR_IDENTIFIER("actor_identifier"),
    ACTION_CODE("action_code"),
    RESOURCE_TYPE("resource_type"),
    RESOURCE_ID("resource_id"),
    RESOURCE_CODE("resource_code"),
    SUMMARY("summary"),
    CORRELATION_ID("correlation_id"),
    METADATA("metadata");

    private static final List<AuditExportColumn> ORDERED_COLUMNS =
            Collections.unmodifiableList(Arrays.asList(values()));

    private final String header;

    AuditExportColumn(String header) {
        this.header = header;
    }

    public String header() {
        return header;
    }

    public static List<AuditExportColumn> ordered() {
        return ORDERED_COLUMNS;
    }

    public static List<String> headers() {
        return ORDERED_COLUMNS.stream().map(AuditExportColumn::header).toList();
    }
}
