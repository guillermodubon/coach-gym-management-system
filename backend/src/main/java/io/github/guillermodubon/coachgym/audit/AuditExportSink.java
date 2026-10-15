package io.github.guillermodubon.coachgym.audit;

/** Technology-neutral callback for incrementally consuming safe export rows. */
@FunctionalInterface
public interface AuditExportSink {

    void accept(AuditExportRow row);
}
