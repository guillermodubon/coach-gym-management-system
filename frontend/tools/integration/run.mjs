import { randomBytes, randomUUID } from "node:crypto";
import { spawn } from "node:child_process";
import { createServer as createTcpServer } from "node:net";
import {
  access,
  mkdir,
  readFile,
  realpath,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Pool } from "pg";
import { PostgreSqlContainer } from "@testcontainers/postgresql";
import prettier from "prettier";
import { projectF01, projectF02 } from "../contracts/scope.mjs";
import { seedExecutionBranch } from "./seed.mjs";
import { startBuiltServer } from "./serve-built.mjs";
import {
  gradleBuildInvocation,
  processStartFailureMessage,
} from "./process-command.mjs";

const FRONTEND_ROOT = resolve(fileURLToPath(new URL("../..", import.meta.url)));
const REPOSITORY_ROOT = resolve(FRONTEND_ROOT, "..");
const BACKEND_ROOT = resolve(REPOSITORY_ROOT, "backend");
const RUNTIME_ROOT = resolve(FRONTEND_ROOT, ".integration-runtime");
const FRONTEND_PORT = 5183;
const BACKEND_PORT = 18082;
const FIXTURE_DATABASE = "coach_gym_f01_e2e";
const FIXTURE_USER = "f01_fixture";
const JAVA_BASE_ARGUMENTS = [
  "--server.address=127.0.0.1",
  `--server.port=${BACKEND_PORT}`,
  "--spring.docker.compose.enabled=false",
  "--spring.config.import=optional:file:./f01-contract-only.properties",
  "--coach-gym.bootstrap.admin.enabled=false",
  "--coach-gym.email.enabled=false",
  "--coach-gym.payment.stripe.enabled=false",
  "--gym.storage.provider=local",
  `--coach-gym.security.cors.allowed-origins=http://127.0.0.1:${FRONTEND_PORT}`,
  "--logging.level.root=WARN",
];
const captureF02Contract = process.argv
  .slice(2)
  .includes("--capture-f02-contract");
if (
  process.argv
    .slice(2)
    .some((argument) => argument !== "--capture-f02-contract")
) {
  throw new Error("Unknown integration harness argument.");
}

class HarnessFailure extends Error {
  constructor(safeMessage) {
    super(safeMessage);
    this.safeMessage = safeMessage;
  }
}

const allowedEnvironmentKeys = [
  "PATH",
  "Path",
  "SystemRoot",
  "WINDIR",
  "COMSPEC",
  "PATHEXT",
  "TEMP",
  "TMP",
  "USERPROFILE",
  "APPDATA",
  "LOCALAPPDATA",
  "JAVA_HOME",
  "DOCKER_HOST",
  "DOCKER_CONTEXT",
  "DOCKER_CONFIG",
  "DOCKER_CERT_PATH",
  "DOCKER_TLS_VERIFY",
];

function safeEnvironment(source = process.env) {
  const result = {};
  const actualKeys = Object.keys(source);
  const copied = new Set();
  for (const candidate of allowedEnvironmentKeys) {
    const actual = actualKeys.find(
      (key) => key.toLowerCase() === candidate.toLowerCase(),
    );
    if (actual && !copied.has(actual.toLowerCase())) {
      result[actual] = source[actual];
      copied.add(actual.toLowerCase());
    }
  }
  return result;
}

const abortController = new AbortController();
const childProcesses = new Set();
let interrupted = false;
let backendProcess;
let postgres;
let databasePool;
let builtServer;
let runtimeDirectory;

function stopOnSignal() {
  interrupted = true;
  abortController.abort();
}

process.once("SIGINT", stopOnSignal);
process.once("SIGTERM", stopOnSignal);

function throwIfInterrupted() {
  if (abortController.signal.aborted) {
    throw new HarnessFailure(
      "F01 integration was interrupted; owned resources are being closed.",
    );
  }
}

