import type { ApiError } from "../../shared/api/errors";
import type { ContextChangeBlockReason } from "./context-change-guard";

export type BranchContextTransitionStatus =
  "idle" | "submitting" | "reconciling" | "uncertain" | "blocked" | "failed";

export type BranchContextTransitionNotice =
  | "context-confirmed"
  | "context-unchanged"
  | "effective-context-differs"
  | "context-reconciled";

export type BranchContextTransitionSnapshot =
  | {
      status: "idle";
      notice: BranchContextTransitionNotice | null;
      error: null;
    }
  | { status: "submitting" | "reconciling"; notice: null; error: null }
  | {
      status: "uncertain";
      notice: null;
      error: ApiError | null;
    }
  | {
      status: "blocked";
      notice: null;
      error: null;
      reason: ContextChangeBlockReason;
    }
  | { status: "failed"; notice: null; error: ApiError };

export interface BranchContextTransitionTicket {
  readonly id: number;
  readonly actorId: string;
  readonly contextEpoch: number;
  readonly previousBranchId: string | null;
  readonly requestedBranchId: string | null;
  readonly operation: "select" | "clear";
}

export interface BranchContextTransitionCoordinator {
  getSnapshot(): BranchContextTransitionSnapshot;
  getActiveTicket(): BranchContextTransitionTicket | null;
  begin(
    input: Omit<BranchContextTransitionTicket, "id">,
  ): BranchContextTransitionTicket | null;
  setStatus(
    ticket: BranchContextTransitionTicket,
    status: "submitting" | "reconciling",
  ): boolean;
  markUncertain(
    ticket: BranchContextTransitionTicket,
    error: ApiError | null,
  ): boolean;
  block(reason: ContextChangeBlockReason): void;
  setIdleNotice(notice: BranchContextTransitionNotice): void;
  fail(ticket: BranchContextTransitionTicket, error: ApiError): boolean;
  complete(
    ticket: BranchContextTransitionTicket,
    notice: BranchContextTransitionNotice,
  ): boolean;
  cancel(ticket: BranchContextTransitionTicket): boolean;
  clear(): void;
  isCurrent(ticket: BranchContextTransitionTicket): boolean;
}

const initialSnapshot: BranchContextTransitionSnapshot = Object.freeze({
  status: "idle",
  notice: null,
  error: null,
});

export function createBranchContextTransitionCoordinator(): BranchContextTransitionCoordinator {
  let nextId = 0;
  let activeTicket: BranchContextTransitionTicket | null = null;
  let snapshot = initialSnapshot;

  function isCurrent(ticket: BranchContextTransitionTicket): boolean {
    return activeTicket?.id === ticket.id;
  }

  function update(
    ticket: BranchContextTransitionTicket,
    next: BranchContextTransitionSnapshot,
  ): boolean {
    if (!isCurrent(ticket)) return false;
    snapshot = Object.freeze(next);
    return true;
  }

  return {
    getSnapshot: () => snapshot,
    getActiveTicket: () => activeTicket,
    begin(input) {
      if (activeTicket) return null;
      const ticket = Object.freeze({ ...input, id: ++nextId });
      activeTicket = ticket;
      snapshot = Object.freeze({
        status: "submitting",
        notice: null,
        error: null,
      });
      return ticket;
    },
    setStatus(ticket, status) {
      return update(ticket, { status, notice: null, error: null });
    },
    markUncertain(ticket, error) {
      return update(ticket, { status: "uncertain", notice: null, error });
    },
    block(reason) {
      if (activeTicket) return;
      snapshot = Object.freeze({
        status: "blocked",
        notice: null,
        error: null,
        reason,
      });
    },
    setIdleNotice(notice) {
      if (activeTicket) return;
      snapshot = Object.freeze({ status: "idle", notice, error: null });
    },
    fail(ticket, error) {
      if (!update(ticket, { status: "failed", notice: null, error })) {
        return false;
      }
      activeTicket = null;
      return true;
    },
    complete(ticket, notice) {
      if (!update(ticket, { status: "idle", notice, error: null })) {
        return false;
      }
      activeTicket = null;
      return true;
    },
    cancel(ticket) {
      if (!isCurrent(ticket)) return false;
      activeTicket = null;
      snapshot = initialSnapshot;
      return true;
    },
    clear() {
      activeTicket = null;
      snapshot = initialSnapshot;
    },
    isCurrent,
  };
}
