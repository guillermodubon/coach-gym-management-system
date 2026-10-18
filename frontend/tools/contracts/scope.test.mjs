import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { f01Operations, projectF01, validateSnapshot } from "./scope.mjs";

const snapshot = JSON.parse(
  await readFile(
    new URL("../../contracts/f01.openapi.json", import.meta.url),
    "utf8",
  ),
);

test("the reviewed input contains exactly six F01 operations and a local session security scheme", () => {
  validateSnapshot(snapshot);
  assert.deepEqual(Object.keys(snapshot.paths), Object.keys(f01Operations));
  assert.equal(Object.keys(snapshot.components.securitySchemes).length, 1);
  assert.equal(snapshot.components.securitySchemes.sessionCookie.in, "cookie");
});

test("projection excludes business paths and branch-context mutations without altering schemas", () => {
  const expanded = structuredClone(snapshot);
  expanded.paths["/api/v1/clients"] = { get: { responses: {} } };
  expanded.paths["/api/v1/me/branch-context"].put = { responses: {} };
  const projected = projectF01(expanded);
  assert.deepEqual(projected, snapshot);
  assert.deepEqual(projected.components.schemas, snapshot.components.schemas);
  assert.throws(() => validateSnapshot(expanded));
});

test("a missing required operation blocks regeneration", () => {
  const changed = structuredClone(snapshot);
  delete changed.paths["/api/v1/auth/login"];
  assert.throws(() => projectF01(changed));
});

test("external and unresolved component references fail before generator network resolution", () => {
  for (const reference of [
    "https://untrusted.invalid/schema.json",
    "#/components/schemas/Missing",
  ]) {
    const changed = structuredClone(snapshot);
    changed.components.schemas.CurrentUserResponse.properties.id = {
      $ref: reference,
    };
    assert.throws(() => projectF01(changed));
  }
});

test("captured password DTOs retain only the actual backend fields", () => {
  assert.deepEqual(
    Object.keys(
      snapshot.components.schemas.ChangeStaffPasswordRequest.properties,
    ).sort(),
    ["currentPassword", "newPassword", "newPasswordConfirmation"].sort(),
  );
  assert.deepEqual(
    Object.keys(snapshot.components.schemas.LoginRequest.properties).sort(),
    ["identifier", "password"],
  );
});