function spawnOwned(command, args, options) {
  let child;
  try {
    child = spawn(command, args, {
      ...options,
      shell: false,
      windowsHide: true,
    });
  } catch (error) {
    throw new HarnessFailure(
      processStartFailureMessage("Integration process", error),
    );
  }
  childProcesses.add(child);
  child.once("close", () => childProcesses.delete(child));
  child.once("error", () => childProcesses.delete(child));
  return child;
}

function waitForChild(child, label = "Integration process") {
  return new Promise((resolveWait, rejectWait) => {
    let settled = false;
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      abortController.signal.removeEventListener("abort", abort);
      callback(value);
    };
    const close = (code, signal) => finish(resolveWait, { code, signal });
    const error = (cause) =>
      finish(
        rejectWait,
        new HarnessFailure(processStartFailureMessage(label, cause)),
      );
    const abort = () => {
      void stopChild(child).finally(() =>
        finish(
          rejectWait,
          new HarnessFailure(
            "F01 integration was interrupted; owned resources are being closed.",
          ),
        ),
      );
    };
    child.once("close", close);
    child.once("error", error);
    if (abortController.signal.aborted) abort();
    else
      abortController.signal.addEventListener("abort", abort, { once: true });
  });
}

async function stopChild(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  await new Promise((resolveStop) => {
    const timeout = setTimeout(() => {
      if (child.exitCode === null && child.signalCode === null) {
        try {
          child.kill("SIGKILL");
        } catch {
          // The process may have exited between the state check and kill.
        }
      }
      resolveStop();
    }, 5_000);
    child.once("close", () => {
      clearTimeout(timeout);
      resolveStop();
    });
    try {
      child.kill("SIGTERM");
    } catch {
      clearTimeout(timeout);
      resolveStop();
    }
  });
}

async function runCommand(command, args, { cwd, env, label }) {
  throwIfInterrupted();
  const child = spawnOwned(command, args, {
    cwd,
    env,
    stdio: "ignore",
  });
  const result = await waitForChild(child, label);
  if (result.code !== 0) {
    throw new HarnessFailure(
      `${label} failed (exit ${result.code ?? "unknown"}).`,
    );
  }
}

async function verifyCommand(command, args, env, expectedPattern, safeMessage) {
  const child = spawnOwned(command, args, {
    cwd: FRONTEND_ROOT,
    env,
    stdio: ["ignore", "pipe", "pipe"],
  });
  let output = "";
  child.stdout?.on("data", (chunk) => {
    output += chunk.toString("utf8").slice(0, 2_000);
  });
  child.stderr?.on("data", (chunk) => {
    output += chunk.toString("utf8").slice(0, 2_000);
  });
  const result = await waitForChild(child);
  if (result.code !== 0 || !expectedPattern.test(output)) {
    throw new HarnessFailure(safeMessage);
  }
}

async function assertPortFree(port) {
  const server = createTcpServer();
  await new Promise((resolveListen, rejectListen) => {
    server.once("error", () =>
      rejectListen(
        new HarnessFailure(`Required loopback port ${port} is already in use.`),
      ),
    );
    server.listen(port, "127.0.0.1", () => resolveListen());
  });
  await new Promise((resolveClose, rejectClose) => {
    server.close((error) => (error ? rejectClose(error) : resolveClose()));
  });
}

async function waitForBackend(child, phase) {
  const deadline = Date.now() + 240_000;
  let nextProgressAt = Date.now() + 10_000;
  while (Date.now() < deadline) {
    throwIfInterrupted();
    if (child.exitCode !== null || child.signalCode !== null) {
      throw new HarnessFailure(`The isolated backend exited during ${phase}.`);
    }
    try {
      const response = await fetch(
        `http://127.0.0.1:${BACKEND_PORT}/v3/api-docs`,
        {
          cache: "no-store",
          signal: AbortSignal.timeout(1_000),
        },
      );
      if (response.ok) {
        const document = await response.json();
        if (document && typeof document === "object" && document.openapi)
          return document;
      } else {
        await response.body?.cancel();
      }
    } catch {
      // Readiness errors can include response details; only a phase is reported.
    }
    if (Date.now() >= nextProgressAt) {
      console.log(`F01 integration: waiting for isolated backend (${phase}).`);
      nextProgressAt = Date.now() + 10_000;
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 500));
  }
  throw new HarnessFailure(
    `The isolated backend did not become ready during ${phase}.`,
  );
}

