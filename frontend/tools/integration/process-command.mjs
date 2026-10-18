const GRADLE_TASKS = [
  "compileJava",
  "compileTestJava",
  "bootJar",
  "--console=plain",
];

/** Invoke script interpreters explicitly; child_process still uses shell: false. */
export function gradleBuildInvocation(platform = process.platform) {
  if (platform === "win32") {
    return {
      command: "cmd.exe",
      args: ["/d", "/s", "/c", `.\\gradlew.bat ${GRADLE_TASKS.join(" ")}`],
    };
  }

  // Git stores this repository's gradlew as 100644. A clean POSIX checkout
  // cannot execute it directly; sh needs read permission, not an executable bit.
  return { command: "sh", args: ["./gradlew", ...GRADLE_TASKS] };
}

const SAFE_START_CODES = new Set(["EACCES", "ENOENT", "ENOEXEC", "EPERM"]);

/** Labels are fixed harness phases; never include command arguments or raw errors. */
export function processStartFailureMessage(label, error) {
  const code = SAFE_START_CODES.has(error?.code) ? error.code : "OS_ERROR";
  return `${label} could not be started [${code}].`;
}
