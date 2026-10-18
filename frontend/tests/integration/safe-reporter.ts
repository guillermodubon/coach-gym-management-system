import { writeFile } from "node:fs/promises";
import {
  basename,
  dirname,
  isAbsolute,
  relative,
  resolve,
  sep,
} from "node:path";
import type {
  FullConfig,
  Reporter,
  Suite,
  TestCase,
  TestResult,
} from "@playwright/test/reporter";

type SafeFailureCategory =
  | "assertion"
  | "other"
  | "responsive-overflow-320"
  | "responsive-overflow-320-menu"
  | "responsive-overflow-390"
  | "strict-locator"
  | "timeout";

const responsiveSources = [
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
] as const;
const responsiveSourceSet: ReadonlySet<string> = new Set(responsiveSources);

interface SafeReport {
  expected: number;
  counts: Record<"passed" | "failed" | "skipped" | "other", number>;
  titles: Array<{
    title: string;
    status: string;
    failureLocation?: string;
    failureCategory?: SafeFailureCategory;
    overflowPixels?: number;
    overflowSources?: string[];
  }>;
}

function reportPath(): string {
  const runtime = process.env.CG_E2E_RUNTIME_DIR;
  const configured = process.env.CG_E2E_REPORT_FILE;
  if (
    !runtime ||
    !configured ||
    !isAbsolute(runtime) ||
    !isAbsolute(configured)
  ) {
    throw new Error(
      "The integration harness did not provide a private report location.",
    );
  }
  const runtimeRoot = resolve(runtime);
  const target = resolve(configured);
  const fromRoot = relative(runtimeRoot, target);
  if (
    fromRoot === "" ||
    fromRoot === ".." ||
    fromRoot.startsWith(`..${sep}`) ||
    isAbsolute(fromRoot) ||
    dirname(target) !== runtimeRoot
  ) {
    throw new Error(
      "The integration report path is outside its owned runtime directory.",
    );
  }
  return target;
}

export default class SafeReporter implements Reporter {
  private expected = 0;
  private readonly report: SafeReport = {
    expected: 0,
    counts: { passed: 0, failed: 0, skipped: 0, other: 0 },
    titles: [],
  };

  onBegin(_config: FullConfig, suite: Suite): void {
    this.expected = suite.allTests().length;
    this.report.expected = this.expected;
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    const status = result.status;
    if (status === "passed") this.report.counts.passed += 1;
    else if (status === "failed" || status === "timedOut") {
      this.report.counts.failed += 1;
    } else if (status === "skipped") this.report.counts.skipped += 1;
    else this.report.counts.other += 1;

    // Test titles are static; credentials and API responses are never emitted.
    const failure = result.errors.find((error) => {
      const file = error.location?.file;
      return (
        file &&
        [
          "auth.integration.spec.ts",
          "expiry.integration.spec.ts",
          "fixtures.ts",
        ].includes(basename(file)) &&
        Number.isSafeInteger(error.location?.line)
      );
    });
    const failureFile = failure?.location?.file;
    const failureLine = failure?.location?.line;
    const errorText = result.errors.map((error) => error.message).join("\n");
    const responsiveMatch =
      /responsive-overflow:(width-390|width-320-menu|width-320):(\d+):([a-z0-9-]*(?:\+[a-z0-9-]+)*)/.exec(
        errorText,
      );
    const responsiveCheck = responsiveMatch?.[1];
    const overflowPixels = responsiveMatch ? Number(responsiveMatch[2]) : null;
    const overflowSources = responsiveMatch?.[3]
      ? responsiveMatch[3]
          .split("+")
          .filter((source) => responsiveSourceSet.has(source))
      : [];
    const failureCategory: SafeFailureCategory =
      responsiveCheck === "width-390"
        ? "responsive-overflow-390"
        : responsiveCheck === "width-320"
          ? "responsive-overflow-320"
          : responsiveCheck === "width-320-menu"
            ? "responsive-overflow-320-menu"
            : /strict mode violation/i.test(errorText)
              ? "strict-locator"
              : /timeout|timed out/i.test(errorText)
                ? "timeout"
                : /expected:|received:|to be visible|toHave|toContain/i.test(
                      errorText,
                    )
                  ? "assertion"
                  : "other";
    this.report.titles.push({
      title: test.title,
      status,
      ...(status !== "passed" ? { failureCategory } : {}),
      ...(overflowPixels !== null ? { overflowPixels } : {}),
      ...(overflowSources.length > 0 ? { overflowSources } : {}),
      ...(failureFile && failureLine
        ? { failureLocation: `${basename(failureFile)}:${failureLine}` }
        : {}),
    });
  }

  async onEnd(): Promise<void> {
    this.report.expected = this.expected;
    const reportFile = reportPath();
    await writeFile(reportFile, JSON.stringify(this.report), {
      encoding: "utf8",
      flag: "wx",
      mode: 0o600,
    });
    const executed =
      this.report.counts.passed +
      this.report.counts.failed +
      this.report.counts.skipped +
      this.report.counts.other;
    if (
      this.expected === 0 ||
      executed !== this.expected ||
      this.report.counts.failed > 0 ||
      this.report.counts.skipped > 0 ||
      this.report.counts.other > 0
    ) {
      process.exitCode = 1;
    }
  }
}