function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, canonicalize(value[key])]),
    );
  }
  return value;
}

async function assertLiveContracts(document) {
  const snapshotPath = resolve(FRONTEND_ROOT, "contracts/f01.openapi.json");
  const snapshot = JSON.parse(await readFile(snapshotPath, "utf8"));
  let projected;
  try {
    projected = projectF01(document);
  } catch {
    throw new HarnessFailure(
      "The live backend is missing a reviewed F01 HTTP operation or schema.",
    );
  }
  if (
    JSON.stringify(canonicalize(projected)) !==
    JSON.stringify(canonicalize(snapshot))
  ) {
    throw new HarnessFailure(
      "The live F01 OpenAPI projection differs from its reviewed snapshot; the snapshot was not changed.",
    );
  }
  if (captureF02Contract) return;

  const f02SnapshotPath = resolve(FRONTEND_ROOT, "contracts/f02.openapi.json");
  const f02Snapshot = JSON.parse(await readFile(f02SnapshotPath, "utf8"));
  let f02Projected;
  try {
    f02Projected = projectF02(document);
  } catch {
    throw new HarnessFailure(
      "The live backend is missing a reviewed F02 HTTP operation or schema.",
    );
  }
  if (
    JSON.stringify(canonicalize(f02Projected)) !==
    JSON.stringify(canonicalize(f02Snapshot))
  ) {
    throw new HarnessFailure(
      "The live F02 OpenAPI projection differs from its reviewed snapshot; the snapshot was not changed.",
    );
  }
}

async function createPool({ host, port, database, user, password }) {
  const pool = new Pool({
    host,
    port,
    database,
    user,
    password,
    application_name: "coach-gym-f01-e2e-harness",
    connectionTimeoutMillis: 5_000,
    max: 2,
  });
  await pool.query("SELECT 1");
  return pool;
}

async function startBackend({
  javaEnvironment,
  isolatedWorkingDirectory,
  expiry,
}) {
  const jar = resolve(
    BACKEND_ROOT,
    "build/libs/coach-gym-management-system-0.1.0-SNAPSHOT.jar",
  );
  const jarInfo = await stat(jar).catch(() => null);
  if (!jarInfo?.isFile()) {
    throw new HarnessFailure(
      "The expected executable backend JAR was not produced by the repository build.",
    );
  }
  const args = ["-jar", jar, ...JAVA_BASE_ARGUMENTS];
  if (expiry) args.push("--coach-gym.security.session.absolute-timeout=PT15S");
  backendProcess = spawnOwned("java", args, {
    cwd: isolatedWorkingDirectory,
    env: javaEnvironment,
    stdio: "ignore",
  });
  return backendProcess;
}

function readSafeReporterResult(reportFile) {
  return readFile(reportFile, "utf8").then((contents) => {
    const report = JSON.parse(contents);
    if (
      !Number.isSafeInteger(report.expected) ||
      !Array.isArray(report.titles) ||
      !report.titles.every(
        (result) =>
          typeof result?.title === "string" &&
          typeof result?.status === "string" &&
          (result.failureCategory === undefined ||
            [
              "assertion",
              "other",
              "responsive-overflow-320",
              "responsive-overflow-320-menu",
              "responsive-overflow-390",
              "strict-locator",
              "timeout",
            ].includes(result.failureCategory)) &&
          (result.overflowPixels === undefined ||
            (Number.isSafeInteger(result.overflowPixels) &&
              result.overflowPixels >= 0 &&
              result.overflowPixels <= 100_000)) &&
          (result.overflowSources === undefined ||
            (Array.isArray(result.overflowSources) &&
              result.overflowSources.every((source) =>
                [
                  "ant-drawer",
                  "ant-dropdown",
                  "body",
                  "cg-account-trigger",
                  "cg-app-layout",
                  "cg-content-layout",
                  "cg-header",
                  "cg-header-account",
                  "cg-header-actions",
                  "cg-header-context",
                  "cg-header-controls",
                  "cg-header-leading",
                  "cg-home-welcome",
                  "cg-main-content",
                  "cg-mobile-drawer",
                  "cg-sidebar",
                  "root",
                ].includes(source),
              ))) &&
          (result.failureLocation === undefined ||
            /^(auth\.integration\.spec\.ts|expiry\.integration\.spec\.ts|f02\.integration\.spec\.ts|fixtures\.ts):\d+$/.test(
              result.failureLocation,
            )),
      ) ||
      !report.counts ||
      !["passed", "failed", "skipped", "other"].every((key) =>
        Number.isSafeInteger(report.counts[key]),
      )
    ) {
      throw new HarnessFailure("The integration browser report was malformed.");
    }
    return report;
  });
}

