import { describe, expect, it, vi } from "vitest";
import { capturePublicIdentityRuntime } from "./public-identity-runtime";

const token = "A".repeat(43);

function capture(pathname: string, search = "", hash = "") {
  const replaceState = vi.fn();
  const runtime = capturePublicIdentityRuntime(
    { pathname, search, hash },
    { state: { retained: true }, replaceState },
  );
  return { replaceState, runtime };
}

describe("public identity token bootstrap", () => {
  it("captures a valid recovery token in memory and strips its fragment", () => {
    const { replaceState, runtime } = capture(
      "/recover-password",
      "?campaign=staff",
      `#token=${token}`,
    );

    expect(runtime.getSnapshot()).toEqual({
      kind: "available",
      purpose: "password-recovery",
      token,
    });
    expect(replaceState).toHaveBeenCalledWith(
      { retained: true },
      "",
      "/recover-password?campaign=staff",
    );
    expect(JSON.stringify(runtime.getSnapshot())).toContain(token);
  });

  it.each([
    `#token=${token}&token=${token}`,
    `#token=${token}%3D`,
    "#token=short",
  ])("rejects malformed or duplicate fragment credentials", (hash) => {
    const { replaceState, runtime } = capture("/recover-password", "", hash);
    expect(runtime.getSnapshot()).toEqual({
      kind: "invalid",
      purpose: "password-recovery",
      reason: "malformed",
    });
    expect(replaceState).toHaveBeenCalledWith(
      { retained: true },
      "",
      "/recover-password",
    );
  });

  it("rejects and removes query-string credentials even with a valid fragment", () => {
    const { replaceState, runtime } = capture(
      "/recover-password",
      `?token=${token}&Token=duplicate&campaign=staff`,
      `#token=${token}`,
    );
    expect(runtime.getSnapshot()).toEqual({
      kind: "invalid",
      purpose: "password-recovery",
      reason: "query-token",
    });
    expect(replaceState).toHaveBeenCalledWith(
      { retained: true },
      "",
      "/recover-password?campaign=staff",
    );
  });

  it("keeps invitation-purpose tokens isolated from recovery routes", () => {
    const { runtime } = capture("/accept-invitation", "", `#token=${token}`);
    expect(runtime.getSnapshot()).toEqual({
      kind: "available",
      purpose: "invitation",
      token,
    });

    const refreshed = capture("/recover-password").runtime;
    expect(refreshed.getSnapshot()).toEqual({ kind: "empty" });
  });

  it("removes fragments outside allowed routes without retaining their token", () => {
    const { replaceState, runtime } = capture("/login", "", `#token=${token}`);
    expect(replaceState).toHaveBeenCalledWith({ retained: true }, "", "/login");
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
  });
});
