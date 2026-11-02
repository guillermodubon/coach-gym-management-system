import { describe, expect, it } from "vitest";
import { ApiError } from "../../shared/api/errors";
import { createBranchContextTransitionCoordinator } from "./branch-context-transition";

describe("branch context transition coordinator", () => {
  it("tracks one actor-bound transition and confirms the server outcome", () => {
    const coordinator = createBranchContextTransitionCoordinator();
    const ticket = coordinator.begin({
      actorId: "synthetic-user",
      contextEpoch: 4,
      previousBranchId: "synthetic-branch-a",
      requestedBranchId: "synthetic-branch-b",
      operation: "select",
    });

    expect(ticket).toMatchObject({
      actorId: "synthetic-user",
      contextEpoch: 4,
      operation: "select",
    });
    expect(
      coordinator.begin({
        actorId: "other-user",
        contextEpoch: 0,
        previousBranchId: null,
        requestedBranchId: null,
        operation: "clear",
      }),
    ).toBeNull();
    expect(coordinator.setStatus(ticket!, "reconciling")).toBe(true);
    expect(coordinator.complete(ticket!, "context-confirmed")).toBe(true);
    expect(coordinator.getSnapshot()).toEqual({
      status: "idle",
      notice: "context-confirmed",
      error: null,
    });
    expect(coordinator.getActiveTicket()).toBeNull();
  });

  it("keeps an uncertain ticket available for explicit reconciliation", () => {
    const coordinator = createBranchContextTransitionCoordinator();
    const ticket = coordinator.begin({
      actorId: "synthetic-user",
      contextEpoch: 0,
      previousBranchId: null,
      requestedBranchId: "synthetic-branch-a",
      operation: "select",
    });
    const error = new ApiError("timeout");

    expect(coordinator.markUncertain(ticket!, error)).toBe(true);
    expect(coordinator.getSnapshot()).toMatchObject({
      status: "uncertain",
      error,
    });
    expect(coordinator.isCurrent(ticket!)).toBe(true);
    expect(coordinator.setStatus(ticket!, "reconciling")).toBe(true);
    expect(coordinator.complete(ticket!, "context-reconciled")).toBe(true);
  });

  it("ignores late results from a cancelled actor transition", () => {
    const coordinator = createBranchContextTransitionCoordinator();
    const ticket = coordinator.begin({
      actorId: "synthetic-user",
      contextEpoch: 0,
      previousBranchId: null,
      requestedBranchId: "synthetic-branch-a",
      operation: "select",
    });

    expect(coordinator.cancel(ticket!)).toBe(true);
    expect(coordinator.complete(ticket!, "context-confirmed")).toBe(false);
    expect(coordinator.getSnapshot()).toEqual({
      status: "idle",
      notice: null,
      error: null,
    });
  });
});