async function runBrowserProject(project, runDirectory, childEnvironment) {
  const reportFile = resolve(runDirectory, `report-${project}.json`);
  const outputDirectory = resolve(runDirectory, `playwright-${project}`);
  await mkdir(outputDirectory, { recursive: false, mode: 0o700 });
  const env = {
    ...childEnvironment,
    CG_E2E_HARNESS: "1",
    CG_E2E_RUNTIME_DIR: runDirectory,
    CG_E2E_OUTPUT_DIR: outputDirectory,
    CG_E2E_REPORT_FILE: reportFile,
    CG_E2E_BASE_URL: `http://127.0.0.1:${FRONTEND_PORT}`,
  };
  console.log(`F01 integration: running Chromium project ${project}.`);
  const npmCli = process.env.npm_execpath;
  if (!npmCli || !isAbsolute(npmCli)) {
    throw new HarnessFailure(
      "Run integration through the npm script so its locked Playwright runner is used.",
    );
  }
  await access(npmCli).catch(() => {
    throw new HarnessFailure("The locked npm runner could not be located.");
  });
  const result = await spawnOwned(
    process.execPath,
    [
      npmCli,
      "run",
      "test:e2e:integration:browser",
      "--",
      `--project=${project}`,
    ],
    {
      cwd: FRONTEND_ROOT,
      env,
      stdio: "ignore",
    },
  );
  const exit = await waitForChild(result, "Chromium integration runner");
  let report;
  try {
    report = await readSafeReporterResult(reportFile);
  } catch (error) {
    if (error instanceof HarnessFailure) throw error;
    throw new HarnessFailure(
      `The safe Chromium report for ${project} was not produced.`,
    );
  }

  for (const testResult of report.titles) {
    const status = testResult.status === "passed" ? "passed" : "failed";
    const location = testResult.failureLocation
      ? ` (${testResult.failureLocation})`
      : "";
    const category = testResult.failureCategory
      ? ` [${testResult.failureCategory}]`
      : "";
    const overflow = Number.isSafeInteger(testResult.overflowPixels)
      ? ` (${testResult.overflowPixels}px horizontal overflow)`
      : "";
    const sources = Array.isArray(testResult.overflowSources)
      ? ` [elements: ${testResult.overflowSources.join(", ")}]`
      : "";
    console.log(
      `  ${status}: ${testResult.title}${location}${category}${overflow}${sources}`,
    );
  }
  console.log(
    `F01 integration: ${project} ${report.counts.passed}/${report.expected} passed; ` +
      `${report.counts.failed} failed, ${report.counts.skipped} skipped.`,
  );

  if (
    exit.code !== 0 ||
    report.expected === 0 ||
    report.counts.passed !== report.expected ||
    report.counts.failed !== 0 ||
    report.counts.skipped !== 0 ||
    report.counts.other !== 0 ||
    report.titles.length !== report.expected
  ) {
    throw new HarnessFailure(
      `The Chromium integration project ${project} did not pass every real case.`,
    );
  }
}

