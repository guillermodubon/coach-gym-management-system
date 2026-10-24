import { isAbsolute, relative, resolve, sep } from "node:path";
import { defineConfig } from "@playwright/test";

const runtimeDirectory = process.env.CG_E2E_RUNTIME_DIR;
const outputDirectory = process.env.CG_E2E_OUTPUT_DIR;
const reportFile = process.env.CG_E2E_REPORT_FILE;
const baseURL = process.env.CG_E2E_BASE_URL;

if (
  process.env.CG_E2E_HARNESS !== "1" ||
  !runtimeDirectory ||
  !outputDirectory ||
  !reportFile ||
  !baseURL ||
  !process.env.CG_E2E_DB_HOST ||
  !process.env.CG_E2E_DB_PORT ||
  !process.env.CG_E2E_DB_NAME ||
  !process.env.CG_E2E_DB_USER ||
  !process.env.CG_E2E_DB_PASSWORD ||
  !process.env.CG_E2E_INITIAL_BRANCH_ID ||
  !process.env.CG_E2E_SECOND_BRANCH_ID ||
  !process.env.CG_E2E_SECOND_BRANCH_NAME
) {
  throw new Error(
    "Run this Playwright project only through test:e2e:integration.",
  );
}

const ownedRuntimeDirectory = runtimeDirectory;
const ownedOutputDirectory = outputDirectory;
const ownedReportFile = reportFile;
const ownedBaseURL = baseURL;

function assertOwnedPath(value: string): void {
  if (!isAbsolute(value)) {
    throw new Error("The integration harness output paths must be absolute.");
  }
  const root = resolve(ownedRuntimeDirectory);
  const target = resolve(value);
  const fromRoot = relative(root, target);
  if (
    fromRoot === "" ||
    fromRoot === ".." ||
    fromRoot.startsWith(`..${sep}`) ||
    isAbsolute(fromRoot)
  ) {
    throw new Error(
      "The integration harness output path is outside its owned directory.",
    );
  }
}

assertOwnedPath(ownedOutputDirectory);
assertOwnedPath(ownedReportFile);
if (ownedBaseURL !== "http://127.0.0.1:5183") {
  throw new Error(
    "Integration browser traffic must use the owned same-origin proxy.",
  );
}

export default defineConfig({
  testDir: "./tests/integration",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  forbidOnly: true,
  reporter: "./tests/integration/safe-reporter.ts",
  outputDir: ownedOutputDirectory,
  projects: [
    {
      name: "normal",
      testMatch: "auth.integration.spec.ts",
      use: { browserName: "chromium" },
    },
    {
      name: "f02",
      testMatch: "f02.integration.spec.ts",
      use: { browserName: "chromium" },
    },
    {
      name: "expiry",
      testMatch: "expiry.integration.spec.ts",
      use: { browserName: "chromium" },
    },
  ],
  use: {
    baseURL: ownedBaseURL,
    browserName: "chromium",
    screenshot: "off",
    trace: "off",
    video: "off",
    viewport: { width: 1280, height: 900 },
  },
});
