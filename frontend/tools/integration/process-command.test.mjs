import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  chmod,
  mkdtemp,
  realpath,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import {
  gradleBuildInvocation,
  processStartFailureMessage,
} from "./process-command.mjs";

const expectedTasks = [
  "compileJava",
  "compileTestJava",
  "bootJar",
  "--console=plain",
];

for (const platform of ["linux", "darwin"]) {
  test(`${platform} invokes the readable Gradle wrapper through sh`, () => {
    assert.deepEqual(gradleBuildInvocation(platform), {
      command: "sh",
      args: ["./gradlew", ...expectedTasks],
    });
  });
}

test("Windows retains the cmd interpreter and batch wrapper invocation", () => {
  assert.deepEqual(gradleBuildInvocation("win32"), {
    command: "cmd.exe",
    args: [
      "/d",
      "/s",
      "/c",
      ".\\gradlew.bat compileJava compileTestJava bootJar --console=plain",
    ],
  });
});

test(
  "a non-executable POSIX wrapper fails directly but succeeds through the production invocation",
  { skip: process.platform === "win32" },
  async (context) => {
    const ownedRoot = await realpath(tmpdir());
    const directory = await mkdtemp(join(ownedRoot, "coach-gym-wrapper-"));
    context.after(async () => {
      const target = await realpath(directory);
      assert.equal(dirname(target), ownedRoot);
      await rm(target, { recursive: true, force: false });
    });
    const wrapper = join(directory, "gradlew");
    await writeFile(join(directory, "fixture-root"), "", { mode: 0o600 });
    await writeFile(
      wrapper,
      '#!/bin/sh\n[ -f ./fixture-root ] || exit 91\nprintf "%s\\n" "$@"\n',
      { mode: 0o644 },
    );
    await chmod(wrapper, 0o644);
    assert.equal((await stat(wrapper)).mode & 0o111, 0);
    const options = {
      cwd: directory,
      encoding: "utf8",
      shell: false,
      timeout: 5_000,
    };

    const direct = spawnSync("./gradlew", expectedTasks, options);
    assert.equal(direct.error?.code, "EACCES");

    const invocation = gradleBuildInvocation();
    const launched = spawnSync(invocation.command, invocation.args, options);
    assert.equal(launched.error, undefined);
    assert.equal(launched.status, 0);
    assert.equal(launched.stdout, `${expectedTasks.join("\n")}\n`);
  },
);

test("startup diagnostics identify the fixed phase and an allowed OS code", () => {
  for (const code of ["EACCES", "ENOENT", "ENOEXEC", "EPERM"]) {
    assert.equal(
      processStartFailureMessage("Backend compile and bootJar", {
        code,
        message: "synthetic private process details",
        path: "/synthetic/private/location",
        spawnargs: ["synthetic-private-argument"],
      }),
      `Backend compile and bootJar could not be started [${code}].`,
    );
  }
});

test("unknown OS errors never print their code, message, path or arguments", () => {
  for (const error of [
    undefined,
    null,
    {
      code: "synthetic-private-code",
      message: "synthetic private process details",
      path: "/synthetic/private/location",
      spawnargs: ["synthetic-private-argument"],
    },
  ]) {
    assert.equal(
      processStartFailureMessage("Backend compile and bootJar", error),
      "Backend compile and bootJar could not be started [OS_ERROR].",
    );
  }
});
