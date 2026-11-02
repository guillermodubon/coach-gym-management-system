import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  f01Operations,
  f02Operations,
  f03Operations,
  projectF01,
  projectF02,
  projectF03,
  validateSnapshot,
} from "./scope.mjs";

const snapshot = JSON.parse(
  await readFile(
    new URL("../../contracts/f01.openapi.json", import.meta.url),
    "utf8",
  ),
);
const f02Snapshot = JSON.parse(
  await readFile(
    new URL("../../contracts/f02.openapi.json", import.meta.url),
    "utf8",
  ),
);
const f03Snapshot = JSON.parse(
  await readFile(
    new URL("../../contracts/f03.openapi.json", import.meta.url),
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

test("the F02 snapshot contains the ten reviewed operations and preserves multiple methods per path", () => {
  validateSnapshot(f02Snapshot, "f02");
  assert.deepEqual(Object.keys(f02Snapshot.paths), Object.keys(f02Operations));
  assert.deepEqual(Object.keys(f02Snapshot.paths["/api/v1/me/profile"]), [
    "get",
    "put",
  ]);
  assert.deepEqual(Object.keys(f02Snapshot.paths["/api/v1/me/profile/photo"]), [
    "get",
    "put",
    "delete",
  ]);
  assert.equal(
    Object.values(f02Snapshot.paths).reduce(
      (count, path) => count + Object.keys(path).length,
      0,
    ),
    10,
  );
});

test("F02 retains the actual untyped H09 response and the F01 H06 operation", () => {
  const invitationPreview =
    f02Snapshot.paths["/api/v1/staff-invitations/inspect"].post.responses[
      "200"
    ];
  assert.equal(invitationPreview.content["*/*"].schema.type, "object");
  assert.equal(invitationPreview.content["*/*"].schema.properties, undefined);
  assert.deepEqual(
    f02Snapshot.paths["/api/v1/me/profile/password"],
    snapshot.paths["/api/v1/me/profile/password"],
  );
});

test("F02 projection excludes unrelated operations and rejects unresolved references", () => {
  const expanded = structuredClone(f02Snapshot);
  expanded.paths["/api/v1/clients"] = { get: { responses: {} } };
  expanded.paths["/api/v1/me/profile"].delete = { responses: {} };
  assert.deepEqual(projectF02(expanded), f02Snapshot);
  assert.throws(() => validateSnapshot(expanded, "f02"));

  const unresolved = structuredClone(f02Snapshot);
  unresolved.components.schemas.StaffSelfProfile.properties.userId = {
    $ref: "https://untrusted.invalid/schema.json",
  };
  assert.throws(() => projectF02(unresolved));
});

test("the F03 snapshot contains the ten reviewed operations across six paths", () => {
  validateSnapshot(f03Snapshot, "f03");
  assert.deepEqual(Object.keys(f03Snapshot.paths), Object.keys(f03Operations));
  assert.equal(
    Object.values(f03Snapshot.paths).reduce(
      (count, path) => count + Object.keys(path).length,
      0,
    ),
    10,
  );
  assert.deepEqual(Object.keys(f03Snapshot.paths["/api/v1/branches"]), [
    "get",
    "post",
  ]);
  assert.deepEqual(
    Object.keys(f03Snapshot.paths["/api/v1/me/branch-context"]),
    ["get", "put", "delete"],
  );
});

test("F03 shares H08 exactly with F01 and excludes future administration paths", () => {
  assert.deepEqual(
    f03Snapshot.paths["/api/v1/me/branch-context"].get,
    snapshot.paths["/api/v1/me/branch-context"].get,
  );
  const expanded = structuredClone(f03Snapshot);
  expanded.paths["/api/v1/organization"].put = { responses: {} };
  expanded.paths["/api/v1/access-policies"] = { get: { responses: {} } };
  assert.deepEqual(projectF03(expanded), f03Snapshot);
  assert.throws(() => validateSnapshot(expanded, "f03"));
});

test("F03 projection rejects missing operations and unresolved references", () => {
  const missing = structuredClone(f03Snapshot);
  delete missing.paths["/api/v1/branches/{id}/activate"];
  assert.throws(() => projectF03(missing));

  const unresolved = structuredClone(f03Snapshot);
  unresolved.components.schemas.BranchResponse.properties.id = {
    $ref: "https://untrusted.invalid/schema.json",
  };
  assert.throws(() => projectF03(unresolved));
});
