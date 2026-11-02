import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { PropsWithChildren } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "../../shared/api/errors";
import { ApiError as SafeApiError } from "../../shared/api/errors";
import type {
  BranchRecord,
  BranchStatus,
} from "../../shared/api/organization-contracts";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import { useAuth } from "../auth/useAuth";
import { useContextChangeGuard } from "../branch-context/ContextChangeGuardProvider";
import { getBranchManagementUser } from "./branch-editor-policy";
import { branchDetailQueryKey } from "./organization-query";

type Operation = {
  actorId: string;
  branchId: string;
  targetStatus: BranchStatus;
};

export type BranchStatusPhase =
  | "idle"
  | "submitting"
  | "reconciling"
  | "uncertain"
  | "conflict"
  | "error"
  | "blocked"
  | "success";

export interface BranchStatusFeedback {
  phase: BranchStatusPhase;
  operation: Operation | null;
  error: ApiError | null;
  message: string | null;
}

interface BranchStatusContextValue {
  feedbackFor(branchId: string): BranchStatusFeedback | null;
  changeStatus(
    branch: BranchRecord,
    targetStatus: BranchStatus,
    reason: string,
  ): Promise<boolean>;
  inspectUncertain(branchId: string): Promise<boolean>;
  clearFeedback(branchId: string): void;
}

const idleFeedback: BranchStatusFeedback = {
  phase: "idle",
  operation: null,
  error: null,
  message: null,
};

const BranchStatusContext = createContext<BranchStatusContextValue | null>(
  null,
);

function safeApiError(error: unknown): ApiError {
  return error instanceof SafeApiError
    ? error
    : new SafeApiError("invalid-response");
}

function isUncertain(error: ApiError): boolean {
  return (
    error.kind === "network" ||
    error.kind === "timeout" ||
    error.kind === "invalid-response" ||
    (error.kind === "http" && (error.status ?? 0) >= 500)
  );
}

function isAuthFailure(error: ApiError): boolean {
  return (
    error.status === 401 ||
    (error.status === 403 && error.code !== "CSRF_TOKEN_INVALID")
  );
}

function isInFlight(phase: BranchStatusPhase): boolean {
  return phase === "submitting" || phase === "reconciling";
}

function isContextBlocking(phase: BranchStatusPhase): boolean {
  return isInFlight(phase) || phase === "uncertain";
}

function actorIdFromKnownAuth(auth: ReturnType<typeof useAuth>): string | null {
  return auth.state.kind === "authenticated" ||
    auth.state.kind === "password-required"
    ? auth.state.user.id
    : null;
}