async function cleanup() {
  if (builtServer) {
    await builtServer.close().catch(() => undefined);
    builtServer = undefined;
  }
  if (backendProcess) {
    await stopChild(backendProcess).catch(() => undefined);
    backendProcess = undefined;
  }
  for (const child of [...childProcesses]) {
    await stopChild(child).catch(() => undefined);
  }
  if (databasePool) {
    await databasePool.end().catch(() => undefined);
    databasePool = undefined;
  }
  if (postgres) {
    await postgres.stop().catch(() => undefined);
    postgres = undefined;
  }
  if (runtimeDirectory) {
    const root = await realpath(RUNTIME_ROOT).catch(() => null);
    const target = await realpath(runtimeDirectory).catch(() => null);
    if (
      root &&
      target &&
      dirname(target) === root &&
      /^run-[0-9a-f-]{36}$/i.test(target.slice(root.length + 1))
    ) {
      await rm(target, { recursive: true, force: false }).catch(
        () => undefined,
      );
    }
    runtimeDirectory = undefined;
  }
}

async function main() {
  console.log(
    "F01 integration: checking Java, Docker, and owned loopback ports.",
  );
  const hostEnvironment = safeEnvironment();
  await verifyCommand(
    "java",
    ["-version"],
    hostEnvironment,
    /version\s+"21(?:\.|"?)/,
    "Java 21 is required for the isolated backend.",
  );
  await verifyCommand(
    "docker",
    ["version", "--format", "{{.Server.Version}}"],
    hostEnvironment,
    /\d+\.\d+/,
    "A reachable Docker engine is required for the PostgreSQL fixture.",
  );
  await assertPortFree(BACKEND_PORT);
  await assertPortFree(FRONTEND_PORT);
  throwIfInterrupted();

  const runId = `run-${randomUUID()}`;
  await mkdir(RUNTIME_ROOT, { recursive: true, mode: 0o700 });
  runtimeDirectory = resolve(RUNTIME_ROOT, runId);
  await mkdir(runtimeDirectory, { recursive: false, mode: 0o700 });
  const canonicalRuntimeRoot = await realpath(RUNTIME_ROOT);
  const canonicalRunDirectory = await realpath(runtimeDirectory);
  if (dirname(canonicalRunDirectory) !== canonicalRuntimeRoot) {
    throw new HarnessFailure(
      "The integration runtime directory is outside its owned root.",
    );
  }
  const isolatedWorkingDirectory = resolve(runtimeDirectory, "backend-cwd");
  const isolatedViteEnvironment = resolve(runtimeDirectory, "empty-vite-env");
  const isolatedBuild = resolve(runtimeDirectory, "frontend-build");
  await mkdir(isolatedWorkingDirectory, { recursive: false, mode: 0o700 });
  await mkdir(isolatedViteEnvironment, { recursive: false, mode: 0o700 });

  const npmCli = process.env.npm_execpath;
  if (!npmCli || !isAbsolute(npmCli)) {
    throw new HarnessFailure(
      "Run the isolated integration harness with npm run test:e2e:integration.",
    );
  }
  const browserBuildEnvironment = {
    ...hostEnvironment,
    CG_F01_VITE_ENV_DIR: isolatedViteEnvironment,
  };
  if (!captureF02Contract) {
    console.log(
      "F01 integration: type-checking and building frontend into an isolated directory.",
    );
    await runCommand(process.execPath, [npmCli, "run", "typecheck"], {
      cwd: FRONTEND_ROOT,
      env: browserBuildEnvironment,
      label: "Frontend typecheck",
    });
    await runCommand(
      process.execPath,
      [
        resolve(FRONTEND_ROOT, "node_modules/vite/bin/vite.js"),
        "build",
        "--outDir",
        isolatedBuild,
        "--emptyOutDir",
      ],
      {
        cwd: FRONTEND_ROOT,
        env: browserBuildEnvironment,
        label: "Isolated frontend production build",
      },
    );
  }

  console.log(
    "F01 integration: compiling the backend with its repository wrapper.",
  );
  const gradle = gradleBuildInvocation();
  await runCommand(gradle.command, gradle.args, {
    cwd: BACKEND_ROOT,
    env: hostEnvironment,
    label: "Backend compile and bootJar",
  });

  console.log("F01 integration: starting an owned PostgreSQL 17 container.");
  const databasePassword = randomBytes(32).toString("base64url");
  postgres = await new PostgreSqlContainer("postgres:17-alpine")
    .withDatabase(FIXTURE_DATABASE)
    .withUsername(FIXTURE_USER)
    .withPassword(databasePassword)
    .start();
  const database = {
    host: postgres.getHost(),
    port: postgres.getMappedPort(5432),
    database: FIXTURE_DATABASE,
    user: FIXTURE_USER,
    password: databasePassword,
  };
  databasePool = await createPool(database);

  const javaEnvironment = {
    ...hostEnvironment,
    DATABASE_URL: `jdbc:postgresql://${database.host}:${database.port}/${database.database}`,
    DATABASE_USERNAME: database.user,
    DATABASE_PASSWORD: database.password,
    STAFF_PROFILE_PHOTOS_DIRECTORY: resolve(
      runtimeDirectory,
      "staff-profile-photos",
    ),
  };

  console.log(
    "F01 integration: starting the isolated backend with normal session policy.",
  );
  backendProcess = await startBackend({
    javaEnvironment,
    isolatedWorkingDirectory,
    expiry: false,
  });
  const liveDocument = await waitForBackend(
    backendProcess,
    "normal session policy",
  );
  await assertLiveContracts(liveDocument);
  console.log(
    "F01 integration: live F01 OpenAPI projection matches the reviewed snapshot.",
  );
  if (captureF02Contract) {
    const snapshot = projectF02(liveDocument);
    await writeFile(
      resolve(FRONTEND_ROOT, "contracts/f02.openapi.json"),
      await prettier.format(JSON.stringify(snapshot), { parser: "json" }),
      "utf8",
    );
    console.log(
      "F02 contract captured from the isolated backend. Review the snapshot before accepting it.",
    );
    return;
  }
  console.log(
    "F02 integration: live F02 OpenAPI projection matches the reviewed snapshot.",
  );

  // Flyway creates and seeds the authoritative fixture schema during backend
  // startup. Add the synthetic branch only after those migrations complete.
  const branches = await seedExecutionBranch(databasePool, FIXTURE_DATABASE);

  builtServer = await startBuiltServer({
    distDirectory: isolatedBuild,
    host: "127.0.0.1",
    port: FRONTEND_PORT,
    backendPort: BACKEND_PORT,
  });

  const browserEnvironment = {
    ...hostEnvironment,
    npm_execpath: npmCli,
    npm_node_execpath: process.execPath,
    CG_E2E_DB_HOST: database.host,
    CG_E2E_DB_PORT: String(database.port),
    CG_E2E_DB_NAME: database.database,
    CG_E2E_DB_USER: database.user,
    CG_E2E_DB_PASSWORD: database.password,
    CG_E2E_INITIAL_BRANCH_ID: branches.initialBranchId,
    CG_E2E_SECOND_BRANCH_ID: branches.secondBranchId,
    CG_E2E_SECOND_BRANCH_NAME: branches.secondBranchName,
  };
  await runBrowserProject("normal", runtimeDirectory, browserEnvironment);
  await runBrowserProject("f02", runtimeDirectory, browserEnvironment);

  console.log(
    "F01 integration: restarting only the owned backend for PT15S expiry coverage.",
  );
  await stopChild(backendProcess);
  backendProcess = undefined;
  backendProcess = await startBackend({
    javaEnvironment,
    isolatedWorkingDirectory,
    expiry: true,
  });
  await waitForBackend(backendProcess, "short absolute-session expiry policy");
  await runBrowserProject("expiry", runtimeDirectory, browserEnvironment);

  console.log(
    "F01 and F02 integration: real backend, PostgreSQL, Chromium, CSRF, profile-photo storage, identity-token, and expiry checks passed.",
  );
}

try {
  await main();
} catch (error) {
  const safeMessage =
    error instanceof HarnessFailure
      ? error.safeMessage
      : "F01 integration failed; raw process and request details were suppressed.";
  console.error(safeMessage);
  process.exitCode = 1;
} finally {
  await cleanup();
  if (interrupted && process.exitCode === undefined) process.exitCode = 130;
  process.removeListener("SIGINT", stopOnSignal);
  process.removeListener("SIGTERM", stopOnSignal);
}
