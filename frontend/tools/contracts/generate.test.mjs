import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  copyFile,
  mkdir,
  mkdtemp,
  readFile,
  realpath,
  rm,
  stat,
  symlink,
  unlink,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join } from "node:path";
import process from "node:process";
import { test } from "node:test";

const outputs = ["schema.d.ts", "f02-schema.d.ts"];

async function createFixture(context) {
  const ownedRoot = await realpath(tmpdir());
  const root = await mkdtemp(join(ownedRoot, "coach-gym-contracts-"));
  const tools = join(root, "tools", "contracts");
  const dependencies = join(tools, "node_modules");
  context.after(async () => {
    const target = await realpath(root);
    assert.equal(dirname(target), ownedRoot);
    assert.match(basename(target), /^coach-gym-contracts-/);
    await unlink(dependencies).catch((error) => {
      if (error.code !== "ENOENT") throw error;
    });
    await rm(target, { recursive: true, force: false });
  });

  await mkdir(tools, { recursive: true });
  await mkdir(join(root, "contracts"));
  for (const file of ["generate.mjs", "scope.mjs"]) {
    await copyFile(new URL(file, import.meta.url), join(tools, file));
  }
  for (const file of ["f01.openapi.json", "f02.openapi.json"]) {
    await copyFile(
      new URL(`../../contracts/${file}`, import.meta.url),
      join(root, "contracts", file),
    );
  }
  await symlink(
    await realpath(new URL("./node_modules/", import.meta.url)),
    dependencies,
    process.platform === "win32" ? "junction" : "dir",
  );

  return {
    root,
    generated: join(root, "src", "shared", "api", "generated"),
    run: (...args) =>
      spawnSync(process.execPath, [join(tools, "generate.mjs"), ...args], {
        cwd: root,
        encoding: "utf8",
        shell: false,
        timeout: 15_000,
      }),
  };
}

function assertSuccess(result) {
  assert.equal(result.error, undefined);
  assert.equal(result.status, 0, result.stderr);
}

test("check succeeds in a clean checkout without creating ignored generated files", async (context) => {
  const fixture = await createFixture(context);
  assertSuccess(fixture.run("--check"));
  await assert.rejects(stat(fixture.generated), { code: "ENOENT" });
});

test("generation writes both scopes and check preserves matching output bytes and timestamps", async (context) => {
  const fixture = await createFixture(context);
  assertSuccess(fixture.run());
  const before = [];
  for (const file of outputs) {
    const output = join(fixture.generated, file);
    const content = await readFile(output, "utf8");
    assert.match(content, /export interface paths/);
    before.push({ content, modified: (await stat(output)).mtimeMs });
  }

  assertSuccess(fixture.run("--check"));
  for (const [index, file] of outputs.entries()) {
    const output = join(fixture.generated, file);
    assert.equal(await readFile(output, "utf8"), before[index].content);
    assert.equal((await stat(output)).mtimeMs, before[index].modified);
  }
});

for (const file of outputs) {
  test(`check rejects stale ${file} without overwriting it`, async (context) => {
    const fixture = await createFixture(context);
    assertSuccess(fixture.run());
    const output = join(fixture.generated, file);
    const stale = "// Synthetic stale contract output.\n";
    await writeFile(output, stale, "utf8");

    const result = fixture.run("--check");
    assert.equal(result.error, undefined);
    assert.equal(result.status, 1);
    assert.ok(result.stderr.includes(`${file} no coincide.`));
    assert.equal(await readFile(output, "utf8"), stale);
  });
}

test("check still validates F02 when generated files are absent", async (context) => {
  const fixture = await createFixture(context);
  const snapshot = join(fixture.root, "contracts", "f02.openapi.json");
  const document = JSON.parse(await readFile(snapshot, "utf8"));
  delete document.paths["/api/v1/me/profile"];
  const invalid = JSON.stringify(document);
  await writeFile(snapshot, invalid, "utf8");

  const result = fixture.run("--check");
  assert.equal(result.error, undefined);
  assert.equal(result.status, 1);
  assert.ok(
    result.stderr.includes("Falta la operación get /api/v1/me/profile."),
  );
  assert.equal(await readFile(snapshot, "utf8"), invalid);
  await assert.rejects(stat(fixture.generated), { code: "ENOENT" });
});
