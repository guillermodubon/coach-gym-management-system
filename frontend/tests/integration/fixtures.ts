import { createHash, randomBytes, randomUUID } from "node:crypto";
import { hash } from "bcryptjs";
import { Pool } from "pg";
import { expect, test as base } from "@playwright/test";

export { expect };

export type StaffRole = "ADMIN" | "RECEPTIONIST";
export type StaffScope = "ORGANIZATION" | "BRANCH";

export interface StaffFixtureOptions {
  role: StaffRole;
  scope: StaffScope;
  branchAssignments?: 0 | 1 | 2;
  passwordChangeRequired?: boolean;
  firstName?: string;
  lastName?: string;
}

export interface StaffFixture {
  id: string;
  username: string;
  email: string;
  password: string;
  fullName: string;
  branchIds: string[];
}

export interface InvitationFixtureOptions {
  inviterUserId: string;
  role: StaffRole;
  scope: StaffScope;
  branchIds: string[];
}

export interface OneTimeTokenFixture {
  id: string;
  /** Raw token remains in the Node process memory and is never persisted. */
  token: string;
}

interface WorkerFixtures {
  database: Pool;
}

function requiredEnvironmentValue(name: string): string {
  const value = process.env[name];
  if (!value)
    throw new Error("The private integration database settings are missing.");
  return value;
}

const initialBranchId = () =>
  requiredEnvironmentValue("CG_E2E_INITIAL_BRANCH_ID");
const secondBranchId = () =>
  requiredEnvironmentValue("CG_E2E_SECOND_BRANCH_ID");

export const test = base.extend<object, WorkerFixtures>({
  database: [
    // Playwright requires destructuring even when a fixture has no dependencies.
    // eslint-disable-next-line no-empty-pattern
    async ({}, use) => {
      if (process.env.CG_E2E_HARNESS !== "1") {
        throw new Error(
          "Database fixtures require the owned integration harness.",
        );
      }
      const port = Number(requiredEnvironmentValue("CG_E2E_DB_PORT"));
      if (!Number.isSafeInteger(port) || port < 1 || port > 65_535) {
        throw new Error("The private integration database port is invalid.");
      }
      const pool = new Pool({
        host: requiredEnvironmentValue("CG_E2E_DB_HOST"),
        port,
        database: requiredEnvironmentValue("CG_E2E_DB_NAME"),
        user: requiredEnvironmentValue("CG_E2E_DB_USER"),
        password: requiredEnvironmentValue("CG_E2E_DB_PASSWORD"),
        application_name: "coach-gym-f01-e2e-fixture",
        connectionTimeoutMillis: 5_000,
        max: 2,
      });
      try {
        await pool.query("SELECT 1");
        await use(pool);
      } finally {
        await pool.end();
      }
    },
    { scope: "worker" },
  ],
});

export async function createStaffFixture(
  database: Pool,
  options: StaffFixtureOptions,
): Promise<StaffFixture> {
  if (
    (options.role === "RECEPTIONIST" && options.scope !== "BRANCH") ||
    (options.scope === "ORGANIZATION" && (options.branchAssignments ?? 0) !== 0)
  ) {
    throw new Error(
      "The requested synthetic role and scope combination is unsupported.",
    );
  }

  const branchAssignments = options.branchAssignments ?? 0;
  const branchIds =
    branchAssignments === 0
      ? []
      : branchAssignments === 1
        ? [initialBranchId()]
        : [initialBranchId(), secondBranchId()];
  const id = randomUUID();
  const username = `f01-${id}`;
  const email = `${username}@example.test`;
  const password = randomBytes(24).toString("base64url");
  const passwordHash = `{bcrypt}${await hash(password, 10)}`;
  const firstName = options.firstName ?? "Operador de integración";
  const lastName = options.lastName ?? "Sintético";
  const fullName = `${firstName} ${lastName}`;
  const client = await database.connect();

  try {
    await client.query("BEGIN");
    const role = await client.query(
      `SELECT id
         FROM gym.roles
        WHERE role_code = $1
          AND is_active = TRUE`,
      [options.role],
    );
    if (role.rowCount !== 1) {
      throw new Error(
        "The fixture database is missing a supported staff role.",
      );
    }

    await client.query(
      `INSERT INTO gym.users
          (id, username, email, password_hash, first_name, last_name, status,
           version, security_version, password_change_required)
       VALUES ($1, $2, $3, $4, $5, $6, 'ACTIVE', 0, 0, $7)`,
      [
        id,
        username,
        email,
        passwordHash,
        firstName,
        lastName,
        options.passwordChangeRequired ?? false,
      ],
    );
    await client.query(
      "INSERT INTO gym.user_roles (user_id, role_id) VALUES ($1, $2)",
      [id, role.rows[0].id],
    );
    await client.query(
      `INSERT INTO gym.staff_scopes (user_id, scope_type, version)
       VALUES ($1, $2, 0)`,
      [id, options.scope],
    );
    for (const branchId of branchIds) {
      await client.query(
        `INSERT INTO gym.staff_branch_assignments
            (id, user_id, branch_id, status, version)
         VALUES ($1, $2, $3, 'ACTIVE', 0)`,
        [randomUUID(), id, branchId],
      );
    }
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }

  return Object.freeze({ id, username, email, password, fullName, branchIds });
}

