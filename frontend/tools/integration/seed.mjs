import { randomUUID } from "node:crypto";

const FIXTURE_DATABASE = "coach_gym_f01_e2e";

/**
 * Seed the one additional branch used by this isolated integration run.
 * The caller must supply a Pool built from the PostgreSQL Testcontainer that
 * owns this run; this module never accepts a connection string.
 */
export async function seedExecutionBranch(pool, databaseName) {
  if (
    databaseName !== FIXTURE_DATABASE ||
    !pool ||
    typeof pool.connect !== "function"
  ) {
    throw new Error(
      "Integration branch seed requires the owned fixture database.",
    );
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const organizationResult = await client.query(
      `SELECT id
         FROM gym.organizations
        WHERE is_canonical = TRUE
          AND status = 'ACTIVE'
        FOR SHARE`,
    );
    if (organizationResult.rowCount !== 1) {
      throw new Error(
        "The migrated fixture must contain one active organization.",
      );
    }

    const organizationId = organizationResult.rows[0].id;
    const branchResult = await client.query(
      `SELECT id
         FROM gym.gym_branches
        WHERE organization_id = $1
          AND is_initial_branch = TRUE
          AND status = 'ACTIVE'
        FOR SHARE`,
      [organizationId],
    );
    if (branchResult.rowCount !== 1) {
      throw new Error(
        "The migrated fixture must contain one active initial branch.",
      );
    }

    const secondBranchId = randomUUID();
    const code = `F01_E2E_${secondBranchId.replaceAll("-", "").slice(0, 12).toUpperCase()}`;
    const name = `Sucursal F01 ${secondBranchId.slice(0, 8)}`;
    const inserted = await client.query(
      `INSERT INTO gym.gym_branches
          (id, organization_id, code, name, country_code, timezone,
           status, is_initial_branch, version)
       VALUES ($1, $2, $3, $4, 'SV', 'America/El_Salvador', 'ACTIVE', FALSE, 0)
       RETURNING id`,
      [secondBranchId, organizationId, code, name],
    );
    if (inserted.rowCount !== 1) {
      throw new Error("The synthetic branch was not created.");
    }

    await client.query("COMMIT");
    return Object.freeze({
      organizationId,
      initialBranchId: branchResult.rows[0].id,
      secondBranchId,
      secondBranchCode: code,
      secondBranchName: name,
    });
  } catch (error) {
    await client.query("ROLLBACK").catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}
