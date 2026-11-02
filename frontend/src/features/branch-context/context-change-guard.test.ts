import { describe, expect, it } from "vitest";
import { createContextChangeGuard } from "./context-change-guard";

describe("context change guard", () => {
  it("blocks a context change while a registered draft is dirty", () => {
    const guard = createContextChangeGuard();
    let dirty = true;
    const unregister = guard.registerBlocker(() => dirty);

    expect(guard.tryAcquireContextChange()).toEqual({
      acquired: false,
      reason: "unsaved-changes",
    });

    dirty = false;
    const admission = guard.tryAcquireContextChange();
    expect(admission.acquired).toBe(true);
    if (admission.acquired) admission.lease.release();
    unregister();
    expect(guard.getBlockReason()).toBeNull();
  });

  it("makes writes and context changes mutually exclusive with idempotent leases", () => {
    const guard = createContextChangeGuard();
    const writeLease = guard.tryAcquireWrite();
    expect(writeLease).not.toBeNull();
    expect(guard.tryAcquireContextChange()).toEqual({
      acquired: false,
      reason: "write-in-progress",
    });

    writeLease?.release();
    writeLease?.release();
    const contextChange = guard.tryAcquireContextChange();
    expect(contextChange.acquired).toBe(true);
    expect(guard.tryAcquireWrite()).toBeNull();
    expect(guard.getBlockReason()).toBe("context-change-in-progress");

    if (contextChange.acquired) {
      contextChange.lease.release();
      contextChange.lease.release();
    }
    expect(guard.tryAcquireWrite()).not.toBeNull();
  });

  it("fails closed when a registered blocker cannot be evaluated", () => {
    const guard = createContextChangeGuard();
    guard.registerBlocker(() => {
      throw new Error("synthetic failure");
    });
    expect(guard.tryAcquireContextChange()).toEqual({
      acquired: false,
      reason: "unsaved-changes",
    });
  });
});
