export type ContextChangeBlockReason =
  "unsaved-changes" | "write-in-progress" | "context-change-in-progress";

export interface ContextChangeLease {
  release(): void;
}

export type ContextChangeAdmission =
  | { acquired: true; lease: ContextChangeLease }
  | { acquired: false; reason: ContextChangeBlockReason };

export interface ContextChangeGuard {
  registerBlocker(blocker: () => boolean): () => void;
  getBlockReason(): ContextChangeBlockReason | null;
  tryAcquireContextChange(): ContextChangeAdmission;
  tryAcquireWrite(): ContextChangeLease | null;
  isContextChangeActive(): boolean;
}

function lease(releaseAction: () => void): ContextChangeLease {
  let released = false;
  return {
    release() {
      if (released) return;
      released = true;
      releaseAction();
    },
  };
}

/**
 * Coordinates local writes and context changes without retaining form values.
 * The session remains authoritative across tabs; these leases close only local
 * check-then-act races.
 */
export function createContextChangeGuard(): ContextChangeGuard {
  const blockers = new Set<() => boolean>();
  let contextChangeActive = false;
  let activeWrites = 0;

  function getBlockReason(): ContextChangeBlockReason | null {
    if (contextChangeActive) return "context-change-in-progress";
    if (activeWrites > 0) return "write-in-progress";
    for (const blocker of blockers) {
      try {
        if (blocker()) return "unsaved-changes";
      } catch {
        // A broken blocker fails closed; it must never allow a context switch.
        return "unsaved-changes";
      }
    }
    return null;
  }

  return {
    registerBlocker(blocker) {
      blockers.add(blocker);
      return () => blockers.delete(blocker);
    },
    getBlockReason,
    tryAcquireContextChange() {
      const reason = getBlockReason();
      if (reason) return { acquired: false, reason };

      contextChangeActive = true;
      return {
        acquired: true,
        lease: lease(() => {
          contextChangeActive = false;
        }),
      };
    },
    tryAcquireWrite() {
      if (contextChangeActive) return null;
      activeWrites++;
      return lease(() => {
        activeWrites = Math.max(0, activeWrites - 1);
      });
    },
    isContextChangeActive: () => contextChangeActive,
  };
}