export async function invalidateFixtureSecurityVersion(
  database: Pool,
  userId: string,
): Promise<void> {
  const result = await database.query(
    `UPDATE gym.users
        SET security_version = security_version + 1,
            version = version + 1
      WHERE id = $1`,
    [userId],
  );
  if (result.rowCount !== 1) {
    throw new Error(
      "The synthetic account could not be advanced for the security check.",
    );
  }
}

function tokenFingerprint(
  token: string,
  purpose: "invitation" | "recovery",
): string {
  const separator =
    purpose === "invitation"
      ? "coach-gym:staff-invitation:v1:"
      : "coach-gym:staff-password-recovery:v1:";
  return createHash("sha256")
    .update(`${separator}${token}`, "ascii")
    .digest("hex");
}

/** Creates only a synthetic recovery row using the backend's purpose-separated fingerprint. */
export async function createPasswordRecoveryFixture(
  database: Pool,
  userId: string,
): Promise<OneTimeTokenFixture> {
  const id = randomUUID();
  const token = randomBytes(32).toString("base64url");
  await database.query(
    `INSERT INTO gym.staff_password_recovery_tokens
        (id, user_id, token_fingerprint, token_scheme, requested_at, expires_at)
     VALUES ($1, $2, $3, 'staff-password-recovery-sha256-v1',
             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '20 minutes')`,
    [id, userId, tokenFingerprint(token, "recovery")],
  );
  return Object.freeze({ id, token });
}

/** Creates a backend-valid invitation while returning its raw token only in memory. */
export async function createInvitationFixture(
  database: Pool,
  options: InvitationFixtureOptions,
): Promise<OneTimeTokenFixture & { email: string }> {
  const branchIds = [...new Set(options.branchIds)];
  if (
    (options.role === "RECEPTIONIST" && options.scope !== "BRANCH") ||
    (options.scope === "ORGANIZATION" && branchIds.length !== 0) ||
    (options.scope === "BRANCH" && branchIds.length === 0)
  ) {
    throw new Error("The requested synthetic invitation proposal is invalid.");
  }

  const id = randomUUID();
  const token = randomBytes(32).toString("base64url");
  const email = `invitation-${id}@example.test`;
  const client = await database.connect();
  try {
    await client.query("BEGIN");
    const organization = await client.query(
      `SELECT id
         FROM gym.organizations
        WHERE is_canonical = TRUE
          AND status = 'ACTIVE'`,
    );
    if (organization.rowCount !== 1) {
      throw new Error(
        "The fixture database has no unique active organization.",
      );
    }

    await client.query(
      `INSERT INTO gym.staff_invitations
          (id, organization_id, email_normalized, proposed_role, proposed_scope,
           token_fingerprint, token_scheme, created_at, last_sent_at, expires_at,
           invited_by_user_id, version)
       VALUES ($1, $2, $3, $4, $5, $6, 'staff-invitation-sha256-v1',
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
               CURRENT_TIMESTAMP + ($7::integer * INTERVAL '1 hour'), $8, 0)`,
      [
        id,
        organization.rows[0].id,
        email,
        options.role,
        options.scope,
        tokenFingerprint(token, "invitation"),
        options.role === "ADMIN" ? 20 : 40,
        options.inviterUserId,
      ],
    );
    for (const branchId of branchIds) {
      await client.query(
        `INSERT INTO gym.staff_invitation_branches (invitation_id, branch_id)
         VALUES ($1, $2)`,
        [id, branchId],
      );
    }
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
  return Object.freeze({ id, token, email });
}
