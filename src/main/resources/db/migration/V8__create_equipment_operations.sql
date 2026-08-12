CREATE TABLE gym.equipment_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_equipment_categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_equipment_categories_version_non_negative CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_equipment_categories_name_ci
    ON gym.equipment_categories (lower(name));

CREATE TABLE gym.equipment (
    id UUID PRIMARY KEY,
    equipment_number BIGINT GENERATED ALWAYS AS IDENTITY,
    equipment_code VARCHAR(32) GENERATED ALWAYS AS (
        'EQP-' || lpad(equipment_number::TEXT, 6, '0')
    ) STORED,
    equipment_category_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    manufacturer VARCHAR(120),
    model VARCHAR(120),
    serial_number VARCHAR(120),
    location VARCHAR(160),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    purchased_on DATE,
    notes TEXT,
    retired_at TIMESTAMPTZ,
    retired_by_user_id UUID,
    retirement_reason TEXT,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_equipment_equipment_number UNIQUE (equipment_number),
    CONSTRAINT uq_equipment_equipment_code UNIQUE (equipment_code),
    CONSTRAINT ck_equipment_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_equipment_status
        CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED')),
    CONSTRAINT ck_equipment_retirement_state CHECK (
        (status <> 'RETIRED'
            AND retired_at IS NULL
            AND retired_by_user_id IS NULL
            AND retirement_reason IS NULL)
        OR
        (status = 'RETIRED'
            AND retired_at IS NOT NULL
            AND retired_by_user_id IS NOT NULL
            AND retirement_reason IS NOT NULL
            AND btrim(retirement_reason) <> '')
    ),
    CONSTRAINT ck_equipment_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_equipment_category
        FOREIGN KEY (equipment_category_id)
        REFERENCES gym.equipment_categories (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_equipment_retired_by_user
        FOREIGN KEY (retired_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_equipment_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_equipment_updated_by_user
        FOREIGN KEY (updated_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_equipment_serial_number_ci
    ON gym.equipment (lower(serial_number))
    WHERE serial_number IS NOT NULL;
CREATE INDEX idx_equipment_category_status
    ON gym.equipment (equipment_category_id, status);
CREATE INDEX idx_equipment_available
    ON gym.equipment (name)
    WHERE status = 'AVAILABLE';

CREATE TABLE gym.equipment_status_history (
    id UUID PRIMARY KEY,
    equipment_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_user_id UUID,
    CONSTRAINT ck_equipment_status_history_previous_status
        CHECK (previous_status IS NULL
            OR previous_status IN ('AVAILABLE', 'MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED')),
    CONSTRAINT ck_equipment_status_history_new_status
        CHECK (new_status IN ('AVAILABLE', 'MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED')),
    CONSTRAINT ck_equipment_status_history_state_change
        CHECK (previous_status IS NULL OR previous_status <> new_status),
    CONSTRAINT fk_equipment_status_history_equipment
        FOREIGN KEY (equipment_id)
        REFERENCES gym.equipment (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_equipment_status_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_equipment_status_history_equipment_occurred_at
    ON gym.equipment_status_history (equipment_id, occurred_at DESC);

CREATE TABLE gym.incidents (
    id UUID PRIMARY KEY,
    incident_number BIGINT GENERATED ALWAYS AS IDENTITY,
    incident_code VARCHAR(32) GENERATED ALWAYS AS (
        'INC-' || lpad(incident_number::TEXT, 6, '0')
    ) STORED,
    equipment_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(10) NOT NULL,
    description TEXT NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reported_by_user_id UUID NOT NULL,
    assigned_to_user_id UUID,
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id UUID,
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_incidents_incident_number UNIQUE (incident_number),
    CONSTRAINT uq_incidents_incident_code UNIQUE (incident_code),
    CONSTRAINT ck_incidents_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT ck_incidents_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT ck_incidents_resolution_state CHECK (
        (status <> 'RESOLVED'
            AND resolved_at IS NULL
            AND resolved_by_user_id IS NULL
            AND resolution_notes IS NULL)
        OR
        (status = 'RESOLVED'
            AND resolved_at IS NOT NULL
            AND resolved_by_user_id IS NOT NULL
            AND resolution_notes IS NOT NULL
            AND btrim(resolution_notes) <> '')
    ),
    CONSTRAINT ck_incidents_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_incidents_equipment
        FOREIGN KEY (equipment_id)
        REFERENCES gym.equipment (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_reported_by_user
        FOREIGN KEY (reported_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_incidents_assigned_to_user
        FOREIGN KEY (assigned_to_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_incidents_resolved_by_user
        FOREIGN KEY (resolved_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_incidents_equipment_status ON gym.incidents (equipment_id, status);
CREATE INDEX idx_incidents_open_priority
    ON gym.incidents (priority, reported_at DESC)
    WHERE status <> 'RESOLVED';

CREATE TABLE gym.incident_status_history (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_user_id UUID,
    CONSTRAINT ck_incident_status_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT ck_incident_status_history_new_status
        CHECK (new_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT ck_incident_status_history_state_change
        CHECK (previous_status IS NULL OR previous_status <> new_status),
    CONSTRAINT fk_incident_status_history_incident
        FOREIGN KEY (incident_id)
        REFERENCES gym.incidents (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_incident_status_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_incident_status_history_incident_occurred_at
    ON gym.incident_status_history (incident_id, occurred_at DESC);

CREATE TABLE gym.maintenances (
    id UUID PRIMARY KEY,
    maintenance_number BIGINT GENERATED ALWAYS AS IDENTITY,
    maintenance_code VARCHAR(32) GENERATED ALWAYS AS (
        'MNT-' || lpad(maintenance_number::TEXT, 6, '0')
    ) STORED,
    equipment_id UUID NOT NULL,
    incident_id UUID,
    maintenance_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_on DATE NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    provider_name VARCHAR(160),
    technician_name VARCHAR(160),
    estimated_cost NUMERIC(12, 2),
    actual_cost NUMERIC(12, 2),
    currency CHAR(3) NOT NULL,
    actions_taken TEXT,
    notes TEXT,
    created_by_user_id UUID NOT NULL,
    assigned_to_user_id UUID,
    completed_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_maintenances_maintenance_number UNIQUE (maintenance_number),
    CONSTRAINT uq_maintenances_maintenance_code UNIQUE (maintenance_code),
    CONSTRAINT ck_maintenances_type CHECK (maintenance_type IN ('PREVENTIVE', 'CORRECTIVE')),
    CONSTRAINT ck_maintenances_status
        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_maintenances_estimated_cost_non_negative
        CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CONSTRAINT ck_maintenances_actual_cost_non_negative
        CHECK (actual_cost IS NULL OR actual_cost >= 0),
    CONSTRAINT ck_maintenances_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_maintenances_completion_state CHECK (
        (status <> 'COMPLETED'
            AND completed_at IS NULL
            AND completed_by_user_id IS NULL)
        OR
        (status = 'COMPLETED'
            AND completed_at IS NOT NULL
            AND completed_by_user_id IS NOT NULL
            AND actions_taken IS NOT NULL
            AND btrim(actions_taken) <> '')
    ),
    CONSTRAINT ck_maintenances_version_non_negative CHECK (version >= 0),
    CONSTRAINT fk_maintenances_equipment
        FOREIGN KEY (equipment_id)
        REFERENCES gym.equipment (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_maintenances_incident
        FOREIGN KEY (incident_id)
        REFERENCES gym.incidents (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_maintenances_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_maintenances_assigned_to_user
        FOREIGN KEY (assigned_to_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_maintenances_completed_by_user
        FOREIGN KEY (completed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_maintenances_equipment_scheduled_on
    ON gym.maintenances (equipment_id, scheduled_on);
CREATE INDEX idx_maintenances_open_scheduled_on
    ON gym.maintenances (scheduled_on)
    WHERE status IN ('SCHEDULED', 'IN_PROGRESS');
CREATE INDEX idx_maintenances_incident_id
    ON gym.maintenances (incident_id)
    WHERE incident_id IS NOT NULL;

CREATE TABLE gym.maintenance_status_history (
    id UUID PRIMARY KEY,
    maintenance_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_user_id UUID,
    CONSTRAINT ck_maintenance_status_history_previous_status
        CHECK (previous_status IS NULL
            OR previous_status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_maintenance_status_history_new_status
        CHECK (new_status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_maintenance_status_history_state_change
        CHECK (previous_status IS NULL OR previous_status <> new_status),
    CONSTRAINT fk_maintenance_status_history_maintenance
        FOREIGN KEY (maintenance_id)
        REFERENCES gym.maintenances (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_maintenance_status_history_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES gym.users (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_maintenance_status_history_maintenance_occurred_at
    ON gym.maintenance_status_history (maintenance_id, occurred_at DESC);

CREATE TRIGGER trg_equipment_categories_set_updated_at
BEFORE UPDATE ON gym.equipment_categories
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_equipment_set_updated_at
BEFORE UPDATE ON gym.equipment
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_incidents_set_updated_at
BEFORE UPDATE ON gym.incidents
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();

CREATE TRIGGER trg_maintenances_set_updated_at
BEFORE UPDATE ON gym.maintenances
FOR EACH ROW
EXECUTE FUNCTION gym.set_updated_at();
