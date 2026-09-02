-- The v1 staff authorization model contains only ADMIN and RECEPTIONIST.
--
-- MAINTENANCE remains a valid equipment status and a business capability,
-- but it is no longer an internal staff role.
--
-- Existing staff accounts whose only assignment is MAINTENANCE are made
-- inactive before the assignment is removed. The migration never promotes
-- an account automatically to ADMIN or RECEPTIONIST.

DO
$$
    DECLARE
        maintenance_role_id UUID;
    BEGIN
        SELECT id
        INTO maintenance_role_id
        FROM gym.roles
        WHERE role_code = 'MAINTENANCE';

        IF maintenance_role_id IS NULL THEN
            RETURN;
        END IF;

        -- Disable active users who would be left without any role after removing
        -- MAINTENANCE. An explicit administrative decision is required before
        -- one of these accounts can receive RECEPTIONIST or ADMIN.
        UPDATE gym.users AS target_user
        SET status = 'INACTIVE',
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE target_user.status = 'ACTIVE'
          AND EXISTS (
            SELECT 1
            FROM gym.user_roles AS maintenance_assignment
            WHERE maintenance_assignment.user_id = target_user.id
              AND maintenance_assignment.role_id = maintenance_role_id
        )
          AND NOT EXISTS (
            SELECT 1
            FROM gym.user_roles AS other_assignment
            WHERE other_assignment.user_id = target_user.id
              AND other_assignment.role_id <> maintenance_role_id
        );

        -- Remove all assignments before removing the role. The FK also uses
        -- ON DELETE CASCADE, but the explicit delete makes the intent clear.
        DELETE FROM gym.user_roles
        WHERE role_id = maintenance_role_id;

        DELETE FROM gym.roles
        WHERE id = maintenance_role_id;
    END
$$;

-- Final-state guarantees for the v1 role model.
DO
$$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM gym.roles
            WHERE role_code = 'MAINTENANCE'
        ) THEN
            RAISE EXCEPTION
                'MAINTENANCE staff role still exists after migration';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM gym.user_roles AS user_role
                     JOIN gym.roles AS role
                          ON role.id = user_role.role_id
            WHERE role.role_code NOT IN ('ADMIN', 'RECEPTIONIST')
        ) THEN
            RAISE EXCEPTION
                'Unsupported staff role assignment exists after migration';
        END IF;
    END
$$;