export function BranchStatusProvider({ children }: PropsWithChildren) {
  const auth = useAuth();
  const authRef = useRef(auth);
  authRef.current = auth;
  const contextChangeGuard = useContextChangeGuard();
  const queryClient = useQueryClient();
  const { organizationApi } = useApiServices();
  const [feedback, setFeedback] = useState(idleFeedback);
  const feedbackRef = useRef(feedback);
  feedbackRef.current = feedback;
  const operationSequence = useRef(0);
  const writeLock = useRef(false);

  const commit = useCallback((next: BranchStatusFeedback) => {
    feedbackRef.current = next;
    setFeedback(next);
  }, []);

  useEffect(
    () =>
      contextChangeGuard.registerBlocker(() =>
        isContextBlocking(feedbackRef.current.phase),
      ),
    [contextChangeGuard],
  );

  useEffect(() => {
    const actorId = actorIdFromKnownAuth(auth);
    const current = feedbackRef.current;
    if (
      current.operation &&
      actorId !== null &&
      current.operation.actorId !== actorId
    ) {
      operationSequence.current += 1;
      writeLock.current = false;
      commit(idleFeedback);
    } else if (auth.state.kind === "anonymous" && current.operation) {
      operationSequence.current += 1;
      writeLock.current = false;
      commit(idleFeedback);
    }
  }, [auth.state, commit]);

  const feedbackFor = useCallback(
    (branchId: string): BranchStatusFeedback | null => {
      const current = feedbackRef.current;
      const actor = getBranchManagementUser(authRef.current);
      return current.operation?.branchId === branchId &&
        current.operation.actorId === actor?.id
        ? current
        : null;
    },
    [],
  );

  const invalidateBranchReads = useCallback(
    async (actorId: string) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["private", "branch-catalog", actorId],
        }),
        queryClient.invalidateQueries({
          queryKey: ["private", "branch-detail", actorId],
        }),
      ]);
    },
    [queryClient],
  );

  const changeStatus = useCallback(
    async (
      branch: BranchRecord,
      targetStatus: BranchStatus,
      reason: string,
    ): Promise<boolean> => {
      const current = feedbackRef.current;
      const user = getBranchManagementUser(authRef.current);
      if (
        !user ||
        writeLock.current ||
        isInFlight(current.phase) ||
        current.phase === "uncertain" ||
        current.phase === "reconciling" ||
        (targetStatus === "ACTIVE" && branch.status !== "INACTIVE") ||
        (targetStatus === "INACTIVE" && branch.status !== "ACTIVE") ||
        reason.trim().length === 0 ||
        reason.trim().length > 1000
      ) {
        return false;
      }

      const lease = contextChangeGuard.tryAcquireWrite();
      if (!lease) {
        const blockReason = contextChangeGuard.getBlockReason();
        commit({
          phase: "blocked",
          operation: {
            actorId: user.id,
            branchId: branch.id,
            targetStatus,
          },
          error: null,
          message:
            blockReason === "unsaved-changes"
              ? "Guarda o descarta los cambios abiertos antes de cambiar el estado de una sucursal."
              : "Espera a que termine la operación actual antes de cambiar el estado.",
        });
        return false;
      }

      const operation: Operation = {
        actorId: user.id,
        branchId: branch.id,
        targetStatus,
      };
      const sequence = ++operationSequence.current;
      writeLock.current = true;
      commit({ phase: "submitting", operation, error: null, message: null });

      try {
        await Promise.all([
          queryClient.cancelQueries({
            queryKey: ["private", "branch-catalog", user.id],
          }),
          queryClient.cancelQueries({
            queryKey: ["private", "branch-detail", user.id],
          }),
        ]);
        const updated =
          targetStatus === "ACTIVE"
            ? await organizationApi.activateBranch(branch.id, {
                reason: reason.trim(),
                version: branch.version,
              })
            : await organizationApi.deactivateBranch(branch.id, {
                reason: reason.trim(),
                version: branch.version,
              });

        // The canonical response confirms this write. Never apply it if the
        // acting account changed while the request was in flight.
        authRef.current.notifySessionChange();
        if (
          getBranchManagementUser(authRef.current)?.id !== operation.actorId ||
          operationSequence.current !== sequence ||
          updated.id !== operation.branchId ||
          updated.status !== operation.targetStatus
        ) {
          if (operationSequence.current === sequence) {
            commit({
              phase: "uncertain",
              operation,
              error: null,
              message:
                "No se pudo asociar la respuesta a la sesión actual. Comprueba el estado antes de continuar.",
            });
          }
          return true;
        }

        commit({
          phase: "reconciling",
          operation,
          error: null,
          message: null,
        });
        await authRef.current.revalidate();

        if (operationSequence.current !== sequence) {
          return true;
        }
        if (
          getBranchManagementUser(authRef.current)?.id !== operation.actorId
        ) {
          commit({
            phase: "uncertain",
            operation,
            error: null,
            message:
              "El cambio de estado respondió correctamente, pero la sesión no quedó confirmada. Comprueba sucursal y contexto antes de continuar.",
          });
          return true;
        }

        const latestAuth = authRef.current;
        const latestUser = getBranchManagementUser(latestAuth);
        if (latestUser) {
          queryClient.setQueryData(
            branchDetailQueryKey(
              {
                actorId: latestUser.id,
                authority: `${latestUser.roles[0]}:${latestUser.organizationScope}`,
                contextEpoch: latestAuth.contextEpoch,
              },
              updated.id,
            ),
            updated,
          );
        }
        await invalidateBranchReads(operation.actorId);
        if (operationSequence.current !== sequence) return true;
        commit({
          phase: "success",
          operation,
          error: null,
          message:
            targetStatus === "ACTIVE"
              ? "La sucursal quedó activa y el contexto se verificó con el servidor."
              : "La sucursal quedó inactiva y el contexto se verificó con el servidor.",
        });
        return true;
      } catch (caught) {
        const error = safeApiError(caught);
        if (isUncertain(error)) {
          authRef.current.notifySessionChange();
          if (operationSequence.current === sequence) {
            commit({
              phase: "uncertain",
              operation,
              error,
              message:
                "El resultado no está confirmado. Comprueba el estado con una lectura antes de iniciar otra acción.",
            });
          }
          return true;
        }

        if (operationSequence.current !== sequence) return true;
        const phase = error.status === 409 ? "reconciling" : "error";
        commit({
          phase,
          operation,
          error,
          message:
            error.status === 409
              ? "La sucursal pudo cambiar mientras confirmabas. Actualiza su ficha antes de decidir otra acción."
              : null,
        });
        if (error.status === 409) {
          authRef.current.notifySessionChange();
          await authRef.current.revalidate();
          await invalidateBranchReads(operation.actorId);
          if (operationSequence.current === sequence) {
            commit({
              phase: "conflict",
              operation,
              error,
              message:
                "La sucursal pudo cambiar mientras confirmabas. Actualiza su ficha antes de decidir otra acción.",
            });
          }
        } else if (isAuthFailure(error)) {
          await authRef.current.revalidate();
        }
        return true;
      } finally {
        writeLock.current = false;
        lease.release();
        const currentState = feedbackRef.current;
        if (
          currentState.operation?.actorId === operation.actorId &&
          currentState.operation.branchId === operation.branchId &&
          isInFlight(currentState.phase)
        ) {
          commit({
            phase: "error",
            operation,
            error: new SafeApiError("invalid-response"),
            message:
              "No se pudo comprobar el resultado. Actualiza la ficha antes de volver a intentar.",
          });
        }
      }
    },
    [
      commit,
      contextChangeGuard,
      invalidateBranchReads,
      organizationApi,
      queryClient,
    ],
  );

  const inspectUncertain = useCallback(
    async (branchId: string): Promise<boolean> => {
      const current = feedbackRef.current;
      const operation = current.operation;
      const user = getBranchManagementUser(authRef.current);
      if (
        current.phase !== "uncertain" ||
        !operation ||
        operation.branchId !== branchId ||
        operation.actorId !== user?.id ||
        writeLock.current
      ) {
        return false;
      }

      const sequence = ++operationSequence.current;
      writeLock.current = true;
      commit({ phase: "reconciling", operation, error: null, message: null });
      try {
        const branch = await organizationApi.branch(branchId);
        await authRef.current.revalidate();
        if (
          operationSequence.current !== sequence ||
          actorIdFromKnownAuth(authRef.current) !== operation.actorId
        ) {
          if (operationSequence.current === sequence) {
            commit({
              phase: "uncertain",
              operation,
              error: null,
              message:
                "La lectura de sucursal terminó, pero no se pudo confirmar la sesión. Comprueba el acceso antes de continuar.",
            });
          }
          return false;
        }

        const latestAuth = authRef.current;
        queryClient.setQueryData(
          branchDetailQueryKey(
            {
              actorId: operation.actorId,
              authority: `${user.roles[0]}:${user.organizationScope}`,
              contextEpoch: latestAuth.contextEpoch,
            },
            branch.id,
          ),
          branch,
        );
        await invalidateBranchReads(operation.actorId);
        if (operationSequence.current !== sequence) return false;
        commit({
          phase: "success",
          operation,
          error: null,
          message: `Lectura confirmada: la sucursal aparece ${branch.status === "ACTIVE" ? "activa" : "inactiva"} en el servidor. Revisa ese estado antes de decidir una nueva acción.`,
        });
        return true;
      } catch (caught) {
        if (operationSequence.current === sequence) {
          commit({
            phase: "uncertain",
            operation,
            error: safeApiError(caught),
            message:
              "No se pudo comprobar la sucursal. El estado sigue bloqueado hasta completar una lectura correcta.",
          });
        }
        return false;
      } finally {
        writeLock.current = false;
      }
    },
    [commit, invalidateBranchReads, organizationApi, queryClient],
  );

  const clearFeedback = useCallback(
    (branchId: string) => {
      const current = feedbackRef.current;
      if (
        current.operation?.branchId === branchId &&
        !isContextBlocking(current.phase)
      ) {
        operationSequence.current += 1;
        commit(idleFeedback);
      }
    },
    [commit],
  );

  return (
    <BranchStatusContext.Provider
      value={{ feedbackFor, changeStatus, inspectUncertain, clearFeedback }}
    >
      {children}
    </BranchStatusContext.Provider>
  );
}

export function useBranchStatus(): BranchStatusContextValue {
  const context = useContext(BranchStatusContext);
  if (!context) {
    throw new Error("Branch status actions require BranchStatusProvider.");
  }
  return context;
}
