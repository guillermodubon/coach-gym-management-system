package io.github.guillermodubon.coachgym.equipment;

/** Safe failure for an unavailable equipment reporting read. */
public final class EquipmentReportingUnavailableException extends RuntimeException {

    public EquipmentReportingUnavailableException(Throwable cause) {
        super("Equipment reporting data could not be read.", cause);
    }
}
